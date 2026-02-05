package io.endee.client;

import io.endee.client.exception.EndeeApiException;
import io.endee.client.exception.EndeeException;
import io.endee.client.types.*;
import io.endee.client.util.CryptoUtils;
import io.endee.client.util.JsonUtils;
import io.endee.client.util.MessagePackUtils;
import io.endee.client.util.ValidationUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Index client for Endee-DB vector operations.
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>{@code
 * Index index = client.getIndex("my_index");
 *
 * // Upsert vectors
 * List<VectorItem> vectors = List.of(
 *         VectorItem.builder("vec1", new double[] { 0.1, 0.2, 0.3 })
 *                 .meta(Map.of("label", "example"))
 *                 .build());
 * index.upsert(vectors);
 *
 * // Query
 * List<QueryResult> results = index.query(
 *         QueryOptions.builder()
 *                 .vector(new double[] { 0.1, 0.2, 0.3 })
 *                 .topK(10)
 *                 .build());
 * }</pre>
 */
public class Index {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_BATCH_SIZE = 1000;
    private static final int MAX_TOP_K = 512;
    private static final int MAX_EF = 1024;

    private final String name;
    private final String token;
    private final String url;
    private final HttpClient httpClient;

    private long count;
    private SpaceType spaceType;
    private int dimension;
    private Precision precision;
    private int m;
    private int sparseDimension;

    /**
     * Creates a new Index instance.
     */
    public Index(String name, String token, String url, int version, IndexInfo params) {
        this.name = name;
        this.token = token;
        this.url = url;

        this.count = params != null ? params.getTotalElements() : 0;
        this.spaceType = params != null && params.getSpaceType() != null ? params.getSpaceType() : SpaceType.COSINE;
        this.dimension = params != null ? params.getDimension() : 0;
        this.precision = params != null && params.getPrecision() != null ? params.getPrecision() : Precision.INT8D;
        this.m = params != null ? params.getM() : 16;
        this.sparseDimension = params != null && params.getSparseDimension() != null ? params.getSparseDimension() : 0;

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(DEFAULT_TIMEOUT)
                .build();
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * Checks if this index supports hybrid (sparse + dense) vectors.
     */
    public boolean isHybrid() {
        return sparseDimension > 0;
    }

    /**
     * Normalizes a vector for cosine similarity.
     * Returns [normalizedVector, norm].
     */
    private double[][] normalizeVector(double[] vector) {
        if (vector.length != dimension) {
            throw new IllegalArgumentException(
                    "Vector dimension mismatch: expected " + dimension + ", got " + vector.length);
        }

        if (spaceType != SpaceType.COSINE) {
            return new double[][] { vector, { 1.0 } };
        }

        double sumSquares = 0;
        for (double v : vector) {
            sumSquares += v * v;
        }
        double norm = Math.sqrt(sumSquares);

        if (norm == 0) {
            return new double[][] { vector, { 1.0 } };
        }

        double[] normalized = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = vector[i] / norm;
        }

        return new double[][] { normalized, { norm } };
    }

    /**
     * Upserts vectors into the index.
     *
     * @param inputArray list of vector items to upsert
     * @return success message
     */
    public String upsert(List<VectorItem> inputArray) {
        if (inputArray.size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Cannot insert more than " + MAX_BATCH_SIZE + " vectors at a time");
        }

        List<String> ids = inputArray.stream()
                .map(item -> item.getId() != null ? item.getId() : "")
                .collect(Collectors.toList());
        ValidationUtils.validateVectorIds(ids);

        List<Object[]> vectorBatch = new ArrayList<>();

        for (VectorItem item : inputArray) {
            double[][] result = normalizeVector(item.getVector());
            double[] normalizedVector = result[0];
            double norm = result[1][0];

            byte[] metaData = CryptoUtils.jsonZip(item.getMeta() != null ? item.getMeta() : Map.of());

            int[] sparseIndices = item.getSparseIndices() != null ? item.getSparseIndices() : new int[0];
            double[] sparseValues = item.getSparseValues() != null ? item.getSparseValues() : new double[0];

            if (!isHybrid() && (sparseIndices.length > 0 || sparseValues.length > 0)) {
                throw new IllegalArgumentException(
                        "Cannot insert sparse data into a dense-only index. Create index with sparseDimension > 0 for hybrid support.");
            }

            if (isHybrid()) {
                if (sparseIndices.length == 0 || sparseValues.length == 0) {
                    throw new IllegalArgumentException(
                            "Both sparse_indices and sparse_values must be provided for hybrid vectors.");
                }
                if (sparseIndices.length != sparseValues.length) {
                    throw new IllegalArgumentException(
                            "sparseIndices and sparseValues must have the same length. Got " +
                                    sparseIndices.length + " indices and " + sparseValues.length + " values.");
                }
                for (int idx : sparseIndices) {
                    if (idx < 0 || idx >= sparseDimension) {
                        throw new IllegalArgumentException(
                                "Sparse index " + idx + " is out of bounds. Must be in range [0," + sparseDimension
                                        + ").");
                    }
                }
            }

            String filterJson = JsonUtils.toJson(item.getFilter() != null ? item.getFilter() : Map.of());

            if (isHybrid()) {
                vectorBatch.add(new Object[] { item.getId(), metaData, filterJson, norm, normalizedVector,
                        sparseIndices, sparseValues });
            } else {
                vectorBatch.add(new Object[] { item.getId(), metaData, filterJson, norm, normalizedVector });
            }
        }

        byte[] serializedData = MessagePackUtils.packVectors(vectorBatch);

        try {
            HttpRequest request = buildPostMsgpackRequest("/index/" + name + "/vector/insert", serializedData);
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                EndeeApiException.raiseException(response.statusCode(), new String(response.body()));
            }

            return "Vectors inserted successfully";
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new EndeeException("Failed to upsert vectors", e);
        }
    }

    /**
     * Queries the index for similar vectors.
     *
     * @param options the query options
     * @return list of query results
     */
    public List<QueryResult> query(QueryOptions options) {
        if (options.getTopK() > MAX_TOP_K || options.getTopK() < 0) {
            throw new IllegalArgumentException("top_k cannot be greater than " + MAX_TOP_K + " and less than 0");
        }
        if (options.getEf() > MAX_EF) {
            throw new IllegalArgumentException("ef search cannot be greater than " + MAX_EF);
        }

        boolean hasSparse = options.getSparseIndices() != null && options.getSparseIndices().length > 0
                && options.getSparseValues() != null && options.getSparseValues().length > 0;
        boolean hasDense = options.getVector() != null;

        if (!hasDense && !hasSparse) {
            throw new IllegalArgumentException(
                    "At least one of 'vector' or 'sparseIndices'/'sparseValues' must be provided.");
        }

        if (hasSparse && !isHybrid()) {
            throw new IllegalArgumentException(
                    "Cannot perform sparse search on a dense-only index. Create index with sparseDimension > 0 for hybrid support.");
        }

        if (hasSparse && options.getSparseIndices().length != options.getSparseValues().length) {
            throw new IllegalArgumentException(
                    "sparseIndices and sparseValues must have the same length.");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("k", options.getTopK());
        data.put("ef", options.getEf());
        data.put("include_vectors", options.isIncludeVectors());

        if (hasDense) {
            double[][] result = normalizeVector(options.getVector());
            data.put("vector", result[0]);
        }

        if (hasSparse) {
            data.put("sparse_indices", options.getSparseIndices());
            data.put("sparse_values", options.getSparseValues());
        }

        if (options.getFilter() != null) {
            data.put("filter", JsonUtils.toJson(options.getFilter()));
        }

        try {
            String jsonBody = JsonUtils.toJson(data);
            HttpRequest request = buildPostJsonRequest("/index/" + name + "/search", jsonBody);
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                EndeeApiException.raiseException(response.statusCode(), new String(response.body()));
            }

            List<Object[]> decoded = MessagePackUtils.unpackQueryResults(response.body());
            List<QueryResult> results = new ArrayList<>();

            for (Object[] tuple : decoded) {
                double similarity = (Double) tuple[0];
                String vectorId = (String) tuple[1];
                byte[] metaData = (byte[]) tuple[2];
                String filterStr = (String) tuple[3];
                double normValue = (Double) tuple[4];

                Map<String, Object> meta = CryptoUtils.jsonUnzip(metaData);

                QueryResult result = new QueryResult();
                result.setId(vectorId);
                result.setSimilarity(similarity);
                result.setDistance(1 - similarity);
                result.setMeta(meta);
                result.setNorm(normValue);

                if (filterStr != null && !filterStr.isEmpty() && !filterStr.equals("{}")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsedFilter = JsonUtils.fromJson(filterStr, Map.class);
                    result.setFilter(parsedFilter);
                }

                if (options.isIncludeVectors() && tuple.length > 5) {
                    result.setVector((double[]) tuple[5]);
                }

                results.add(result);
            }

            return results;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new EndeeException("Failed to query index", e);
        }
    }

    /**
     * Deletes a vector by ID.
     *
     * @param id the vector ID to delete
     * @return success message
     */
    public String deleteVector(String id) {
        try {
            HttpRequest request = buildDeleteRequest("/index/" + name + "/vector/" + id + "/delete");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                EndeeApiException.raiseException(response.statusCode(), response.body());
            }

            return response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new EndeeException("Failed to delete vector", e);
        }
    }

    /**
     * Deletes vectors matching a filter.
     *
     * @param filter the filter criteria
     * @return the API response
     */
    public String deleteWithFilter(List<Map<String, Object>> filter) {
        try {
            Map<String, Object> data = Map.of("filter", filter);
            String jsonBody = JsonUtils.toJson(data);

            HttpRequest request = buildDeleteJsonRequest("/index/" + name + "/vectors/delete", jsonBody);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                EndeeApiException.raiseException(response.statusCode(), response.body());
            }

            return response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new EndeeException("Failed to delete vectors with filter", e);
        }
    }

    /**
     * Gets a vector by ID.
     *
     * @param id the vector ID
     * @return the vector information
     */
    public VectorInfo getVector(String id) {
        try {
            Map<String, Object> data = Map.of("id", id);
            String jsonBody = JsonUtils.toJson(data);

            HttpRequest request = buildPostJsonRequest("/index/" + name + "/vector/get", jsonBody);
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                EndeeApiException.raiseException(response.statusCode(), new String(response.body()));
            }

            Object[] vectorObj = MessagePackUtils.unpackVector(response.body());

            VectorInfo info = new VectorInfo();
            info.setId((String) vectorObj[0]);
            info.setMeta(CryptoUtils.jsonUnzip((byte[]) vectorObj[1]));

            String filterStr = (String) vectorObj[2];
            if (filterStr != null && !filterStr.isEmpty() && !filterStr.equals("{}")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsedFilter = JsonUtils.fromJson(filterStr, Map.class);
                info.setFilter(parsedFilter);
            }

            info.setNorm((Double) vectorObj[3]);
            info.setVector((double[]) vectorObj[4]);

            return info;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new EndeeException("Failed to get vector", e);
        }
    }

    /**
     * Returns a description of this index.
     *
     * @return the index description
     */
    public IndexDescription describe() {
        return new IndexDescription(
                name,
                spaceType,
                dimension,
                sparseDimension,
                isHybrid(),
                count,
                precision,
                m);
    }

    // ==================== HTTP Request Helper Methods ====================

    /**
     * Builds a POST request with JSON body.
     */
    private HttpRequest buildPostJsonRequest(String path, String jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url + path))
                .header("Content-Type", "application/json")
                .timeout(DEFAULT_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        if (token != null && !token.isBlank()) {
            builder.header("Authorization", token);
        }

        return builder.build();
    }

    /**
     * Builds a POST request with MessagePack body.
     */
    private HttpRequest buildPostMsgpackRequest(String path, byte[] body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url + path))
                .header("Content-Type", "application/msgpack")
                .timeout(DEFAULT_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));

        if (token != null && !token.isBlank()) {
            builder.header("Authorization", token);
        }

        return builder.build();
    }

    /**
     * Builds a DELETE request.
     */
    private HttpRequest buildDeleteRequest(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url + path))
                .timeout(DEFAULT_TIMEOUT)
                .DELETE();

        if (token != null && !token.isBlank()) {
            builder.header("Authorization", token);
        }

        return builder.build();
    }

    /**
     * Builds a DELETE request with JSON body.
     */
    private HttpRequest buildDeleteJsonRequest(String path, String jsonBody) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url + path))
                .header("Content-Type", "application/json")
                .timeout(DEFAULT_TIMEOUT)
                .method("DELETE", HttpRequest.BodyPublishers.ofString(jsonBody));

        if (token != null && !token.isBlank()) {
            builder.header("Authorization", token);
        }

        return builder.build();
    }
}

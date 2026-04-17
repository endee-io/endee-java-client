package io.endee.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * <p>Example usage:
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
  private static final int MAX_TOP_K = 4096;
  private static final int MAX_EF = 1024;
  private static final int MAX_FILTER_BOOST_PERCENTAGE = 400;

  private final String name;
  private final String token;
  private final String url;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  private long count;
  private SpaceType spaceType;
  private int dimension;
  private Precision precision;
  private int m;
  private String sparseModel;
  private String libToken;
  private int efCon;

  /** Creates a new Index instance. */
  public Index(String name, String token, String url, int version, IndexInfo params) {
    this.name = name;
    this.token = token;
    this.url = url;
    this.objectMapper = new ObjectMapper();

    this.count = params != null ? params.getTotalElements() : 0;
    this.spaceType =
        params != null && params.getSpaceType() != null ? params.getSpaceType() : SpaceType.COSINE;
    this.dimension = params != null ? params.getDimension() : 0;
    this.precision =
        params != null && params.getPrecision() != null ? params.getPrecision() : Precision.INT8;
    this.m = params != null ? params.getM() : 16;
    this.sparseModel = params != null ? params.getSparseModel() : "None";
    this.libToken = params != null ? params.getLibToken() : null;
    this.efCon = params != null ? params.getEfCon() : 128;

    this.httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(DEFAULT_TIMEOUT)
            .build();
  }

  @Override
  public String toString() {
    return name;
  }

  public String getLibToken() {
    return libToken;
  }

  /**
   * Returns {@code true} when this index supports hybrid (sparse + dense) vectors. Determined by
   * {@code sparse_model != "None"} from the server response.
   */
  public boolean isHybrid() {
    return sparseModel != null && !"None".equals(sparseModel);
  }

  /** Normalizes a vector for cosine similarity. Returns [normalizedVector, norm]. */
  private double[][] normalizeVector(double[] vector) {
    if (vector.length != dimension) {
      throw new IllegalArgumentException(
          "Vector dimension mismatch: expected " + dimension + ", got " + vector.length);
    }

    if (spaceType != SpaceType.COSINE) {
      return new double[][] {vector, {1.0}};
    }

    double sumSquares = 0;
    for (double v : vector) {
      sumSquares += v * v;
    }
    double norm = Math.sqrt(sumSquares);

    if (norm == 0) {
      return new double[][] {vector, {1.0}};
    }

    double[] normalized = new double[vector.length];
    for (int i = 0; i < vector.length; i++) {
      normalized[i] = vector[i] / norm;
    }

    return new double[][] {normalized, {norm}};
  }

  /** Validates that a vector contains only finite values (no NaN or Inf). */
  private static void validateVectorValues(double[] vector, String vectorId) {
    for (double v : vector) {
      if (Double.isNaN(v) || Double.isInfinite(v)) {
        throw new IllegalArgumentException(
            "Vector '" + vectorId + "' contains non-finite value (NaN or Inf)");
      }
    }
  }

  /**
   * Upserts vectors into the index.
   *
   * @param inputArray list of vector items to upsert (1 – 1,000 items)
   * @return success message
   */
  public String upsert(List<VectorItem> inputArray) {
    if (inputArray.isEmpty()) {
      throw new IllegalArgumentException("Must provide at least one vector to upsert");
    }
    if (inputArray.size() > MAX_BATCH_SIZE) {
      throw new IllegalArgumentException(
          "Cannot insert more than " + MAX_BATCH_SIZE + " vectors at a time");
    }

    List<String> ids =
        inputArray.stream()
            .map(item -> item.getId() != null ? item.getId() : "")
            .collect(Collectors.toList());
    ValidationUtils.validateVectorIds(ids);

    List<Object[]> vectorBatch = new ArrayList<>();

    for (VectorItem item : inputArray) {
      validateVectorValues(item.getVector(), item.getId());
      double[][] result = normalizeVector(item.getVector());
      double[] normalizedVector = result[0];
      double norm = result[1][0];

      byte[] metaData = CryptoUtils.jsonZip(item.getMeta() != null ? item.getMeta() : Map.of());

      int[] sparseIndices = item.getSparseIndices() != null ? item.getSparseIndices() : new int[0];
      double[] sparseValues =
          item.getSparseValues() != null ? item.getSparseValues() : new double[0];

      if (!isHybrid() && (sparseIndices.length > 0 || sparseValues.length > 0)) {
        throw new IllegalArgumentException(
            "Cannot insert sparse data into a dense-only index. Use sparseModel(\"default\") when creating the index.");
      }

      if (isHybrid()) {
        if (sparseIndices.length == 0 || sparseValues.length == 0) {
          throw new IllegalArgumentException(
              "Both sparse_indices and sparse_values must be provided for hybrid vectors.");
        }
        if (sparseIndices.length != sparseValues.length) {
          throw new IllegalArgumentException(
              "sparseIndices and sparseValues must have the same length. Got "
                  + sparseIndices.length
                  + " indices and "
                  + sparseValues.length
                  + " values.");
        }
      }

      String filterJson = JsonUtils.toJson(item.getFilter() != null ? item.getFilter() : Map.of());

      if (isHybrid()) {
        vectorBatch.add(
            new Object[] {
              item.getId(),
              metaData,
              filterJson,
              norm,
              normalizedVector,
              sparseIndices,
              sparseValues
            });
      } else {
        vectorBatch.add(new Object[] {item.getId(), metaData, filterJson, norm, normalizedVector});
      }
    }

    byte[] serializedData = MessagePackUtils.packVectors(vectorBatch);

    try {
      HttpRequest request =
          buildPostMsgpackRequest("/index/" + name + "/vector/insert", serializedData);
      HttpResponse<byte[]> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

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
    if (options.getTopK() < 1 || options.getTopK() > MAX_TOP_K) {
      throw new IllegalArgumentException("top_k must be between 1 and " + MAX_TOP_K);
    }
    if (options.getEf() > MAX_EF) {
      throw new IllegalArgumentException("ef cannot be greater than " + MAX_EF);
    }
    if (options.getPrefilterCardinalityThreshold() < 1_000
        || options.getPrefilterCardinalityThreshold() > 1_000_000) {
      throw new IllegalArgumentException(
          "prefilterCardinalityThreshold must be between 1,000 and 1,000,000");
    }
    if (options.getFilterBoostPercentage() < 0
        || options.getFilterBoostPercentage() > MAX_FILTER_BOOST_PERCENTAGE) {
      throw new IllegalArgumentException(
          "filterBoostPercentage must be between 0 and " + MAX_FILTER_BOOST_PERCENTAGE);
    }
    if (options.getDenseRrfWeight() < 0.0 || options.getDenseRrfWeight() > 1.0) {
      throw new IllegalArgumentException("denseRrfWeight must be between 0.0 and 1.0");
    }
    if (options.getRrfRankConstant() < 1) {
      throw new IllegalArgumentException("rrfRankConstant must be at least 1");
    }

    boolean hasSparse =
        options.getSparseIndices() != null
            && options.getSparseIndices().length > 0
            && options.getSparseValues() != null
            && options.getSparseValues().length > 0;
    boolean hasDense = options.getVector() != null;

    if (!hasDense && !hasSparse) {
      throw new IllegalArgumentException(
          "At least one of 'vector' or 'sparseIndices'/'sparseValues' must be provided.");
    }

    if (hasSparse && !isHybrid()) {
      throw new IllegalArgumentException("Cannot perform sparse search on a dense-only index.");
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

    Map<String, Object> filterParams = new HashMap<>();
    filterParams.put("prefilter_threshold", options.getPrefilterCardinalityThreshold());
    filterParams.put("boost_percentage", options.getFilterBoostPercentage());
    data.put("filter_params", filterParams);

    data.put("dense_rrf_weight", options.getDenseRrfWeight());
    data.put("rrf_rank_constant", options.getRrfRankConstant());

    try {
      String jsonBody = JsonUtils.toJson(data);
      HttpRequest request = buildPostJsonRequest("/index/" + name + "/search", jsonBody);
      HttpResponse<byte[]> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

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
        result.setVector(new double[0]);

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
   * Updates the filter fields of existing vectors without re-upserting them.
   *
   * @param updates list of filter updates, each containing an id and the new filter object
   * @return server response text
   */
  public String updateFilters(List<UpdateFilterParams> updates) {
    List<String> ids = updates.stream().map(UpdateFilterParams::getId).collect(Collectors.toList());
    ValidationUtils.validateVectorIds(ids);

    List<Map<String, Object>> payload = new ArrayList<>();
    for (UpdateFilterParams update : updates) {
      Map<String, Object> entry = new HashMap<>();
      entry.put("id", update.getId());
      entry.put("filter", update.getFilter() != null ? update.getFilter() : Map.of());
      payload.add(entry);
    }

    try {
      String jsonBody = JsonUtils.toJson(Map.of("updates", payload));
      HttpRequest request = buildPostJsonRequest("/index/" + name + "/filters/update", jsonBody);
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        EndeeApiException.raiseException(response.statusCode(), response.body());
      }

      return response.body();
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new EndeeException("Failed to update filters", e);
    }
  }

  /**
   * Deletes a vector by ID.
   *
   * @param id the vector ID to delete
   * @return deletion count message (e.g. {@code "1 rows deleted"})
   */
  public String deleteVector(String id) {
    try {
      HttpRequest request = buildDeleteRequest("/index/" + name + "/vector/" + id + "/delete");
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        EndeeApiException.raiseException(response.statusCode(), response.body());
      }

      return response.body() + " rows deleted";
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
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

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
   * @return the vector information including sparse fields for hybrid indexes
   */
  public VectorInfo getVector(String id) {
    try {
      Map<String, Object> data = Map.of("id", id);
      String jsonBody = JsonUtils.toJson(data);

      HttpRequest request = buildPostJsonRequest("/index/" + name + "/vector/get", jsonBody);
      HttpResponse<byte[]> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

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

      if (vectorObj.length > 5) {
        info.setSparseIndices((int[]) vectorObj[5]);
        info.setSparseValues((double[]) vectorObj[6]);
      }

      return info;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new EndeeException("Failed to get vector", e);
    }
  }

  /**
   * Returns a description of this index without making a network call.
   *
   * @return the index description
   */
  public IndexDescription describe() {
    return new IndexDescription(
        name, spaceType, dimension, sparseModel, isHybrid(), count, precision, m, efCon);
  }

  /**
   * Triggers an index rebuild with new HNSW parameters.
   *
   * @param m HNSW M parameter (bi-directional links per node), must be &gt; 0
   * @param efCon HNSW ef_construction parameter, must be &gt; 0
   * @return rebuild status dict with {@code status}, {@code previous_config}, {@code new_config},
   *     {@code total_vectors}
   */
  public Map<String, Object> rebuild(int m, int efCon) {
    if (m <= 0) {
      throw new IllegalArgumentException("M must be greater than 0");
    }
    if (efCon <= 0) {
      throw new IllegalArgumentException("ef_con must be greater than 0");
    }

    refreshMetadata();
    if (count == 0) {
      throw new IllegalStateException("Cannot rebuild an empty index");
    }

    try {
      String jsonBody = JsonUtils.toJson(Map.of("M", m, "ef_con", efCon));
      HttpRequest request = buildPostJsonRequest("/index/" + name + "/rebuild", jsonBody);
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 202) {
        EndeeApiException.raiseException(response.statusCode(), response.body());
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
      return result;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new EndeeException("Failed to rebuild index", e);
    }
  }

  /**
   * Returns the current rebuild status of this index.
   *
   * @return dict with {@code status}, and optionally {@code vectors_processed}, {@code
   *     total_vectors}, {@code percent_complete}
   */
  public Map<String, Object> rebuildStatus() {
    try {
      HttpRequest request = buildGetRequest("/index/" + name + "/rebuild/status");
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        EndeeApiException.raiseException(response.statusCode(), response.body());
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> result = objectMapper.readValue(response.body(), Map.class);
      return result;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new EndeeException("Failed to get rebuild status", e);
    }
  }

  /**
   * Fetches the latest metadata from the server and updates this Index object's fields.
   *
   * @return dict with current {@code count}, {@code space_type}, {@code dimension}, {@code
   *     precision}, {@code M}, {@code ef_con}, {@code sparse_model}, {@code is_hybrid}
   */
  public Map<String, Object> refreshMetadata() {
    try {
      HttpRequest request = buildGetRequest("/index/" + name + "/info");
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        EndeeApiException.raiseException(response.statusCode(), response.body());
      }

      JsonNode data = objectMapper.readTree(response.body());

      this.count = data.get("total_elements").asLong();
      this.spaceType = SpaceType.fromValue(data.get("space_type").asText());
      this.dimension = data.get("dimension").asInt();
      this.precision = Precision.fromValue(data.get("precision").asText());
      this.m = data.get("M").asInt();
      this.efCon = data.get("ef_con").asInt();

      if (data.has("sparse_model") && !data.get("sparse_model").isNull()) {
        this.sparseModel = data.get("sparse_model").asText();
      }
      if (data.has("lib_token") && !data.get("lib_token").isNull()) {
        this.libToken = data.get("lib_token").asText();
      }

      Map<String, Object> result = new HashMap<>();
      result.put("count", this.count);
      result.put("space_type", this.spaceType.getValue());
      result.put("dimension", this.dimension);
      result.put("precision", this.precision.getValue());
      result.put("M", this.m);
      result.put("ef_con", this.efCon);
      result.put("sparse_model", this.sparseModel);
      result.put("is_hybrid", isHybrid());
      return result;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new EndeeException("Failed to refresh metadata", e);
    }
  }

  // ==================== HTTP Request Helpers ====================

  private HttpRequest buildGetRequest(String path) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(url + path))
            .header("Content-Type", "application/json")
            .timeout(DEFAULT_TIMEOUT)
            .GET();

    if (token != null && !token.isBlank()) {
      builder.header("Authorization", token);
    }

    return builder.build();
  }

  private HttpRequest buildPostJsonRequest(String path, String jsonBody) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(url + path))
            .header("Content-Type", "application/json")
            .timeout(DEFAULT_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

    if (token != null && !token.isBlank()) {
      builder.header("Authorization", token);
    }

    return builder.build();
  }

  private HttpRequest buildPostMsgpackRequest(String path, byte[] body) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
            .uri(URI.create(url + path))
            .header("Content-Type", "application/msgpack")
            .timeout(DEFAULT_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));

    if (token != null && !token.isBlank()) {
      builder.header("Authorization", token);
    }

    return builder.build();
  }

  private HttpRequest buildDeleteRequest(String path) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder().uri(URI.create(url + path)).timeout(DEFAULT_TIMEOUT).DELETE();

    if (token != null && !token.isBlank()) {
      builder.header("Authorization", token);
    }

    return builder.build();
  }

  private HttpRequest buildDeleteJsonRequest(String path, String jsonBody) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder()
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

package io.endee.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.endee.client.exception.EndeeApiException;
import io.endee.client.exception.EndeeException;
import io.endee.client.types.CreateIndexOptions;
import io.endee.client.types.IndexInfo;
import io.endee.client.types.Precision;
import io.endee.client.types.SpaceType;
import io.endee.client.util.JsonUtils;
import io.endee.client.util.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Main Endee client for Endee-DB.
 *
 * <p>
 * Example usage:
 * </p>
 * 
 * <pre>{@code
 * Endee client = new Endee("auth-token");
 *
 * // Create an index
 * CreateIndexOptions options = CreateIndexOptions.builder("my_index", 128)
 *         .spaceType(SpaceType.COSINE)
 *         .precision(Precision.INT8D)
 *         .build();
 * client.createIndex(options);
 *
 * // Get an index and perform operations
 * Index index = client.getIndex("my_index");
 * }</pre>
 */
public class Endee {
    private static final Logger logger = LoggerFactory.getLogger(Endee.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final int MAX_DIMENSION = 10000;

    private String token;
    private String baseUrl;
    private final int version;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new Endee client without authentication.
     * Uses local server at http://127.0.0.1:8080/api/v1
     */
    public Endee() {
        this(null);
        this.baseUrl = "http://127.0.0.1:8080/api/v1";
    }

    /**
     * Creates a new Endee client.
     *
     * @param token the Auth token (optional)
     */
    public Endee(String token) {
        this.token = token;
        this.baseUrl = "http://127.0.0.1:8080/api/v1";
        this.version = 1;
        this.objectMapper = new ObjectMapper();

        if (token != null && !token.isEmpty()) {
            String[] tokenParts = token.split(":");
            if (tokenParts.length > 2) {
                this.baseUrl = "https://" + tokenParts[2] + ".endee.io/api/v1";
                this.token = tokenParts[0] + ":" + tokenParts[1];
            }
        }

        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(DEFAULT_TIMEOUT)
                .build();
    }

    /**
     * Sets a custom base URL for the API.
     *
     * @param url the base URL
     * @return the URL that was set
     */
    public String setBaseUrl(String url) {
        this.baseUrl = url;
        return url;
    }

    /**
     * Creates a new index.
     *
     * @param options the index creation options
     * @return success message
     * @throws EndeeException if the operation fails
     */
    public String createIndex(CreateIndexOptions options) {
        if (!ValidationUtils.isValidIndexName(options.getName())) {
            throw new IllegalArgumentException(
                    "Invalid index name. Index name must be alphanumeric and can contain underscores and less than 48 characters");
        }
        if (options.getDimension() > MAX_DIMENSION) {
            throw new IllegalArgumentException("Dimension cannot be greater than " + MAX_DIMENSION);
        }
        if (options.getSparseDimension() != null && options.getSparseDimension() < 0) {
            throw new IllegalArgumentException("Sparse dimension cannot be less than 0");
        }

        String normalizedSpaceType = options.getSpaceType().getValue().toLowerCase();
        if (!List.of("cosine", "l2", "ip").contains(normalizedSpaceType)) {
            throw new IllegalArgumentException("Invalid space type: " + options.getSpaceType());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("index_name", options.getName());
        data.put("dim", options.getDimension());
        data.put("space_type", normalizedSpaceType);
        data.put("M", options.getM());
        data.put("ef_con", options.getEfCon());
        data.put("checksum", -1);
        data.put("precision", options.getPrecision().getValue());

        if (options.getSparseDimension() != null) {
            data.put("sparse_dim", options.getSparseDimension());
        }
        if (options.getVersion() != null) {
            data.put("version", options.getVersion());
        }

        try {
            HttpRequest request = buildPostRequest("/index/create", data);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Error: {}", response.body());
                EndeeApiException.raiseException(response.statusCode(), response.body());
            }

            return "Index created successfully";
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new EndeeException("Failed to create index", e);
        }
    }

    /**
     * Lists all indexes.
     *
     * @return list of index information
     * @throws EndeeException if the operation fails
     */
    public String listIndexes() {
        try {
            HttpRequest request = buildGetRequest("/index/list");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            return response.body();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new EndeeException("Failed to list indexes", e);
        }
    }

    /**
     * Deletes an index.
     *
     * @param name the index name to delete
     * @return success message
     * @throws EndeeException if the operation fails
     */
    public String deleteIndex(String name) {
        try {
            HttpRequest request = buildDeleteRequest("/index/" + name + "/delete");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Error: {}", response.body());
                EndeeApiException.raiseException(response.statusCode(), response.body());
            }

            return "Index " + name + " deleted successfully";
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new EndeeException("Failed to delete index", e);
        }
    }

    /**
     * Gets an index by name.
     *
     * @param name the index name
     * @return the Index object for performing vector operations
     * @throws EndeeException if the operation fails
     */
    public Index getIndex(String name) {
        try {
            HttpRequest request = buildGetRequest("/index/" + name + "/info");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                EndeeApiException.raiseException(response.statusCode(), response.body());
            }

            JsonNode data = objectMapper.readTree(response.body());

            IndexInfo indexInfo = new IndexInfo();
            indexInfo.setSpaceType(SpaceType.fromValue(data.get("space_type").asText()));
            indexInfo.setDimension(data.get("dimension").asInt());
            indexInfo.setTotalElements(data.get("total_elements").asLong());
            indexInfo.setPrecision(Precision.fromValue(data.get("precision").asText()));
            indexInfo.setM(data.get("M").asInt());
            indexInfo.setChecksum(data.get("checksum").asLong());

            if (data.has("version") && !data.get("version").isNull()) {
                indexInfo.setVersion(data.get("version").asInt());
            }
            if (data.has("sparse_dim") && !data.get("sparse_dim").isNull()) {
                indexInfo.setSparseDimension(data.get("sparse_dim").asInt());
            }

            return new Index(name, token, baseUrl, version, indexInfo);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new EndeeException("Failed to get index", e);
        }
    }

    private HttpRequest buildGetRequest(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .timeout(DEFAULT_TIMEOUT)
                .GET();

        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", token);
        }

        return builder.build();
    }

    private HttpRequest buildPostRequest(String path, Map<String, Object> data) {
        String json = JsonUtils.toJson(data);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .timeout(DEFAULT_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(json));

        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", token);
        }

        return builder.build();
    }

    private HttpRequest buildDeleteRequest(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(DEFAULT_TIMEOUT)
                .DELETE();

        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", token);
        }

        return builder.build();
    }
}

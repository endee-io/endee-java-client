# Endee - Java Vector Database Client

Endee is a Java client for a local vector database designed for maximum speed and efficiency. This package provides type-safe operations, modern Java features, and optimized code for rapid Approximate Nearest Neighbor (ANN) searches on vector data.

## Key Features

- **Type Safe**: Full compile-time type checking with builder patterns
- **Fast ANN Searches**: Efficient similarity searches on vector data
- **Multiple Distance Metrics**: Support for cosine, L2, and inner product distance metrics
- **Hybrid Indexes**: Support for dense vectors, sparse vectors, and hybrid (dense + sparse) searches
- **Metadata Support**: Attach and search with metadata and filters
- **High Performance**: HTTP/2, MessagePack serialization, and DEFLATE compression
- **Modern Java**: Requires Java 17+, uses modern APIs

## Requirements

- Java 17 or higher
- Endee Local server running (see [Quick Start](https://docs.endee.io/quick-start))

## Installation

### Maven

```xml
<dependency>
    <groupId>io.endee</groupId>
    <artifactId>endee-java-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.endee:endee-java-client:1.0.0'
```

## Quick Start

### Initialize the Client

The Endee client connects to your local server (defaults to `http://127.0.0.1:8080/api/v1`):

```java
import io.endee.client.Endee;
import io.endee.client.Index;
import io.endee.client.types.*;

// Connect to local Endee server (defaults to localhost:8080)
Endee client = new Endee();
```

**Using Authentication?** If your server has `NDD_AUTH_TOKEN` set, pass the token when initializing:

```java
// With Auth Token
Endee client = new Endee("auth-token");
```

### Setting a Custom Base URL

If your server runs on a different port, use `setBaseUrl()`:

```java
Endee client = new Endee();

// Set custom base URL for non-default port
client.setBaseUrl("http://0.0.0.0:8081/api/v1");
```

### Create a Dense Index

```java
import io.endee.client.types.CreateIndexOptions;
import io.endee.client.types.Precision;
import io.endee.client.types.SpaceType;

CreateIndexOptions options = CreateIndexOptions.builder("my_vectors", 384)
    .spaceType(SpaceType.COSINE)
    .precision(Precision.INT8D)
    .build();

client.createIndex(options);
```

**Dense Index Parameters:**

| Parameter   | Description                                                                  | Default  |
| ----------- | ---------------------------------------------------------------------------- | -------- |
| `name`      | Unique name for your index (alphanumeric + underscore, max 48 chars)         | Required |
| `dimension` | Vector dimensionality (must match your embedding model's output, max 10,000) | Required |
| `spaceType` | Distance metric - `COSINE`, `L2`, or `IP` (inner product)                    | `COSINE` |
| `m`         | Graph connectivity - higher values increase recall but use more memory       | 16       |
| `efCon`     | Construction-time parameter - higher values improve index quality            | 128      |
| `precision` | Quantization precision                                                       | `INT8D`  |

### Create a Hybrid Index

Hybrid indexes combine dense vector search with sparse vector search. Add the `sparseDimension` parameter:

```java
CreateIndexOptions options = CreateIndexOptions.builder("hybrid_index", 384)
    .sparseDimension(30000)    // Sparse vector dimension (vocabulary size)
    .spaceType(SpaceType.COSINE)
    .precision(Precision.INT8D)
    .build();

client.createIndex(options);
```

### List and Access Indexes

```java
// List all indexes (returns JSON string)
String indexes = client.listIndexes();

// Get reference to an existing index
Index index = client.getIndex("my_vectors");

// Delete an index
client.deleteIndex("my_vectors");
```

## Upserting Vectors

The `index.upsert()` method adds or updates vectors in an existing index.

```java
import io.endee.client.types.VectorItem;
import java.util.List;
import java.util.Map;

Index index = client.getIndex("my_index");

List<VectorItem> vectors = List.of(
    VectorItem.builder("vec1", new double[] {0.1, 0.2, 0.3 /* ... */})
        .meta(Map.of("title", "First document", "group" , 10))      // meta : {"title" : "First Document", "label" : 10 }
        .filter(Map.of("category", "tech", "group" , 10))           // filter : {"category" : "tech" , "group" : 10}
        .build(),

    VectorItem.builder("vec2", new double[] {0.3, 0.4, 0.5 /* ... */})
        .meta(Map.of("title", "Second document"))
        .filter(Map.of("category", "science"))
        .build()
);

index.upsert(vectors);
```

**VectorItem Fields:**

| Field    | Required | Description                                         |
| -------- | -------- | --------------------------------------------------- |
| `id`     | Yes      | Unique identifier for the vector (non-empty string) |
| `vector` | Yes      | Array of doubles representing the embedding         |
| `meta`   | No       | Arbitrary metadata map                              |
| `filter` | No       | Key-value pairs for filtering during queries        |

**Limits:**

- Maximum 1,000 vectors per upsert call
- Vector dimension must match index dimension
- IDs must be unique within a single upsert batch

## Querying the Index

The `index.query()` method performs a similarity search.

```java
import io.endee.client.types.QueryOptions;
import io.endee.client.types.QueryResult;

List<QueryResult> results = index.query(
    QueryOptions.builder()
        .vector(new double[] {0.15, 0.25 /* ... */})
        .topK(5)
        .ef(128)
        .includeVectors(true)
        .build()
);

for (QueryResult item : results) {
    System.out.println("ID: " + item.getId());
    System.out.println("Similarity: " + item.getSimilarity());
    System.out.println("Distance: " + item.getDistance());
    System.out.println("Meta: " + item.getMeta());
}
```

**Query Parameters:**

| Parameter        | Description                                             | Default  | Max  |
| ---------------- | ------------------------------------------------------- | -------- | ---- |
| `vector`         | Query vector (must match index dimension)               | Required | -    |
| `topK`           | Number of results to return                             | 10       | 512  |
| `ef`             | Search quality parameter - higher values improve recall | 128      | 1024 |
| `includeVectors` | Include vector data in results                          | false    | -    |

## Filtered Querying

Use the `filter` parameter to restrict results. All filters are combined with **logical AND**.

```java
List<QueryResult> results = index.query(
    QueryOptions.builder()
        .vector(new double[] {0.15, 0.25 /* ... */})
        .topK(5)
        .filter(List.of(
            Map.of("category", Map.of("$eq", "tech")),
            Map.of("score", Map.of("$range", List.of(80, 100)))
        ))
        .build()
);
```

### Filtering Operators

| Operator | Description               | Example                                              |
| -------- | ------------------------- | ---------------------------------------------------- |
| `$eq`    | Exact match               | `Map.of("status", Map.of("$eq", "published"))`       |
| `$in`    | Match any in list         | `Map.of("tags", Map.of("$in", List.of("ai", "ml")))` |
| `$range` | Numeric range (inclusive) | `Map.of("score", Map.of("$range", List.of(70, 95)))` |

> **Note:** The `$range` operator supports values within **[0 - 999]**. Normalize larger values before upserting.

## Hybrid Search

### Upserting Hybrid Vectors

Provide both dense vectors and sparse representations:

```java
Index index = client.getIndex("hybrid_index");

List<VectorItem> vectors = List.of(
    VectorItem.builder("doc1", new double[] {0.1, 0.2 /* ... */})
        .sparseIndices(new int[] {10, 50, 200})       // Non-zero term positions
        .sparseValues(new double[] {0.8, 0.5, 0.3})   // Weights for each position
        .meta(Map.of("title", "Document 1"))
        .build(),

    VectorItem.builder("doc2", new double[] {0.3, 0.4 /* ... */})
        .sparseIndices(new int[] {15, 100, 500})
        .sparseValues(new double[] {0.9, 0.4, 0.6})
        .meta(Map.of("title", "Document 2"))
        .build()
);

index.upsert(vectors);
```

**Hybrid Vector Fields:**

| Field           | Required     | Description                              |
| --------------- | ------------ | ---------------------------------------- |
| `id`            | Yes          | Unique identifier                        |
| `vector`        | Yes          | Dense embedding vector                   |
| `sparseIndices` | Yes (hybrid) | Non-zero term positions in sparse vector |
| `sparseValues`  | Yes (hybrid) | Weights for each sparse index            |
| `meta`          | No           | Metadata map                             |
| `filter`        | No           | Filter fields                            |

> **Important:** `sparseIndices` and `sparseValues` must have the same length. Values in `sparseIndices` must be within `[0, sparseDimension)`.

### Querying Hybrid Index

Provide both dense and sparse query vectors:

```java
List<QueryResult> results = index.query(
    QueryOptions.builder()
        .vector(new double[] {0.15, 0.25 /* ... */})        // Dense query
        .sparseIndices(new int[] {10, 100, 300})            // Sparse query positions
        .sparseValues(new double[] {0.7, 0.5, 0.4})         // Sparse query weights
        .topK(5)
        .build()
);

for (QueryResult item : results) {
    System.out.println("ID: " + item.getId() + ", Similarity: " + item.getSimilarity());
}
```

You can also query with:

- **Dense only**: Provide only `vector`
- **Sparse only**: Provide only `sparseIndices` and `sparseValues`
- **Hybrid**: Provide all three for combined results

## Deletion Methods

### Delete by ID

Delete a vector with a specific vector ID.

```java
index.deleteVector("vec1");
```

### Delete by Filter

Delete all vectors matching specific filters.

```java
index.deleteWithFilter(List.of(
    Map.of("category", Map.of("$eq", "tech"))
));
```

### Delete Index

Delete an entire index.

```java
client.deleteIndex("my_index");
```

> **Warning:** Deletion operations are **irreversible**.

## Additional Operations

### Get Vector by ID

```java
import io.endee.client.types.VectorInfo;

VectorInfo vector = index.getVector("vec1");
System.out.println("ID: " + vector.getId());
System.out.println("Vector: " + Arrays.toString(vector.getVector()));
System.out.println("Meta: " + vector.getMeta());
System.out.println("Norm: " + vector.getNorm());
```

### Describe Index

```java
import io.endee.client.types.IndexDescription;

IndexDescription info = index.describe();
System.out.println(info);
// IndexDescription{name='my_index', spaceType=COSINE, dimension=384,
//                  sparseDimension=0, isHybrid=false, count=1000,
//                  precision=INT8D, m=16}
```

### Check if Index is Hybrid

```java
boolean isHybrid = index.isHybrid();
```

## Precision Options

Endee supports different quantization precision levels:

```java
import io.endee.client.types.Precision;

Precision.BINARY    // Binary quantization (1-bit) - smallest storage, fastest search
Precision.INT8D     // 8-bit integer quantization (default) - balanced performance
Precision.INT16D    // 16-bit integer quantization - higher precision
Precision.FLOAT16   // 16-bit floating point - good balance
Precision.FLOAT32   // 32-bit floating point - highest precision
```

**Choosing Precision:**

| Precision | Use Case                                                                  |
| --------- | ------------------------------------------------------------------------- |
| `BINARY`  | Very large datasets where speed and storage are critical                  |
| `INT8D`   | Recommended for most use cases - good balance of accuracy and performance |
| `INT16D`  | Better accuracy than INT8D but less storage than FLOAT32                  |
| `FLOAT16` | Good compromise between precision and storage for embeddings              |
| `FLOAT32` | Maximum precision when storage is not a concern                           |

## Space Types (Distance Metrics)

```java
import io.endee.client.types.SpaceType;

SpaceType.COSINE    // Cosine similarity (default) - best for normalized embeddings
SpaceType.L2        // Euclidean distance - best for spatial data
SpaceType.IP        // Inner product - best for unnormalized embeddings
```

## Error Handling

The client throws specific exceptions for different error scenarios:

```java
import io.endee.client.exception.EndeeException;
import io.endee.client.exception.EndeeApiException;

try {
    client.createIndex(options);
} catch (EndeeApiException e) {
    // API-specific errors (e.g., 400, 401, 404, 409, 500)
    System.err.println("Status Code: " + e.getStatusCode());
    System.err.println("Error Body: " + e.getErrorBody());
} catch (EndeeException e) {
    // Client errors (network, serialization, etc.)
    System.err.println("Client Error: " + e.getMessage());
} catch (IllegalArgumentException e) {
    // Validation errors
    System.err.println("Validation Error: " + e.getMessage());
}
```

**HTTP Status Codes:**

| Code | Description                                      |
| ---- | ------------------------------------------------ |
| 400  | Bad Request - Invalid parameters                 |
| 401  | Unauthorized - Invalid or missing authentication |
| 403  | Forbidden - Insufficient permissions             |
| 404  | Not Found - Index or vector doesn't exist        |
| 409  | Conflict - Index already exists                  |
| 500  | Internal Server Error                            |

## Complete Example

```java
import io.endee.client.Endee;
import io.endee.client.Index;
import io.endee.client.types.*;

import java.util.List;
import java.util.Map;

public class EndeeExample {
    public static void main(String[] args) {
        // Initialize client
        Endee client = new Endee();

        // Create a dense index
        CreateIndexOptions createOptions = CreateIndexOptions.builder("documents", 384)
            .spaceType(SpaceType.COSINE)
            .precision(Precision.INT8D)
            .build();

        client.createIndex(createOptions);

        // Get the index
        Index index = client.getIndex("documents");

        // Add vectors
        List<VectorItem> vectors = List.of(
            VectorItem.builder("doc1", new double[384]{/*...*/})  // 384 dimensions
                .meta(Map.of("title", "First Document"))
                .filter(Map.of("category", "tech"))
                .build(),

            VectorItem.builder("doc2", new double[384])
                .meta(Map.of("title", "Second Document"))
                .filter(Map.of("category", "science"))
                .build()
        );

        index.upsert(vectors);

        // Query the index
        List<QueryResult> results = index.query(
            QueryOptions.builder()
                .vector(new double[384])  // Query vector
                .topK(5)
                .build()
        );

        for (QueryResult item : results) {
            System.out.println("ID: " + item.getId() + ", Similarity: " + item.getSimilarity());
        }

        // Describe the index
        IndexDescription description = index.describe();
        System.out.println(description);

        // Clean up
        client.deleteIndex("documents");
    }
}
```

## API Reference

### Endee Class

| Method                            | Parameters | Return Type | Description                   |
| --------------------------------- | ---------- | ----------- | ----------------------------- |
| `Endee()`                         | -          | -           | Create client without auth    |
| `Endee(String token)`             | `token`    | -           | Create client with auth token |
| `setBaseUrl(String url)`          | `url`      | `String`    | Set custom base URL           |
| `createIndex(CreateIndexOptions)` | `options`  | `String`    | Create a new index            |
| `listIndexes()`                   | -          | `String`    | List all indexes (JSON)       |
| `deleteIndex(String name)`        | `name`     | `String`    | Delete an index               |
| `getIndex(String name)`           | `name`     | `Index`     | Get reference to an index     |

### Index Class

| Method                        | Parameters | Return Type         | Description                |
| ----------------------------- | ---------- | ------------------- | -------------------------- |
| `upsert(List<VectorItem>)`    | `vectors`  | `String`            | Insert or update vectors   |
| `query(QueryOptions)`         | `options`  | `List<QueryResult>` | Search for similar vectors |
| `deleteVector(String id)`     | `id`       | `String`            | Delete a vector by ID      |
| `deleteWithFilter(List<Map>)` | `filter`   | `String`            | Delete vectors by filter   |
| `getVector(String id)`        | `id`       | `VectorInfo`        | Get a vector by ID         |
| `describe()`                  | -          | `IndexDescription`  | Get index metadata         |
| `isHybrid()`                  | -          | `boolean`           | Check if index is hybrid   |

### Builder Classes

#### CreateIndexOptions.Builder

```java
CreateIndexOptions.builder(String name, int dimension)
    .spaceType(SpaceType)        // Default: COSINE
    .m(int)                      // Default: 16
    .efCon(int)                  // Default: 128
    .precision(Precision)        // Default: INT8D
    .sparseDimension(Integer)    // Optional, for hybrid indexes
    .build()
```

#### QueryOptions.Builder

```java
QueryOptions.builder()
    .vector(double[])                        // Required for dense search
    .topK(int)                               // Required
    .ef(int)                                 // Default: 128
    .filter(List<Map<String, Object>>)       // Optional
    .includeVectors(boolean)                 // Default: false
    .sparseIndices(int[])                    // Optional, for hybrid search
    .sparseValues(double[])                  // Optional, for hybrid search
    .build()
```

#### VectorItem.Builder

```java
VectorItem.builder(String id, double[] vector)
    .meta(Map<String, Object>)               // Optional
    .filter(Map<String, Object>)             // Optional
    .sparseIndices(int[])                    // Optional, for hybrid
    .sparseValues(double[])                  // Optional, for hybrid
    .build()
```

## Data Types

### QueryResult

| Field        | Type                  | Description                |
| ------------ | --------------------- | -------------------------- |
| `id`         | `String`              | Vector identifier          |
| `similarity` | `double`              | Similarity score           |
| `distance`   | `double`              | Distance (1 - similarity)  |
| `meta`       | `Map<String, Object>` | Metadata                   |
| `filter`     | `Map<String, Object>` | Filter values              |
| `norm`       | `double`              | Normalization factor       |
| `vector`     | `double[]`            | Vector data (if requested) |

### VectorInfo

| Field    | Type                  | Description          |
| -------- | --------------------- | -------------------- |
| `id`     | `String`              | Vector identifier    |
| `vector` | `double[]`            | Vector data          |
| `meta`   | `Map<String, Object>` | Metadata             |
| `filter` | `Map<String, Object>` | Filter values        |
| `norm`   | `double`              | Normalization factor |

### IndexDescription

| Field             | Type        | Description                          |
| ----------------- | ----------- | ------------------------------------ |
| `name`            | `String`    | Index name                           |
| `spaceType`       | `SpaceType` | Distance metric                      |
| `dimension`       | `int`       | Vector dimension                     |
| `sparseDimension` | `int`       | Sparse vector dimension              |
| `isHybrid`        | `boolean`   | Whether index supports hybrid search |
| `count`           | `long`      | Number of vectors                    |
| `precision`       | `Precision` | Quantization precision               |
| `m`               | `int`       | Graph connectivity                   |

## Dependencies

- Jackson (JSON serialization)
- MessagePack (Binary serialization)
- SLF4J (Logging)

## License

MIT

## Author

Pankaj Singh

# Endee - Java Vector Database Client

Endee is a Java client for the Endee vector database, designed for maximum speed and efficiency. This package provides type-safe operations, modern Java features, and optimized code for rapid Approximate Nearest Neighbor (ANN) searches on vector data.

## Key Features

- **Type Safe**: Full compile-time type checking with builder patterns
- **Fast ANN Searches**: Efficient similarity searches on vector data
- **Multiple Distance Metrics**: Cosine, L2, and inner product
- **Hybrid Indexes**: Dense + sparse (BM25 or default) vector search
- **Metadata & Filters**: Attach and query metadata with flexible filter operators
- **Typed Exceptions**: Specific exception types per HTTP error code
- **High Performance**: HTTP/2, MessagePack serialization, and DEFLATE compression
- **Modern Java**: Java 17+, uses modern APIs

## Requirements

- Java 17 or higher
- Endee server running (see [Quick Start](https://docs.endee.io/quick-start))

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

```java
import io.endee.client.Endee;
import io.endee.client.Index;
import io.endee.client.types.*;

// Local server (defaults to http://127.0.0.1:8080/api/v1)
Endee client = new Endee();

// With an auth token
Endee client = new Endee("account:password");

// With a region (connects to https://{region}.endee.io/api/v1)
Endee client = new Endee("account:password:us-east-1");

// Custom base URL
client.setBaseUrl("http://0.0.0.0:8081/api/v1");
```

---

## Index Management

### Create a Dense Index

```java
CreateIndexOptions options = CreateIndexOptions.builder("my_vectors", 384)
    .spaceType(SpaceType.COSINE)
    .precision(Precision.INT8)
    .m(16)
    .efCon(128)
    .build();

client.createIndex(options);
```

**Parameters:**

| Parameter   | Description                                                           | Default  | Constraints          |
|-------------|-----------------------------------------------------------------------|----------|----------------------|
| `name`      | Unique index name (alphanumeric + underscore)                         | required | max 48 chars         |
| `dimension` | Vector dimensionality (must match your embedding model)               | required | 2 – 8,000            |
| `spaceType` | Distance metric — `COSINE`, `L2`, `IP`                               | `COSINE` | —                    |
| `m`         | HNSW graph connectivity — higher = better recall, more memory         | `16`     | > 0                  |
| `efCon`     | HNSW construction quality — higher = better index, slower build       | `128`    | > 0                  |
| `precision` | Quantization level                                                    | `INT8`   | see Precision section |

### Create a Hybrid Index

Hybrid indexes support both dense and sparse vectors. Set `sparseModel` to enable sparse search:

```java
// Standard sparse search
CreateIndexOptions options = CreateIndexOptions.builder("hybrid_index", 384)
    .spaceType(SpaceType.COSINE)
    .precision(Precision.INT8)
    .sparseModel("default")       // or "endee_bm25" for BM25 scoring
    .build();

client.createIndex(options);
```

**`sparseModel` values:**

| Value          | Description                                     |
|----------------|-------------------------------------------------|
| `"default"`    | Standard sparse search without server-side IDF  |
| `"endee_bm25"` | BM25 scoring with server-side IDF               |
| `null`         | Dense-only index (omit `sparseModel` entirely)  |

### List, Get, and Delete Indexes

```java
// List all indexes (returns raw JSON string)
String indexes = client.listIndexes();

// Get a reference to an existing index
Index index = client.getIndex("my_vectors");

// Delete an index (irreversible)
client.deleteIndex("my_vectors");
```

---

## Upserting Vectors

### Dense Vectors

```java
Index index = client.getIndex("my_index");

List<VectorItem> vectors = List.of(
    VectorItem.builder("vec1", new double[] {0.1, 0.2, 0.3 /* ... */})
        .meta(Map.of("title", "First document", "score", 95))
        .filter(Map.of("category", "tech", "group", 1))
        .build(),

    VectorItem.builder("vec2", new double[] {0.4, 0.5, 0.6 /* ... */})
        .meta(Map.of("title", "Second document", "score", 80))
        .filter(Map.of("category", "science", "group", 2))
        .build()
);

index.upsert(vectors);
```

### Hybrid Vectors

For hybrid indexes, every upserted vector must supply both sparse fields:

```java
List<VectorItem> vectors = List.of(
    VectorItem.builder("doc1", new double[] {0.1, 0.2 /* ... */})
        .sparseIndices(new int[] {10, 50, 200})       // non-zero term positions
        .sparseValues(new double[] {0.8, 0.5, 0.3})   // weight for each position
        .meta(Map.of("title", "Document 1"))
        .filter(Map.of("category", "tech"))
        .build()
);

index.upsert(vectors);
```

**`VectorItem` fields:**

| Field           | Required      | Description                                                  |
|-----------------|---------------|--------------------------------------------------------------|
| `id`            | Yes           | Unique non-empty string identifier                           |
| `vector`        | Yes           | Dense embedding (length must equal index `dimension`)        |
| `meta`          | No            | Arbitrary metadata `Map` — stored compressed, not filterable |
| `filter`        | No            | Key-value pairs used for filtered queries                    |
| `sparseIndices` | Hybrid only   | Non-zero term positions in the sparse vector                 |
| `sparseValues`  | Hybrid only   | Weight for each sparse index (same length as `sparseIndices`)|

**Limits:**
- 1 – 1,000 vectors per `upsert` call
- IDs must be unique within a batch
- Vector values must be finite (no `NaN` or `Inf`)

---

## Querying

### Basic Dense Query

```java
List<QueryResult> results = index.query(
    QueryOptions.builder()
        .vector(new double[] {0.15, 0.25 /* ... */})
        .topK(5)
        .build()
);

for (QueryResult item : results) {
    System.out.println("ID: " + item.getId());
    System.out.println("Similarity: " + item.getSimilarity());
    System.out.println("Distance: " + item.getDistance());  // 1 - similarity
    System.out.println("Meta: " + item.getMeta());
    System.out.println("Vector: " + Arrays.toString(item.getVector())); // empty unless includeVectors=true
}
```

### Filtered Query

All filter conditions are combined with **logical AND**:

```java
List<QueryResult> results = index.query(
    QueryOptions.builder()
        .vector(new double[] {0.15, 0.25 /* ... */})
        .topK(10)
        .filter(List.of(
            Map.of("category", Map.of("$eq", "tech")),
            Map.of("score",    Map.of("$range", List.of(80, 100)))
        ))
        .build()
);
```

**Filter operators:**

| Operator  | Description               | Example                                               |
|-----------|---------------------------|-------------------------------------------------------|
| `$eq`     | Exact match               | `Map.of("status", Map.of("$eq", "published"))`        |
| `$in`     | Match any value in list   | `Map.of("tags", Map.of("$in", List.of("ai", "ml")))` |
| `$range`  | Numeric range (inclusive) | `Map.of("score", Map.of("$range", List.of(70, 95)))`  |

> `$range` supports integer values in **[0, 999]**. Normalize larger values before upserting.

### Hybrid Query

```java
List<QueryResult> results = index.query(
    QueryOptions.builder()
        .vector(new double[] {0.15, 0.25 /* ... */})      // dense component
        .sparseIndices(new int[] {10, 100, 300})           // sparse query positions
        .sparseValues(new double[] {0.7, 0.5, 0.4})        // sparse query weights
        .topK(5)
        .denseRrfWeight(0.7)    // weight for the dense component in RRF fusion (0.0–1.0)
        .rrfRankConstant(60)    // RRF rank constant (default 60)
        .build()
);
```

You can also query with only dense (`vector`) or only sparse (`sparseIndices` + `sparseValues`).

### All Query Options

```java
QueryOptions.builder()
    .vector(double[])                        // dense query vector
    .topK(int)                               // results to return (default: 10, max: 4,096)
    .ef(int)                                 // HNSW search depth (default: 128, max: 1,024)
    .filter(List<Map<String, Object>>)       // filter conditions (AND-combined)
    .includeVectors(boolean)                 // include vector data in results (default: false)
    .sparseIndices(int[])                    // sparse query positions (hybrid only)
    .sparseValues(double[])                  // sparse query weights (hybrid only)
    .denseRrfWeight(double)                  // dense RRF weight 0.0–1.0 (default: 0.5)
    .rrfRankConstant(int)                    // RRF rank constant ≥ 1 (default: 60)
    .prefilterCardinalityThreshold(int)      // switch to postfilter above this (default: 10,000, range: 1,000–1,000,000)
    .filterBoostPercentage(int)              // expand candidate pool toward filter matches (default: 0, range: 0–400)
    .build()
```

---

## CRUD Operations

### Get a Vector by ID

```java
VectorInfo info = index.getVector("vec1");
System.out.println("ID: "     + info.getId());
System.out.println("Vector: " + Arrays.toString(info.getVector()));
System.out.println("Meta: "   + info.getMeta());
System.out.println("Filter: " + info.getFilter());
System.out.println("Norm: "   + info.getNorm());

// For hybrid indexes, sparse fields are also populated:
System.out.println("SparseIndices: " + Arrays.toString(info.getSparseIndices()));
System.out.println("SparseValues: "  + Arrays.toString(info.getSparseValues()));
```

### Update Filters

Updates filter fields on existing vectors without re-upserting. The entire filter object is replaced:

```java
index.updateFilters(List.of(
    new UpdateFilterParams("vec1", Map.of("category", "ml", "score", 95)),
    new UpdateFilterParams("vec2", Map.of("category", "science", "score", 80))
));
```

### Delete by ID

```java
String result = index.deleteVector("vec1");
// returns e.g. "1 rows deleted"
```

### Delete by Filter

```java
index.deleteWithFilter(List.of(
    Map.of("category", Map.of("$eq", "tech"))
));
```

---

## Index Maintenance

### Describe Index

Returns stored metadata without a network call:

```java
IndexDescription desc = index.describe();
System.out.println(desc);
// {name='my_index', spaceType=COSINE, dimension=384, precision=INT8,
//  count=1000, isHybrid=true, sparseModel='default', M=16, efCon=128}
```

### Refresh Metadata

Fetches the latest metadata from the server and updates the local Index object:

```java
Map<String, Object> meta = index.refreshMetadata();
// returns: {count, space_type, dimension, precision, M, ef_con, sparse_model, is_hybrid}
```

### Rebuild Index

Rebuilds the HNSW graph with new parameters. Useful after bulk inserts or to tune recall:

```java
Map<String, Object> result = index.rebuild(16, 200);
// result: {status, previous_config, new_config, total_vectors}
```

> `rebuild()` first calls `refreshMetadata()` to verify the index is non-empty, then sends a `POST /rebuild` request. The server responds `202 Accepted` while the rebuild runs asynchronously.

### Rebuild Status

Poll the rebuild progress:

```java
Map<String, Object> status = index.rebuildStatus();
// status: {status: "in_progress"|"completed"|"failed"|"idle",
//          vectors_processed, total_vectors, percent_complete}
```

---

## Precision Options

| Value       | Wire     | Use Case                                                         |
|-------------|----------|------------------------------------------------------------------|
| `BINARY`    | `binary` | Maximum compression — 1 bit/dim, fastest search                 |
| `INT8`      | `int8`   | Default — best balance of accuracy and performance               |
| `INT16`     | `int16`  | Higher accuracy than INT8                                        |
| `FLOAT16`   | `float16`| Good compromise for embeddings                                   |
| `FLOAT32`   | `float32`| Maximum precision                                                |

## Space Types

| Value    | Wire     | Best For                               |
|----------|----------|----------------------------------------|
| `COSINE` | `cosine` | Normalized embeddings (default)        |
| `L2`     | `l2`     | Spatial / Euclidean distance           |
| `IP`     | `ip`     | Unnormalized embeddings (dot product)  |

---

## Error Handling

The client uses a typed exception hierarchy. All exceptions extend `EndeeException`:

```java
import io.endee.client.exception.*;

try {
    index.getVector("missing_id");
} catch (NotFoundException e) {
    System.err.println("Not found: " + e.getMessage());
} catch (AuthenticationException e) {
    System.err.println("Auth failed: " + e.getMessage());
} catch (EndeeApiException e) {
    // catch-all for any API error — provides status code and raw body
    System.err.println("HTTP " + e.getStatusCode() + ": " + e.getErrorBody());
} catch (EndeeException e) {
    // network / serialization errors
    System.err.println("Client error: " + e.getMessage());
} catch (IllegalArgumentException e) {
    // validation errors (invalid params, dimension mismatch, etc.)
    System.err.println("Validation: " + e.getMessage());
}
```

**Exception hierarchy:**

| Exception                | HTTP Status | Trigger                                |
|--------------------------|-------------|----------------------------------------|
| `EndeeApiException`      | 400         | Bad request / validation error (base)  |
| `AuthenticationException`| 401         | Invalid or expired token               |
| `SubscriptionException`  | 402         | Quota exceeded / tier limit            |
| `ForbiddenException`     | 403         | Insufficient permissions               |
| `NotFoundException`      | 404         | Index or vector not found              |
| `ConflictException`      | 409         | Resource already exists                |
| `ServerException`        | 5xx         | Server busy / internal error           |

All typed exceptions also extend `EndeeApiException`, so catching `EndeeApiException` handles every API error if you only need the status code.

---

## Complete Example

```java
import io.endee.client.Endee;
import io.endee.client.Index;
import io.endee.client.exception.*;
import io.endee.client.types.*;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Example {
    public static void main(String[] args) {
        Endee client = new Endee();

        // 1. Create a hybrid index
        client.createIndex(
            CreateIndexOptions.builder("docs", 384)
                .spaceType(SpaceType.COSINE)
                .precision(Precision.INT8)
                .sparseModel("default")
                .build()
        );

        // 2. Get index reference
        Index index = client.getIndex("docs");
        System.out.println("isHybrid: " + index.isHybrid()); // true

        // 3. Upsert vectors
        index.upsert(List.of(
            VectorItem.builder("doc1", new double[384])
                .sparseIndices(new int[]  {10, 500, 1200})
                .sparseValues( new double[]{0.8, 0.5, 0.3})
                .meta(Map.of("title", "Hello World"))
                .filter(Map.of("category", "tech", "score", 90))
                .build()
        ));

        // 4. Query
        List<QueryResult> results = index.query(
            QueryOptions.builder()
                .vector(new double[384])
                .sparseIndices(new int[]  {10, 500})
                .sparseValues( new double[]{0.9, 0.4})
                .topK(5)
                .denseRrfWeight(0.6)
                .filter(List.of(Map.of("category", Map.of("$eq", "tech"))))
                .includeVectors(true)
                .build()
        );

        for (QueryResult r : results) {
            System.out.printf("ID: %s  Similarity: %.4f  Meta: %s%n",
                r.getId(), r.getSimilarity(), r.getMeta());
        }

        // 5. Get a vector (hybrid returns sparse fields too)
        VectorInfo info = index.getVector("doc1");
        System.out.println("SparseIndices: " + Arrays.toString(info.getSparseIndices()));

        // 6. Update filter
        index.updateFilters(List.of(
            new UpdateFilterParams("doc1", Map.of("category", "ml", "score", 95))
        ));

        // 7. Rebuild index after bulk inserts
        Map<String, Object> rebuildResult = index.rebuild(16, 200);
        System.out.println("Rebuild: " + rebuildResult.get("status"));

        // 8. Poll rebuild status
        Map<String, Object> status = index.rebuildStatus();
        System.out.println("Status: " + status);

        // 9. Cleanup
        client.deleteIndex("docs");
    }
}
```

---

## API Reference

### `Endee`

| Method                              | Returns  | Description                        |
|-------------------------------------|----------|------------------------------------|
| `Endee()`                           | —        | Connect to local server            |
| `Endee(String token)`               | —        | Connect with auth token            |
| `setBaseUrl(String url)`            | `String` | Override the base URL              |
| `createIndex(CreateIndexOptions)`   | `String` | Create a new index                 |
| `listIndexes()`                     | `String` | List all indexes (raw JSON)        |
| `getIndex(String name)`             | `Index`  | Get an Index object                |
| `deleteIndex(String name)`          | `String` | Delete an index                    |

### `Index`

| Method                                    | Returns             | Description                              |
|-------------------------------------------|---------------------|------------------------------------------|
| `upsert(List<VectorItem>)`                | `String`            | Insert or update vectors                 |
| `query(QueryOptions)`                     | `List<QueryResult>` | Similarity search                        |
| `getVector(String id)`                    | `VectorInfo`        | Fetch a vector by ID                     |
| `updateFilters(List<UpdateFilterParams>)` | `String`            | Update filter fields without re-upserting|
| `deleteVector(String id)`                 | `String`            | Delete a vector by ID                    |
| `deleteWithFilter(List<Map>)`             | `String`            | Delete vectors matching a filter         |
| `describe()`                              | `IndexDescription`  | Return index metadata (no network call)  |
| `refreshMetadata()`                       | `Map<String,Object>`| Fetch + update metadata from server      |
| `rebuild(int m, int efCon)`               | `Map<String,Object>`| Trigger HNSW graph rebuild               |
| `rebuildStatus()`                         | `Map<String,Object>`| Poll rebuild progress                    |
| `isHybrid()`                              | `boolean`           | True when sparse_model ≠ "None"          |
| `getLibToken()`                           | `String`            | Library token from the server            |

### `CreateIndexOptions.Builder`

```java
CreateIndexOptions.builder(String name, int dimension)
    .spaceType(SpaceType)     // default: COSINE
    .m(int)                   // default: 16
    .efCon(int)               // default: 128
    .precision(Precision)     // default: INT8
    .sparseModel(String)      // "default" | "endee_bm25" | null (dense-only)
    .version(Integer)         // optional API version
    .build()
```

### `QueryOptions.Builder`

```java
QueryOptions.builder()
    .vector(double[])                    // dense query vector
    .topK(int)                           // default: 10, range: 1–4,096
    .ef(int)                             // default: 128, max: 1,024
    .filter(List<Map<String, Object>>)   // AND-combined filter conditions
    .includeVectors(boolean)             // default: false
    .sparseIndices(int[])                // hybrid only
    .sparseValues(double[])              // hybrid only
    .denseRrfWeight(double)              // default: 0.5, range: 0.0–1.0
    .rrfRankConstant(int)                // default: 60, min: 1
    .prefilterCardinalityThreshold(int)  // default: 10,000, range: 1,000–1,000,000
    .filterBoostPercentage(int)          // default: 0, range: 0–400
    .build()
```

### `VectorItem.Builder`

```java
VectorItem.builder(String id, double[] vector)
    .meta(Map<String, Object>)     // arbitrary metadata
    .filter(Map<String, Object>)   // filterable key-value fields
    .sparseIndices(int[])          // hybrid only
    .sparseValues(double[])        // hybrid only
    .build()
```

---

## Data Types

### `QueryResult`

| Field        | Type                  | Description                                      |
|--------------|-----------------------|--------------------------------------------------|
| `id`         | `String`              | Vector ID                                        |
| `similarity` | `double`              | Similarity score                                 |
| `distance`   | `double`              | Distance (`1 - similarity`)                      |
| `meta`       | `Map<String, Object>` | Metadata                                         |
| `filter`     | `Map<String, Object>` | Filter values (omitted when empty)               |
| `norm`       | `double`              | L2 norm of the original vector                   |
| `vector`     | `double[]`            | Vector data — empty `[]` unless `includeVectors` |

### `VectorInfo`

| Field           | Type                  | Description                          |
|-----------------|-----------------------|--------------------------------------|
| `id`            | `String`              | Vector ID                            |
| `vector`        | `double[]`            | Dense vector data                    |
| `meta`          | `Map<String, Object>` | Metadata                             |
| `filter`        | `Map<String, Object>` | Filter values                        |
| `norm`          | `double`              | L2 norm                              |
| `sparseIndices` | `int[]`               | Sparse positions (hybrid only)       |
| `sparseValues`  | `double[]`            | Sparse weights (hybrid only)         |

### `IndexDescription`

| Field         | Type        | Description                              |
|---------------|-------------|------------------------------------------|
| `name`        | `String`    | Index name                               |
| `spaceType`   | `SpaceType` | Distance metric                          |
| `dimension`   | `int`       | Dense vector dimension                   |
| `sparseModel` | `String`    | `"default"`, `"endee_bm25"`, or `"None"` |
| `isHybrid`    | `boolean`   | True when sparse_model ≠ `"None"`        |
| `count`       | `long`      | Number of vectors in the index           |
| `precision`   | `Precision` | Quantization precision                   |
| `m`           | `int`       | HNSW M parameter                         |
| `efCon`       | `int`       | HNSW ef_construction                     |

---

## Code Formatting

This project uses [Spotless](https://github.com/diffplug/spotless) with [Google Java Format](https://github.com/google/google-java-format).

```bash
mvn spotless:apply   # auto-format all source files
mvn spotless:check   # verify formatting (runs in CI)
```

## Dependencies

- **Jackson** — JSON serialization
- **MessagePack** — binary serialization for vector payloads
- **SLF4J** — logging facade

## License

MIT

## Author

Pankaj Singh

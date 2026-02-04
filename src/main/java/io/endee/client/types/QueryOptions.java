package io.endee.client.types;

import java.util.List;
import java.util.Map;

/**
 * Options for querying an Endee index.
 *
 * <p>Example usage with filters:</p>
 * <pre>{@code
 * QueryOptions options = QueryOptions.builder()
 *     .vector(new double[]{0.1, 0.2, 0.3})
 *     .topK(10)
 *     .filter(List.of(
 *         Map.of("category", Map.of("$eq", "tech")),
 *         Map.of("score", Map.of("$range", List.of(80, 100)))
 *     ))
 *     .build();
 * }</pre>
 */
public class QueryOptions {
    private double[] vector;
    private int topK = 10;
    private List<Map<String, Object>> filter;
    private int ef = 128;
    private boolean includeVectors = false;
    private int[] sparseIndices;
    private double[] sparseValues;

    private QueryOptions() {}

    public static Builder builder() {
        return new Builder();
    }

    public double[] getVector() { return vector; }
    public int getTopK() { return topK; }
    public List<Map<String, Object>> getFilter() { return filter; }
    public int getEf() { return ef; }
    public boolean isIncludeVectors() { return includeVectors; }
    public int[] getSparseIndices() { return sparseIndices; }
    public double[] getSparseValues() { return sparseValues; }

    public static class Builder {
        private final QueryOptions options = new QueryOptions();

        public Builder vector(double[] vector) {
            options.vector = vector;
            return this;
        }

        public Builder topK(int topK) {
            options.topK = topK;
            return this;
        }

        /**
         * Sets the filter conditions as an array of filter objects.
         *
         * @param filter list of filter conditions, e.g.:
         *               [{"category": {"$eq": "tech"}}, {"score": {"$range": [80, 100]}}]
         * @return this builder
         */
        public Builder filter(List<Map<String, Object>> filter) {
            options.filter = filter;
            return this;
        }

        public Builder ef(int ef) {
            options.ef = ef;
            return this;
        }

        public Builder includeVectors(boolean includeVectors) {
            options.includeVectors = includeVectors;
            return this;
        }

        public Builder sparseIndices(int[] sparseIndices) {
            options.sparseIndices = sparseIndices;
            return this;
        }

        public Builder sparseValues(double[] sparseValues) {
            options.sparseValues = sparseValues;
            return this;
        }

        public QueryOptions build() {
            return options;
        }
    }
}

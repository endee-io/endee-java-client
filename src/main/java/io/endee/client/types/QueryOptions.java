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
    private static final int DEFAULT_PREFILTER_CARDINALITY_THRESHOLD = 10_000;

    private double[] vector;
    private int topK;
    private List<Map<String, Object>> filter;
    private int ef = 128;
    private boolean includeVectors = false;
    private int[] sparseIndices;
    private double[] sparseValues;
    private int prefilterCardinalityThreshold = DEFAULT_PREFILTER_CARDINALITY_THRESHOLD;
    private int filterBoostPercentage = 0;

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
    public int getPrefilterCardinalityThreshold() { return prefilterCardinalityThreshold; }
    public int getFilterBoostPercentage() { return filterBoostPercentage; }

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

        /**
         * Sets the prefilter cardinality threshold. When the estimated number of
         * matching vectors exceeds this value, postfiltering is used instead.
         * Must be between 1,000 and 1,000,000. Default: 10,000.
         */
        public Builder prefilterCardinalityThreshold(int prefilterCardinalityThreshold) {
            options.prefilterCardinalityThreshold = prefilterCardinalityThreshold;
            return this;
        }

        /**
         * Sets the filter boost percentage (0-100). Higher values bias results
         * toward filter matches. Default: 0.
         */
        public Builder filterBoostPercentage(int filterBoostPercentage) {
            options.filterBoostPercentage = filterBoostPercentage;
            return this;
        }

        public QueryOptions build() {
            return options;
        }
    }
}

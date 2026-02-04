package io.endee.client.types;

import java.util.Map;

/**
 * A vector item for upsert operations.
 */
public class VectorItem {
    private final String id;
    private final double[] vector;
    private Map<String, Object> meta;
    private Map<String, Object> filter;
    private int[] sparseIndices;
    private double[] sparseValues;

    private VectorItem(String id, double[] vector) {
        this.id = id;
        this.vector = vector;
    }

    public static Builder builder(String id, double[] vector) {
        return new Builder(id, vector);
    }

    public String getId() { return id; }
    public double[] getVector() { return vector; }
    public Map<String, Object> getMeta() { return meta; }
    public Map<String, Object> getFilter() { return filter; }
    public int[] getSparseIndices() { return sparseIndices; }
    public double[] getSparseValues() { return sparseValues; }

    public static class Builder {
        private final VectorItem item;

        private Builder(String id, double[] vector) {
            this.item = new VectorItem(id, vector);
        }

        public Builder meta(Map<String, Object> meta) {
            item.meta = meta;
            return this;
        }

        public Builder filter(Map<String, Object> filter) {
            item.filter = filter;
            return this;
        }

        public Builder sparseIndices(int[] sparseIndices) {
            item.sparseIndices = sparseIndices;
            return this;
        }

        public Builder sparseValues(double[] sparseValues) {
            item.sparseValues = sparseValues;
            return this;
        }

        public VectorItem build() {
            return item;
        }
    }
}

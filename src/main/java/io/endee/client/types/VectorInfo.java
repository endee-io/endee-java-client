package io.endee.client.types;

import java.util.Map;

/**
 * Information about a vector retrieved from an index.
 */
public class VectorInfo {
    private String id;
    private Map<String, Object> meta;
    private Map<String, Object> filter;
    private double norm;
    private double[] vector;

    public VectorInfo() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }

    public Map<String, Object> getFilter() { return filter; }
    public void setFilter(Map<String, Object> filter) { this.filter = filter; }

    public double getNorm() { return norm; }
    public void setNorm(double norm) { this.norm = norm; }

    public double[] getVector() { return vector; }
    public void setVector(double[] vector) { this.vector = vector; }

    @Override
    public String toString() {
        return "VectorInfo{id='" + id + "', norm=" + norm + ", vectorLength=" + (vector != null ? vector.length : 0) + "}";
    }
}

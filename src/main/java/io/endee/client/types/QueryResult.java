package io.endee.client.types;

import java.util.Map;

/**
 * Result from a query operation.
 */
public class QueryResult {
    private String id;
    private double similarity;
    private double distance;
    private Map<String, Object> meta;
    private double norm;
    private Map<String, Object> filter;
    private double[] vector;

    public QueryResult() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public double getSimilarity() { return similarity; }
    public void setSimilarity(double similarity) { this.similarity = similarity; }

    public double getDistance() { return distance; }
    public void setDistance(double distance) { this.distance = distance; }

    public Map<String, Object> getMeta() { return meta; }
    public void setMeta(Map<String, Object> meta) { this.meta = meta; }

    public double getNorm() { return norm; }
    public void setNorm(double norm) { this.norm = norm; }

    public Map<String, Object> getFilter() { return filter; }
    public void setFilter(Map<String, Object> filter) { this.filter = filter; }

    public double[] getVector() { return vector; }
    public void setVector(double[] vector) { this.vector = vector; }

    @Override
    public String toString() {
        return "QueryResult{id='" + id + "', similarity=" + similarity + ", distance=" + distance + "}";
    }
}

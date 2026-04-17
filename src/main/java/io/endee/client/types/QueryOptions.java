package io.endee.client.types;

import java.util.List;
import java.util.Map;

/**
 * Options for querying an Endee index.
 *
 * <p>Example usage with filters:
 *
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
  private int prefilterCardinalityThreshold = 10_000;
  private int filterBoostPercentage = 0;
  private double denseRrfWeight = 0.5;
  private int rrfRankConstant = 60;

  private QueryOptions() {}

  public static Builder builder() {
    return new Builder();
  }

  public double[] getVector() {
    return vector;
  }

  public int getTopK() {
    return topK;
  }

  public List<Map<String, Object>> getFilter() {
    return filter;
  }

  public int getEf() {
    return ef;
  }

  public boolean isIncludeVectors() {
    return includeVectors;
  }

  public int[] getSparseIndices() {
    return sparseIndices;
  }

  public double[] getSparseValues() {
    return sparseValues;
  }

  public int getPrefilterCardinalityThreshold() {
    return prefilterCardinalityThreshold;
  }

  public int getFilterBoostPercentage() {
    return filterBoostPercentage;
  }

  public double getDenseRrfWeight() {
    return denseRrfWeight;
  }

  public int getRrfRankConstant() {
    return rrfRankConstant;
  }

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
     * @param filter list of filter conditions, e.g.: [{"category": {"$eq": "tech"}}, {"score":
     *     {"$range": [80, 100]}}]
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
     * Switches from HNSW to brute-force when estimated matching vectors exceeds this value. Range:
     * 1,000 – 1,000,000. Default: 10,000.
     */
    public Builder prefilterCardinalityThreshold(int prefilterCardinalityThreshold) {
      options.prefilterCardinalityThreshold = prefilterCardinalityThreshold;
      return this;
    }

    /**
     * Expands the HNSW candidate pool by this percentage to bias results toward filter matches.
     * Range: 0 – 400. Default: 0.
     */
    public Builder filterBoostPercentage(int filterBoostPercentage) {
      options.filterBoostPercentage = filterBoostPercentage;
      return this;
    }

    /** RRF weight for the dense component in hybrid search. Range: 0.0 – 1.0. Default: 0.5. */
    public Builder denseRrfWeight(double denseRrfWeight) {
      options.denseRrfWeight = denseRrfWeight;
      return this;
    }

    /** RRF rank constant used in hybrid search scoring. Minimum: 1. Default: 60. */
    public Builder rrfRankConstant(int rrfRankConstant) {
      options.rrfRankConstant = rrfRankConstant;
      return this;
    }

    public QueryOptions build() {
      return options;
    }
  }
}

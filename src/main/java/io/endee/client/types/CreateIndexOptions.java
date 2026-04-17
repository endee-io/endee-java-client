package io.endee.client.types;

/** Options for creating an Endee index. */
public class CreateIndexOptions {
  private final String name;
  private final int dimension;
  private SpaceType spaceType = SpaceType.COSINE;
  private int m = 16;
  private int efCon = 128;
  private Precision precision = Precision.INT8;
  private Integer version = null;
  private String sparseModel = null;

  private CreateIndexOptions(String name, int dimension) {
    this.name = name;
    this.dimension = dimension;
  }

  public static Builder builder(String name, int dimension) {
    return new Builder(name, dimension);
  }

  public String getName() {
    return name;
  }

  public int getDimension() {
    return dimension;
  }

  public SpaceType getSpaceType() {
    return spaceType;
  }

  public int getM() {
    return m;
  }

  public int getEfCon() {
    return efCon;
  }

  public Precision getPrecision() {
    return precision;
  }

  public Integer getVersion() {
    return version;
  }

  public String getSparseModel() {
    return sparseModel;
  }

  public static class Builder {
    private final CreateIndexOptions options;

    private Builder(String name, int dimension) {
      this.options = new CreateIndexOptions(name, dimension);
    }

    public Builder spaceType(SpaceType spaceType) {
      options.spaceType = spaceType;
      return this;
    }

    public Builder m(int m) {
      options.m = m;
      return this;
    }

    public Builder efCon(int efCon) {
      options.efCon = efCon;
      return this;
    }

    public Builder precision(Precision precision) {
      options.precision = precision;
      return this;
    }

    public Builder version(Integer version) {
      options.version = version;
      return this;
    }

    /**
     * Sets the sparse model for hybrid indexing.
     *
     * @param sparseModel {@code "default"} for standard sparse search, {@code "endee_bm25"} for
     *     BM25 scoring. Pass {@code null} for a dense-only index.
     */
    public Builder sparseModel(String sparseModel) {
      options.sparseModel = sparseModel;
      return this;
    }

    public CreateIndexOptions build() {
      return options;
    }
  }
}

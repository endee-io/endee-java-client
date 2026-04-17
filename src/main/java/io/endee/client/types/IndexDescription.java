package io.endee.client.types;

/** Description of an Endee index, returned by {@link io.endee.client.Index#describe()}. */
public class IndexDescription {
  private final String name;
  private final SpaceType spaceType;
  private final int dimension;
  private final String sparseModel;
  private final boolean isHybrid;
  private final long count;
  private final Precision precision;
  private final int m;
  private final int efCon;

  public IndexDescription(
      String name,
      SpaceType spaceType,
      int dimension,
      String sparseModel,
      boolean isHybrid,
      long count,
      Precision precision,
      int m,
      int efCon) {
    this.name = name;
    this.spaceType = spaceType;
    this.dimension = dimension;
    this.sparseModel = sparseModel;
    this.isHybrid = isHybrid;
    this.count = count;
    this.precision = precision;
    this.m = m;
    this.efCon = efCon;
  }

  public String getName() {
    return name;
  }

  public SpaceType getSpaceType() {
    return spaceType;
  }

  public int getDimension() {
    return dimension;
  }

  public String getSparseModel() {
    return sparseModel;
  }

  public boolean isHybrid() {
    return isHybrid;
  }

  public long getCount() {
    return count;
  }

  public Precision getPrecision() {
    return precision;
  }

  public int getM() {
    return m;
  }

  public int getEfCon() {
    return efCon;
  }

  @Override
  public String toString() {
    return "{name='"
        + name
        + "', spaceType="
        + spaceType
        + ", dimension="
        + dimension
        + ", precision="
        + precision
        + ", count="
        + count
        + ", isHybrid="
        + isHybrid
        + ", sparseModel='"
        + sparseModel
        + "', M="
        + m
        + ", efCon="
        + efCon
        + "}";
  }
}

package io.endee.client.types;

/** Description of an Endee index. */
public class IndexDescription {
  private final String name;
  private final SpaceType spaceType;
  private final int dimension;
  private final int sparseDimension;
  private final boolean isHybrid;
  private final long count;
  private final Precision precision;
  private final int m;
  private final int efCon;

  public IndexDescription(
      String name,
      SpaceType spaceType,
      int dimension,
      int sparseDimension,
      boolean isHybrid,
      long count,
      Precision precision,
      int m,
      int efCon) {
    this.name = name;
    this.spaceType = spaceType;
    this.dimension = dimension;
    this.sparseDimension = sparseDimension;
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

  public int getSparseDimension() {
    return sparseDimension;
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
        + "', spaceType= "
        + spaceType
        + ", dimension="
        + dimension
        + ", precision="
        + precision
        + ", count="
        + count
        + ", isHybrid="
        + isHybrid
        + ", sparseDimension="
        + sparseDimension
        + ", M="
        + m
        + ", efCon="
        + efCon
        + "}";
  }
}

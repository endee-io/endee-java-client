package io.endee.client.types;

/** Information about an Endee index from the server. */
public class IndexInfo {
  private String name;
  private SpaceType spaceType;
  private int dimension;
  private long totalElements;
  private Precision precision;
  private int m;
  private long checksum;
  private int version;
  private String sparseModel;
  private String libToken;
  private int efCon;

  public IndexInfo() {}

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public SpaceType getSpaceType() {
    return spaceType;
  }

  public void setSpaceType(SpaceType spaceType) {
    this.spaceType = spaceType;
  }

  public int getDimension() {
    return dimension;
  }

  public void setDimension(int dimension) {
    this.dimension = dimension;
  }

  public long getTotalElements() {
    return totalElements;
  }

  public void setTotalElements(long totalElements) {
    this.totalElements = totalElements;
  }

  public Precision getPrecision() {
    return precision;
  }

  public void setPrecision(Precision precision) {
    this.precision = precision;
  }

  public int getM() {
    return m;
  }

  public void setM(int m) {
    this.m = m;
  }

  public long getChecksum() {
    return checksum;
  }

  public void setChecksum(long checksum) {
    this.checksum = checksum;
  }

  public int getVersion() {
    return version;
  }

  public void setVersion(int version) {
    this.version = version;
  }

  public String getSparseModel() {
    return sparseModel;
  }

  public void setSparseModel(String sparseModel) {
    this.sparseModel = sparseModel;
  }

  public String getLibToken() {
    return libToken;
  }

  public void setLibToken(String libToken) {
    this.libToken = libToken;
  }

  public int getEfCon() {
    return efCon;
  }

  public void setEfCon(int efCon) {
    this.efCon = efCon;
  }
}

package io.endee.client.types;

import java.util.Map;

/** Parameters for updating the filter fields of an existing vector. */
public class UpdateFilterParams {
  private final String id;
  private final Map<String, Object> filter;

  public UpdateFilterParams(String id, Map<String, Object> filter) {
    this.id = id;
    this.filter = filter;
  }

  public String getId() {
    return id;
  }

  public Map<String, Object> getFilter() {
    return filter;
  }
}

package com.pia.mockserver.model;

/**
 * @author Gokhan Demir
 */
public class Id {
  private boolean provided;
  private String id;
  private String version;

  public String getHrefSuffix() {
    return version == null ? id : id + ":(version=" + version + ")";
  }

  public String getCompositeId() {
    return version == null ? id : id + "-" + version;
  }

  public boolean isProvided() {
    return provided;
  }

  public void setProvided(boolean provided) {
    this.provided = provided;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }
}

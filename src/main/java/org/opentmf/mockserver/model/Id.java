package org.opentmf.mockserver.model;

import java.util.Objects;

/**
 * @author Gokhan Demir
 */
public class Id implements Comparable<Id> {

  private String id;
  private String version;

  public String getHrefSuffix() {
    return version == null ? id : id + ":(version=" + version + ")";
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

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Id)) {
      return false;
    }
    Id that = (Id) obj;
    return Objects.equals(id, that.id) && Objects.equals(version, that.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, version);
  }

  @Override
  public int compareTo(Id other) {
    // First compare by id since it's mandatory
    int idCompare = this.id.compareTo(other.id);
    if (idCompare != 0) {
      return idCompare;
    }
    // If ids are equal, handle version comparison with null checking
    if (this.version == null && other.version == null) {
      return 0;
    }
    if (this.version == null) {
      return -1;  // Null version comes first
    }
    if (other.version == null) {
      return 1;   // Null version comes first
    }
    return this.version.compareTo(other.version);
  }

  @Override
  public String toString() {
    return "id='" + id + "', version=" +
        (version == null ? "null" : "'" + version + "'");
  }
}

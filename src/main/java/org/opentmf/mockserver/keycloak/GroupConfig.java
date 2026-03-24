package org.opentmf.mockserver.keycloak;

import java.util.Collections;
import java.util.List;

public class GroupConfig {

  private String name;
  private List<String> subGroups = Collections.emptyList();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<String> getSubGroups() {
    return subGroups;
  }

  public void setSubGroups(List<String> subGroups) {
    this.subGroups = subGroups;
  }
}

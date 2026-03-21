package org.opentmf.mockserver.keycloak;

import java.util.Collections;
import java.util.List;

public class UserConfig {

  private String username;
  private String password;
  private List<String> roles = Collections.emptyList();

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(List<String> roles) {
    this.roles = roles;
  }
}

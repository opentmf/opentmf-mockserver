package org.opentmf.mockserver.keycloak;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RealmConfig {

  private String name;
  private List<String> roles = Collections.emptyList();
  private List<ClientConfig> clients = Collections.emptyList();
  private List<UserConfig> users = Collections.emptyList();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<String> getRoles() {
    return roles;
  }

  public void setRoles(List<String> roles) {
    this.roles = roles;
  }

  public List<ClientConfig> getClients() {
    return clients;
  }

  public void setClients(List<ClientConfig> clients) {
    this.clients = clients;
  }

  public List<UserConfig> getUsers() {
    return users;
  }

  public void setUsers(List<UserConfig> users) {
    this.users = users;
  }

  public Optional<ClientConfig> findClient(String clientId) {
    return clients.stream()
        .filter(c -> c.getClientId().equals(clientId))
        .findFirst();
  }

  public Optional<UserConfig> findUser(String username) {
    return users.stream()
        .filter(u -> u.getUsername().equals(username))
        .findFirst();
  }
}

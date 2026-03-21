package org.opentmf.mockserver.keycloak;

import java.util.Collections;
import java.util.List;

public class ClientConfig {

  private String clientId;
  private String clientSecret;
  private boolean publicClient;
  private List<String> allowedGrantTypes = Collections.emptyList();
  private List<String> serviceAccountRoles;

  public String getClientId() {
    return clientId;
  }

  public void setClientId(String clientId) {
    this.clientId = clientId;
  }

  public String getClientSecret() {
    return clientSecret;
  }

  public void setClientSecret(String clientSecret) {
    this.clientSecret = clientSecret;
  }

  public boolean isPublicClient() {
    return publicClient;
  }

  public void setPublicClient(boolean publicClient) {
    this.publicClient = publicClient;
  }

  public List<String> getAllowedGrantTypes() {
    return allowedGrantTypes;
  }

  public void setAllowedGrantTypes(List<String> allowedGrantTypes) {
    this.allowedGrantTypes = allowedGrantTypes;
  }

  /**
   * Roles assigned to the service account for {@code client_credentials} grant.
   * If {@code null}, all realm roles are used as a default.
   */
  public List<String> getServiceAccountRoles() {
    return serviceAccountRoles;
  }

  public void setServiceAccountRoles(List<String> serviceAccountRoles) {
    this.serviceAccountRoles = serviceAccountRoles;
  }
}

package org.opentmf.mockserver.keycloak;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

class KeycloakConfigTests {

  @Test
  void defaultConfigLoadsFromClasspath() {
    KeycloakConfig config = KeycloakConfig.getInstance();
    assertNotNull(config);
    assertFalse(config.getRealms().isEmpty(), "Default config must have at least one realm");
  }

  @Test
  void defaultConfigContainsRealm1() {
    KeycloakConfig config = KeycloakConfig.getInstance();
    Optional<RealmConfig> realm = config.findRealm("realm1");
    assertTrue(realm.isPresent(), "realm1 must exist in default config");
  }

  @Test
  void realm1HasExpectedClients() {
    RealmConfig realm = KeycloakConfig.getInstance().findRealm("realm1").orElseThrow();

    Optional<ClientConfig> client1 = realm.findClient("client1");
    assertTrue(client1.isPresent());
    assertFalse(client1.get().isPublicClient());
    assertEquals("client1Secret", client1.get().getClientSecret());
    assertTrue(client1.get().getAllowedGrantTypes().contains("client_credentials"));

    Optional<ClientConfig> client2 = realm.findClient("client2");
    assertTrue(client2.isPresent());
    assertFalse(client2.get().isPublicClient());
    assertTrue(client2.get().getAllowedGrantTypes().contains("password"));

    Optional<ClientConfig> uiClient = realm.findClient("uiClient");
    assertTrue(uiClient.isPresent());
    assertTrue(uiClient.get().isPublicClient());
    assertNull(uiClient.get().getClientSecret());
  }

  @Test
  void realm1HasExpectedUsers() {
    RealmConfig realm = KeycloakConfig.getInstance().findRealm("realm1").orElseThrow();

    Optional<UserConfig> admin = realm.findUser("admin_usr");
    assertTrue(admin.isPresent());
    assertEquals("admin_pwd", admin.get().getPassword());
    assertEquals(3, admin.get().getRoles().size());
    assertTrue(admin.get().getRoles().contains("admin"));

    Optional<UserConfig> writer = realm.findUser("writer_usr");
    assertTrue(writer.isPresent());
    assertEquals(2, writer.get().getRoles().size());

    Optional<UserConfig> reader = realm.findUser("reader_usr");
    assertTrue(reader.isPresent());
    assertEquals(1, reader.get().getRoles().size());
    assertTrue(reader.get().getRoles().contains("reader"));
  }

  @Test
  void realm1HasExpectedRoles() {
    RealmConfig realm = KeycloakConfig.getInstance().findRealm("realm1").orElseThrow();
    assertEquals(3, realm.getRoles().size());
    assertTrue(realm.getRoles().contains("admin"));
    assertTrue(realm.getRoles().contains("writer"));
    assertTrue(realm.getRoles().contains("reader"));
  }

  @Test
  void findRealmReturnsEmptyForUnknown() {
    assertFalse(KeycloakConfig.getInstance().findRealm("nonexistent").isPresent());
  }

  @Test
  void baseUrlHasDefault() {
    assertNotNull(KeycloakConfig.getInstance().getBaseUrl());
    assertFalse(KeycloakConfig.getInstance().getBaseUrl().isEmpty());
  }

  @Test
  void jacksonDeserializesAllConfigFields() throws Exception {
    String json =
        "{"
            + "\"baseUrl\":\"http://custom:9090\","
            + "\"realms\":[{"
            + "  \"name\":\"testRealm\","
            + "  \"roles\":[\"role1\",\"role2\"],"
            + "  \"clients\":[{"
            + "    \"clientId\":\"c1\","
            + "    \"clientSecret\":\"s1\","
            + "    \"publicClient\":true,"
            + "    \"allowedGrantTypes\":[\"client_credentials\",\"password\"],"
            + "    \"serviceAccountRoles\":[\"role1\"]"
            + "  }],"
            + "  \"users\":[{"
            + "    \"username\":\"u1\","
            + "    \"password\":\"p1\","
            + "    \"roles\":[\"role1\",\"role2\"]"
            + "  }]"
            + "}]}";

    ObjectMapper mapper =
        tools.jackson.databind.json.JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
    KeycloakConfig config = mapper.readValue(json, KeycloakConfig.class);

    assertEquals("http://custom:9090", config.getBaseUrl());
    assertEquals(1, config.getRealms().size());

    RealmConfig realm = config.getRealms().get(0);
    assertEquals("testRealm", realm.getName());
    assertEquals(Arrays.asList("role1", "role2"), realm.getRoles());

    List<ClientConfig> clients = realm.getClients();
    assertEquals(1, clients.size());
    ClientConfig client = clients.get(0);
    assertEquals("c1", client.getClientId());
    assertEquals("s1", client.getClientSecret());
    assertTrue(client.isPublicClient());
    assertEquals(Arrays.asList("client_credentials", "password"), client.getAllowedGrantTypes());
    assertEquals(List.of("role1"), client.getServiceAccountRoles());

    List<UserConfig> users = realm.getUsers();
    assertEquals(1, users.size());
    UserConfig user = users.get(0);
    assertEquals("u1", user.getUsername());
    assertEquals("p1", user.getPassword());
    assertEquals(Arrays.asList("role1", "role2"), user.getRoles());
  }

  @Test
  void settersWorkOnAllConfigClasses() {
    ClientConfig client = new ClientConfig();
    client.setClientId("cid");
    client.setClientSecret("csec");
    client.setPublicClient(true);
    client.setAllowedGrantTypes(List.of("password"));
    client.setServiceAccountRoles(List.of("admin"));
    assertEquals("cid", client.getClientId());
    assertEquals("csec", client.getClientSecret());
    assertTrue(client.isPublicClient());
    assertEquals(List.of("password"), client.getAllowedGrantTypes());
    assertEquals(List.of("admin"), client.getServiceAccountRoles());

    UserConfig user = new UserConfig();
    user.setUsername("usr");
    user.setPassword("pwd");
    user.setRoles(List.of("reader"));
    assertEquals("usr", user.getUsername());
    assertEquals("pwd", user.getPassword());
    assertEquals(List.of("reader"), user.getRoles());

    RealmConfig realm = new RealmConfig();
    realm.setName("r1");
    realm.setRoles(List.of("a", "b"));
    realm.setClients(List.of(client));
    realm.setUsers(List.of(user));
    assertEquals("r1", realm.getName());
    assertEquals(List.of("a", "b"), realm.getRoles());
    assertEquals(1, realm.getClients().size());
    assertEquals(1, realm.getUsers().size());

    KeycloakConfig config = new KeycloakConfig();
    config.setBaseUrl("http://test:8080");
    config.setRealms(List.of(realm));
    assertEquals("http://test:8080", config.getBaseUrl());
    assertEquals(1, config.getRealms().size());
  }
}

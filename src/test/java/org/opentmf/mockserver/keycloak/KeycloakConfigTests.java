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
    assertEquals("admin@example.com", admin.get().getEmail());
    assertEquals("Admin", admin.get().getFirstName());
    assertEquals("User", admin.get().getLastName());
    assertEquals(3, admin.get().getRoles().size());
    assertTrue(admin.get().getRoles().contains("admin"));
    assertEquals(List.of("admins"), admin.get().getGroups());

    Optional<UserConfig> writer = realm.findUser("writer_usr");
    assertTrue(writer.isPresent());
    assertEquals(2, writer.get().getRoles().size());

    Optional<UserConfig> reader = realm.findUser("reader_usr");
    assertTrue(reader.isPresent());
    assertEquals(1, reader.get().getRoles().size());
    assertTrue(reader.get().getRoles().contains("reader"));
  }

  @Test
  void realm1HasExpectedGroups() {
    RealmConfig realm = KeycloakConfig.getInstance().findRealm("realm1").orElseThrow();
    assertEquals(3, realm.getGroups().size());
    assertTrue(realm.findGroup("admins").isPresent());
    assertTrue(realm.findGroup("developers").isPresent());
    assertTrue(realm.findGroup("viewers").isPresent());

    GroupConfig developers = realm.findGroup("developers").orElseThrow();
    assertEquals(List.of("backend", "frontend"), developers.getSubGroups());
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
            + "  \"groups\":[{\"name\":\"g1\",\"subGroups\":[\"sub1\",\"sub2\"]}],"
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
            + "    \"email\":\"u1@example.com\","
            + "    \"firstName\":\"First\","
            + "    \"lastName\":\"Last\","
            + "    \"roles\":[\"role1\",\"role2\"],"
            + "    \"groups\":[\"g1\"]"
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

    assertEquals(1, realm.getGroups().size());
    GroupConfig group = realm.getGroups().get(0);
    assertEquals("g1", group.getName());
    assertEquals(Arrays.asList("sub1", "sub2"), group.getSubGroups());
    assertTrue(realm.findGroup("g1").isPresent());
    assertFalse(realm.findGroup("nonexistent").isPresent());

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
    assertEquals("u1@example.com", user.getEmail());
    assertEquals("First", user.getFirstName());
    assertEquals("Last", user.getLastName());
    assertEquals(Arrays.asList("role1", "role2"), user.getRoles());
    assertEquals(List.of("g1"), user.getGroups());
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
    user.setEmail("usr@example.com");
    user.setFirstName("First");
    user.setLastName("Last");
    user.setRoles(List.of("reader"));
    user.setGroups(List.of("g1"));
    assertEquals("usr", user.getUsername());
    assertEquals("pwd", user.getPassword());
    assertEquals("usr@example.com", user.getEmail());
    assertEquals("First", user.getFirstName());
    assertEquals("Last", user.getLastName());
    assertEquals(List.of("reader"), user.getRoles());
    assertEquals(List.of("g1"), user.getGroups());

    GroupConfig group = new GroupConfig();
    group.setName("g1");
    group.setSubGroups(List.of("sub1"));
    assertEquals("g1", group.getName());
    assertEquals(List.of("sub1"), group.getSubGroups());

    RealmConfig realm = new RealmConfig();
    realm.setName("r1");
    realm.setRoles(List.of("a", "b"));
    realm.setGroups(List.of(group));
    realm.setClients(List.of(client));
    realm.setUsers(List.of(user));
    assertEquals("r1", realm.getName());
    assertEquals(List.of("a", "b"), realm.getRoles());
    assertEquals(1, realm.getGroups().size());
    assertEquals(1, realm.getClients().size());
    assertEquals(1, realm.getUsers().size());

    KeycloakConfig config = new KeycloakConfig();
    config.setBaseUrl("http://test:8080");
    config.setRealms(List.of(realm));
    assertEquals("http://test:8080", config.getBaseUrl());
    assertEquals(1, config.getRealms().size());
  }
}

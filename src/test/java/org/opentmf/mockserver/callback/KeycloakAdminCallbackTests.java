package org.opentmf.mockserver.callback;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockserver.model.HttpRequest.request;

import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.opentmf.mockserver.util.JacksonUtil;
import tools.jackson.databind.JsonNode;

class KeycloakAdminCallbackTests {

  private final KeycloakAdminCallback callback = new KeycloakAdminCallback();

  private static final String REALM = "realm1";
  private static final String BASE = "/admin/realms/" + REALM;

  // ── Realm ───────────────────────────────────────────────────────────────

  @Test
  void getRealm_returnsRealmRepresentation() {
    HttpResponse resp = callback.handle(adminGet(BASE));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertEquals(REALM, body.get("realm").asText());
    assertTrue(body.get("enabled").asBoolean());
    assertNotNull(body.get("id"));
    assertTrue(body.get("defaultRoles").isArray());
  }

  @Test
  void unknownRealm_returns404() {
    HttpResponse resp = callback.handle(adminGet("/admin/realms/nonexistent"));
    assertEquals(404, resp.getStatusCode());
  }

  // ── Users ─────────────────────────────────────────────────────────────

  @Test
  void listUsers_returnsAllUsers() {
    HttpResponse resp = callback.handle(adminGet(BASE + "/users"));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertTrue(body.isArray());
    assertEquals(3, body.size());

    JsonNode first = body.get(0);
    assertNotNull(first.get("id"));
    assertNotNull(first.get("username"));
    assertTrue(first.get("enabled").asBoolean());
  }

  @Test
  void listUsers_withUsernameFilter() {
    HttpResponse resp = callback.handle(
        adminGet(BASE + "/users").withQueryStringParameter("username", "admin_usr"));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertEquals(1, body.size());
    assertEquals("admin_usr", body.get(0).get("username").asText());
  }

  @Test
  void listUsers_withSearchFilter() {
    HttpResponse resp = callback.handle(
        adminGet(BASE + "/users").withQueryStringParameter("search", "writer"));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertEquals(1, body.size());
    assertEquals("writer_usr", body.get(0).get("username").asText());
  }

  @Test
  void listUsers_withPagination() {
    HttpResponse resp = callback.handle(
        adminGet(BASE + "/users")
            .withQueryStringParameter("first", "1")
            .withQueryStringParameter("max", "1"));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertEquals(1, body.size());
  }

  @Test
  void countUsers_returnsCount() {
    HttpResponse resp = callback.handle(adminGet(BASE + "/users/count"));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertEquals(3, body.asInt());
  }

  @Test
  void getUserById_returnsUser() {
    JsonNode users = parse(callback.handle(adminGet(BASE + "/users")));
    String userId = users.get(0).get("id").asText();

    HttpResponse resp = callback.handle(adminGet(BASE + "/users/" + userId));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertEquals(userId, body.get("id").asText());
    assertNotNull(body.get("username"));
    assertNotNull(body.get("email"));
    assertNotNull(body.get("firstName"));
    assertNotNull(body.get("lastName"));
  }

  @Test
  void getUserById_unknownId_returns404() {
    HttpResponse resp = callback.handle(
        adminGet(BASE + "/users/00000000-0000-0000-0000-000000000000"));
    assertEquals(404, resp.getStatusCode());
  }

  @Test
  void userRealmRoles_returnsRoles() {
    JsonNode users = parse(callback.handle(adminGet(BASE + "/users")));
    String adminUserId = users.get(0).get("id").asText();

    HttpResponse resp = callback.handle(
        adminGet(BASE + "/users/" + adminUserId + "/role-mappings/realm"));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertTrue(body.isArray());
    assertTrue(body.size() > 0);
    assertNotNull(body.get(0).get("name"));
    assertNotNull(body.get(0).get("id"));
  }

  @Test
  void userGroups_returnsGroups() {
    JsonNode users = parse(callback.handle(adminGet(BASE + "/users")));
    String adminUserId = users.get(0).get("id").asText();

    HttpResponse resp = callback.handle(
        adminGet(BASE + "/users/" + adminUserId + "/groups"));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertTrue(body.isArray());
    assertEquals(1, body.size());
    assertEquals("admins", body.get(0).get("name").asText());
  }

  // ── Groups ────────────────────────────────────────────────────────────

  @Test
  void listGroups_returnsAllGroups() {
    HttpResponse resp = callback.handle(adminGet(BASE + "/groups"));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertTrue(body.isArray());
    assertEquals(3, body.size());
    assertNotNull(body.get(0).get("id"));
    assertNotNull(body.get(0).get("name"));
    assertNotNull(body.get(0).get("path"));
    assertNotNull(body.get(0).get("subGroups"));
  }

  @Test
  void listGroups_withSearchFilter() {
    HttpResponse resp = callback.handle(
        adminGet(BASE + "/groups").withQueryStringParameter("search", "dev"));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertEquals(1, body.size());
    assertEquals("developers", body.get(0).get("name").asText());
  }

  @Test
  void countGroups_returnsCount() {
    HttpResponse resp = callback.handle(adminGet(BASE + "/groups/count"));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertEquals(3, body.get("count").asInt());
  }

  @Test
  void getGroupById_returnsGroupWithSubGroups() {
    JsonNode groups = parse(callback.handle(adminGet(BASE + "/groups")));
    String devGroupId = null;
    for (JsonNode g : groups) {
      if ("developers".equals(g.get("name").asText())) {
        devGroupId = g.get("id").asText();
        break;
      }
    }
    assertNotNull(devGroupId);

    HttpResponse resp = callback.handle(adminGet(BASE + "/groups/" + devGroupId));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertEquals("developers", body.get("name").asText());
    assertEquals(2, body.get("subGroups").size());
  }

  @Test
  void getGroupById_unknownId_returns404() {
    HttpResponse resp = callback.handle(
        adminGet(BASE + "/groups/00000000-0000-0000-0000-000000000000"));
    assertEquals(404, resp.getStatusCode());
  }

  @Test
  void groupMembers_returnsUsersInGroup() {
    JsonNode groups = parse(callback.handle(adminGet(BASE + "/groups")));
    String adminsGroupId = null;
    for (JsonNode g : groups) {
      if ("admins".equals(g.get("name").asText())) {
        adminsGroupId = g.get("id").asText();
        break;
      }
    }
    assertNotNull(adminsGroupId);

    HttpResponse resp = callback.handle(
        adminGet(BASE + "/groups/" + adminsGroupId + "/members"));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertTrue(body.isArray());
    assertEquals(1, body.size());
    assertEquals("admin_usr", body.get(0).get("username").asText());
  }

  // ── Roles ─────────────────────────────────────────────────────────────

  @Test
  void listRoles_returnsAllRoles() {
    HttpResponse resp = callback.handle(adminGet(BASE + "/roles"));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertTrue(body.isArray());
    assertEquals(3, body.size());
    assertNotNull(body.get(0).get("id"));
    assertNotNull(body.get(0).get("name"));
    assertFalse(body.get(0).get("clientRole").asBoolean());
  }

  @Test
  void getRoleByName_returnsRole() {
    HttpResponse resp = callback.handle(adminGet(BASE + "/roles/admin"));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertEquals("admin", body.get("name").asText());
    assertNotNull(body.get("id"));
    assertFalse(body.get("clientRole").asBoolean());
  }

  @Test
  void getRoleByName_unknown_returns404() {
    HttpResponse resp = callback.handle(adminGet(BASE + "/roles/nonexistent"));
    assertEquals(404, resp.getStatusCode());
  }

  @Test
  void roleUsers_returnsUsersWithRole() {
    HttpResponse resp = callback.handle(adminGet(BASE + "/roles/admin/users"));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertTrue(body.isArray());
    assertEquals(1, body.size());
    assertEquals("admin_usr", body.get(0).get("username").asText());
  }

  @Test
  void roleUsers_reader_returnsAll() {
    HttpResponse resp = callback.handle(adminGet(BASE + "/roles/reader/users"));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertEquals(3, body.size());
  }

  // ── Clients ───────────────────────────────────────────────────────────

  @Test
  void listClients_returnsAllClients() {
    HttpResponse resp = callback.handle(adminGet(BASE + "/clients"));
    assertEquals(200, resp.getStatusCode());

    JsonNode body = parse(resp);
    assertTrue(body.isArray());
    assertEquals(3, body.size());
    assertNotNull(body.get(0).get("id"));
    assertNotNull(body.get(0).get("clientId"));
    assertEquals("openid-connect", body.get(0).get("protocol").asText());
  }

  // ── Helpers ───────────────────────────────────────────────────────────

  @Test
  void extractRealm_parsesCorrectly() {
    assertEquals("myRealm", KeycloakAdminCallback.extractRealm("/admin/realms/myRealm"));
    assertEquals("myRealm", KeycloakAdminCallback.extractRealm("/admin/realms/myRealm/users"));
    assertEquals("", KeycloakAdminCallback.extractRealm("/other/path"));
  }

  @Test
  void stableIds_areDeterministic() {
    JsonNode users1 = parse(callback.handle(adminGet(BASE + "/users")));
    JsonNode users2 = parse(callback.handle(adminGet(BASE + "/users")));
    assertEquals(users1.get(0).get("id").asText(), users2.get(0).get("id").asText());
  }

  @Test
  void unknownSubEndpoint_returns404() {
    HttpResponse resp = callback.handle(adminGet(BASE + "/unknown"));
    assertEquals(404, resp.getStatusCode());
  }

  private static HttpRequest adminGet(String path) {
    return request().withMethod("GET").withPath(path);
  }

  private static JsonNode parse(HttpResponse response) {
    return JacksonUtil.readAsTree(response.getBodyAsString());
  }
}

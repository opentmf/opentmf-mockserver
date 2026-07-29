package org.opentmf.mockserver.callback;

import static org.opentmf.mockserver.util.JacksonUtil.writeAsString;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.opentmf.mockserver.keycloak.ClientConfig;
import org.opentmf.mockserver.keycloak.GroupConfig;
import org.opentmf.mockserver.keycloak.KeycloakConfig;
import org.opentmf.mockserver.keycloak.RealmConfig;
import org.opentmf.mockserver.keycloak.UserConfig;
import org.opentmf.mockserver.token.TokenEnforcer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keycloak Admin REST API mock callback.
 *
 * <p>Handles read-only {@code GET /admin/realms/{realm}/...} requests, returning
 * Keycloak-compatible representations for users, groups, roles, and the realm itself.
 * Requires a valid Bearer token with the {@code admin} role.
 */
public class KeycloakAdminCallback implements ExpectationResponseCallback {

  private static final Logger LOG = LoggerFactory.getLogger(KeycloakAdminCallback.class);
  private static final long CREATED_TIMESTAMP = 1700000000000L;

  @Override
  public HttpResponse handle(HttpRequest httpRequest) {
    HttpResponse authError = TokenEnforcer.getInstance().validateWithRoles(httpRequest, "admin");
    if (authError != null) {
      return authError;
    }

    String path = httpRequest.getPath().getValue();
    String realmName = extractRealm(path);

    KeycloakConfig config = KeycloakConfig.getInstance();
    Optional<RealmConfig> realmOpt = config.findRealm(realmName);
    if (realmOpt.isEmpty()) {
      LOG.warn("Admin API: unknown realm '{}'", realmName);
      return jsonResponse(404, Map.of("error", "Realm not found"));
    }
    RealmConfig realm = realmOpt.get();

    String subPath = extractSubPath(path, realmName);
    return route(httpRequest, realm, realmName, subPath);
  }

  private HttpResponse route(HttpRequest request, RealmConfig realm, String realmName,
      String subPath) {
    if (subPath.isEmpty()) {
      return handleGetRealm(realm, realmName);
    }

    if (subPath.equals("/users/count")) {
      return handleCountUsers(realm);
    }
    if (subPath.equals("/users")) {
      return handleListUsers(request, realm, realmName);
    }
    if (subPath.startsWith("/users/")) {
      return routeUser(realm, realmName, subPath.substring("/users/".length()));
    }

    if (subPath.equals("/groups/count")) {
      return handleCountGroups(realm);
    }
    if (subPath.equals("/groups")) {
      return handleListGroups(request, realm, realmName);
    }
    if (subPath.startsWith("/groups/")) {
      return routeGroup(realm, realmName, subPath.substring("/groups/".length()));
    }

    if (subPath.equals("/roles")) {
      return handleListRoles(realm, realmName);
    }
    if (subPath.startsWith("/roles/")) {
      return routeRole(realm, realmName, subPath.substring("/roles/".length()));
    }

    if (subPath.equals("/clients")) {
      return handleListClients(realm, realmName);
    }

    return jsonResponse(404, Map.of("error", "Unknown admin endpoint: " + subPath));
  }

  // ── Realm ─────────────────────────────────────────────────────────────────

  private HttpResponse handleGetRealm(RealmConfig realm, String realmName) {
    Map<String, Object> rep = new LinkedHashMap<>();
    rep.put("id", stableId(realmName, "realm"));
    rep.put("realm", realmName);
    rep.put("displayName", realmName);
    rep.put("enabled", true);
    rep.put("registrationAllowed", false);
    rep.put("resetPasswordAllowed", false);
    rep.put("editUsernameAllowed", false);
    rep.put("bruteForceProtected", false);
    rep.put("loginWithEmailAllowed", true);
    rep.put("duplicateEmailsAllowed", false);
    rep.put("sslRequired", "external");
    rep.put("defaultRoles", realm.getRoles());
    return jsonResponse(200, rep);
  }

  // ── Users ─────────────────────────────────────────────────────────────────

  private HttpResponse handleListUsers(HttpRequest request, RealmConfig realm, String realmName) {
    String usernameFilter = queryParam(request, "username");
    String searchFilter = queryParam(request, "search");
    int first = queryParamInt(request, "first", 0);
    int max = queryParamInt(request, "max", 100);

    List<Map<String, Object>> results = realm.getUsers().stream()
        .filter(u -> usernameFilter == null || u.getUsername().equals(usernameFilter))
        .filter(u -> searchFilter == null
            || u.getUsername().toLowerCase().contains(searchFilter.toLowerCase())
            || (u.getEmail() != null
                && u.getEmail().toLowerCase().contains(searchFilter.toLowerCase()))
            || (u.getFirstName() != null
                && u.getFirstName().toLowerCase().contains(searchFilter.toLowerCase()))
            || (u.getLastName() != null
                && u.getLastName().toLowerCase().contains(searchFilter.toLowerCase())))
        .skip(first)
        .limit(max)
        .map(u -> userRepresentation(u, realmName))
        .collect(Collectors.toList());
    return jsonResponse(200, results);
  }

  private HttpResponse handleCountUsers(RealmConfig realm) {
    return jsonResponse(200, realm.getUsers().size());
  }

  private HttpResponse routeUser(RealmConfig realm, String realmName, String rest) {
    int slash = rest.indexOf('/');
    String userId = slash < 0 ? rest : rest.substring(0, slash);
    String tail = slash < 0 ? "" : rest.substring(slash);

    Optional<UserConfig> userOpt = findUserById(realm, realmName, userId);
    if (userOpt.isEmpty()) {
      return jsonResponse(404, Map.of("error", "User not found"));
    }
    UserConfig user = userOpt.get();

    if (tail.isEmpty()) {
      return jsonResponse(200, userRepresentation(user, realmName));
    }
    if (tail.equals("/role-mappings/realm")) {
      return handleUserRealmRoles(user, realmName);
    }
    if (tail.equals("/groups")) {
      return handleUserGroups(user, realmName);
    }
    return jsonResponse(404, Map.of("error", "Unknown user sub-resource: " + tail));
  }

  private HttpResponse handleUserRealmRoles(UserConfig user, String realmName) {
    List<Map<String, Object>> roles = user.getRoles().stream()
        .map(r -> roleRepresentation(r, realmName))
        .collect(Collectors.toList());
    return jsonResponse(200, roles);
  }

  private HttpResponse handleUserGroups(UserConfig user, String realmName) {
    List<Map<String, Object>> groups = user.getGroups().stream()
        .map(g -> {
          Map<String, Object> rep = new LinkedHashMap<>();
          rep.put("id", stableId(realmName, "group:" + g));
          rep.put("name", g);
          rep.put("path", "/" + g);
          return rep;
        })
        .collect(Collectors.toList());
    return jsonResponse(200, groups);
  }

  // ── Groups ────────────────────────────────────────────────────────────────

  private HttpResponse handleListGroups(HttpRequest request, RealmConfig realm, String realmName) {
    String searchFilter = queryParam(request, "search");
    int first = queryParamInt(request, "first", 0);
    int max = queryParamInt(request, "max", 100);

    List<Map<String, Object>> results = realm.getGroups().stream()
        .filter(g -> searchFilter == null
            || g.getName().toLowerCase().contains(searchFilter.toLowerCase()))
        .skip(first)
        .limit(max)
        .map(g -> groupRepresentation(g, realmName))
        .collect(Collectors.toList());
    return jsonResponse(200, results);
  }

  private HttpResponse handleCountGroups(RealmConfig realm) {
    return jsonResponse(200, Map.of("count", realm.getGroups().size()));
  }

  private HttpResponse routeGroup(RealmConfig realm, String realmName, String rest) {
    int slash = rest.indexOf('/');
    String groupId = slash < 0 ? rest : rest.substring(0, slash);
    String tail = slash < 0 ? "" : rest.substring(slash);

    Optional<GroupConfig> groupOpt = findGroupById(realm, realmName, groupId);
    if (groupOpt.isEmpty()) {
      return jsonResponse(404, Map.of("error", "Group not found"));
    }
    GroupConfig group = groupOpt.get();

    if (tail.isEmpty()) {
      return jsonResponse(200, groupRepresentation(group, realmName));
    }
    if (tail.equals("/members")) {
      return handleGroupMembers(realm, group, realmName);
    }
    return jsonResponse(404, Map.of("error", "Unknown group sub-resource: " + tail));
  }

  private HttpResponse handleGroupMembers(RealmConfig realm, GroupConfig group, String realmName) {
    List<Map<String, Object>> members = realm.getUsers().stream()
        .filter(u -> u.getGroups().contains(group.getName()))
        .map(u -> userRepresentation(u, realmName))
        .collect(Collectors.toList());
    return jsonResponse(200, members);
  }

  // ── Roles ─────────────────────────────────────────────────────────────────

  private HttpResponse handleListRoles(RealmConfig realm, String realmName) {
    List<Map<String, Object>> roles = realm.getRoles().stream()
        .map(r -> roleRepresentation(r, realmName))
        .collect(Collectors.toList());
    return jsonResponse(200, roles);
  }

  private HttpResponse routeRole(RealmConfig realm, String realmName, String rest) {
    int slash = rest.indexOf('/');
    String roleName = slash < 0 ? rest : rest.substring(0, slash);
    String tail = slash < 0 ? "" : rest.substring(slash);

    if (!realm.getRoles().contains(roleName)) {
      return jsonResponse(404, Map.of("error", "Role not found: " + roleName));
    }

    if (tail.isEmpty()) {
      return jsonResponse(200, roleRepresentation(roleName, realmName));
    }
    if (tail.equals("/users")) {
      return handleRoleUsers(realm, roleName, realmName);
    }
    return jsonResponse(404, Map.of("error", "Unknown role sub-resource: " + tail));
  }

  private HttpResponse handleRoleUsers(RealmConfig realm, String roleName, String realmName) {
    List<Map<String, Object>> users = realm.getUsers().stream()
        .filter(u -> u.getRoles().contains(roleName))
        .map(u -> userRepresentation(u, realmName))
        .collect(Collectors.toList());
    return jsonResponse(200, users);
  }

  // ── Clients ───────────────────────────────────────────────────────────────

  private HttpResponse handleListClients(RealmConfig realm, String realmName) {
    List<Map<String, Object>> clients = realm.getClients().stream()
        .map(c -> clientRepresentation(c, realmName))
        .collect(Collectors.toList());
    return jsonResponse(200, clients);
  }

  // ── Representations ───────────────────────────────────────────────────────

  private Map<String, Object> userRepresentation(UserConfig user, String realmName) {
    Map<String, Object> rep = new LinkedHashMap<>();
    rep.put("id", stableId(realmName, "user:" + user.getUsername()));
    rep.put("username", user.getUsername());
    rep.put("email", user.getEmail() != null ? user.getEmail() : "");
    rep.put("firstName", user.getFirstName() != null ? user.getFirstName() : "");
    rep.put("lastName", user.getLastName() != null ? user.getLastName() : "");
    rep.put("enabled", true);
    rep.put("emailVerified", true);
    rep.put("createdTimestamp", CREATED_TIMESTAMP);
    rep.put("totp", false);
    rep.put("disableableCredentialTypes", List.of());
    rep.put("requiredActions", List.of());
    rep.put("notBefore", 0);
    Map<String, Boolean> access = new LinkedHashMap<>();
    access.put("manageGroupMembership", true);
    access.put("view", true);
    access.put("mapRoles", true);
    access.put("impersonate", true);
    access.put("manage", true);
    rep.put("access", access);
    return rep;
  }

  private Map<String, Object> roleRepresentation(String roleName, String realmName) {
    Map<String, Object> rep = new LinkedHashMap<>();
    rep.put("id", stableId(realmName, "role:" + roleName));
    rep.put("name", roleName);
    rep.put("description", "");
    rep.put("composite", false);
    rep.put("clientRole", false);
    rep.put("containerId", stableId(realmName, "realm"));
    return rep;
  }

  private Map<String, Object> groupRepresentation(GroupConfig group, String realmName) {
    Map<String, Object> rep = new LinkedHashMap<>();
    rep.put("id", stableId(realmName, "group:" + group.getName()));
    rep.put("name", group.getName());
    rep.put("path", "/" + group.getName());

    List<Map<String, Object>> subGroups = new ArrayList<>();
    for (String sub : group.getSubGroups()) {
      Map<String, Object> subRep = new LinkedHashMap<>();
      subRep.put("id", stableId(realmName, "group:" + group.getName() + "/" + sub));
      subRep.put("name", sub);
      subRep.put("path", "/" + group.getName() + "/" + sub);
      subRep.put("subGroups", List.of());
      subGroups.add(subRep);
    }
    rep.put("subGroups", subGroups);
    rep.put("subGroupCount", (long) subGroups.size());
    return rep;
  }

  private Map<String, Object> clientRepresentation(ClientConfig client, String realmName) {
    Map<String, Object> rep = new LinkedHashMap<>();
    rep.put("id", stableId(realmName, "client:" + client.getClientId()));
    rep.put("clientId", client.getClientId());
    rep.put("enabled", true);
    rep.put("publicClient", client.isPublicClient());
    rep.put("protocol", "openid-connect");
    rep.put("bearerOnly", false);
    rep.put("directAccessGrantsEnabled",
        client.getAllowedGrantTypes().contains("password"));
    rep.put("serviceAccountsEnabled",
        client.getAllowedGrantTypes().contains("client_credentials"));
    return rep;
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  static String extractRealm(String path) {
    String prefix = "/admin/realms/";
    int start = path.indexOf(prefix);
    if (start < 0) {
      return "";
    }
    int nameStart = start + prefix.length();
    int nameEnd = path.indexOf('/', nameStart);
    return nameEnd < 0 ? path.substring(nameStart) : path.substring(nameStart, nameEnd);
  }

  private static String extractSubPath(String path, String realmName) {
    String realmPrefix = "/admin/realms/" + realmName;
    int idx = path.indexOf(realmPrefix);
    if (idx < 0) {
      return "";
    }
    return path.substring(idx + realmPrefix.length());
  }

  private Optional<UserConfig> findUserById(RealmConfig realm, String realmName, String userId) {
    return realm.getUsers().stream()
        .filter(u -> stableId(realmName, "user:" + u.getUsername()).equals(userId))
        .findFirst();
  }

  private Optional<GroupConfig> findGroupById(RealmConfig realm, String realmName, String groupId) {
    return realm.getGroups().stream()
        .filter(g -> stableId(realmName, "group:" + g.getName()).equals(groupId))
        .findFirst();
  }

  private static String stableId(String realmName, String entity) {
    return UUID.nameUUIDFromBytes(
        (realmName + ":" + entity).getBytes(StandardCharsets.UTF_8)).toString();
  }

  private static String queryParam(HttpRequest request, String name) {
    String value = request.getFirstQueryStringParameter(name);
    return (value != null && !value.isEmpty()) ? value : null;
  }

  private static int queryParamInt(HttpRequest request, String name, int defaultValue) {
    String val = queryParam(request, name);
    if (val == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(val);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static HttpResponse jsonResponse(int status, Object body) {
    return HttpResponse.response()
        .withStatusCode(status)
        .withContentType(MediaType.APPLICATION_JSON)
        .withBody(writeAsString(body));
  }
}

# opentmf-mockserver

General-purpose, [TMF-630](https://www.tmforum.org/resources/specification/tmf630-rest-api-design-guidelines-4-2-0/)
-compatible dynamic mock server built on top of [MockServer](https://github.com/mock-server/mockserver). Ships with *
*out-of-the-box Keycloak-like OIDC support** -- realms, clients, users, roles, real signed JWTs, JWKS, and token
enforcement are all included without any external dependencies.

> **MockServer feature matrix** -- This project embeds a subset of MockServer. For a detailed
> breakdown of what is included and what is intentionally excluded (OpenAPI, XML matching,
> dashboard, proxy, templates, etc.), see [MOCKSERVER.md](MOCKSERVER.md).

<!-- TOC -->

* [opentmf-mockserver](#opentmf-mockserver)
    * [Features](#features)
    * [Quick Start](#quick-start)
        * [Docker](#docker)
        * [Standalone](#standalone)
    * [Keycloak Mock](#keycloak-mock)
        * [What You Get for Free](#what-you-get-for-free)
        * [Default Configuration](#default-configuration)
        * [Obtaining Tokens](#obtaining-tokens)
        * [OIDC Discovery and JWKS](#oidc-discovery-and-jwks)
        * [Custom Keycloak Configuration](#custom-keycloak-configuration)
    * [Keycloak Admin REST API](#keycloak-admin-rest-api)
        * [Admin Endpoints](#admin-endpoints)
        * [Admin API Examples](#admin-api-examples)
    * [Token Enforcement and Role-Based Access](#token-enforcement-and-role-based-access)
        * [Validating Against an External Keycloak](#validating-against-an-external-keycloak)
        * [Role Matrix](#role-matrix)
    * [Dynamic Callbacks](#dynamic-callbacks)
        * [POST (DynamicPostCallback)](#post-dynamicpostcallback)
        * [GET by ID (DynamicGetCallback)](#get-by-id-dynamicgetcallback)
        * [GET List (DynamicGetListCallback)](#get-list-dynamicgetlistcallback)
        * [JSON Patch (DynamicJsonPatchCallback)](#json-patch-dynamicjsonpatchcallback)
        * [Merge Patch (DynamicMergePatchCallback)](#merge-patch-dynamicmergepatchcallback)
        * [DELETE (DynamicDeleteCallback)](#delete-dynamicdeletecallback)
    * [Create Expectations](#create-expectations)
    * [Environment Variables](#environment-variables)
    * [Content-Range Calculations](#content-range-calculations)
    * [API Reference](#api-reference)
    * [MockServer Feature Matrix](#mockserver-feature-matrix)
    * [Acknowledgments](#acknowledgments)
    * [Release Notes](#release-notes)

<!-- TOC -->

## Features

- **Zero-configuration TMF mocking** -- POST, GET, GET List, JSON Patch, Merge Patch, and DELETE with automatic `id`,
  `href`, state transitions, audit fields, and paging.
- **Built-in Keycloak mock** -- real RSA-signed JWTs, OIDC discovery, JWKS endpoint, Admin REST API, configurable realms
  / clients / users / roles / groups. No real Keycloak needed.
- **Token enforcement** -- optionally validate Bearer tokens on every request, with role-based access control (reader /
  writer / admin).
- **External IdP support** -- validate tokens against a real Keycloak (or any OIDC provider) via `JWKS_URI` or
  `TOKEN_ISSUER` auto-discovery.
- **TMF-630 compliant** -- Content-Range, X-Total-Count, X-Result-Count headers, status-field lifecycle, versioned
  entities, query parameters, jsonPath filters, `fields=` parameter.

## Quick Start

### Docker

```shell
# Build
mvn -P docker clean package

# Run
docker run -p 1080:1080 local/opentmf-mockserver:2.1.0-SNAPSHOT
```

The server starts on port 1080. Keycloak endpoints and JWKS are available immediately -- no extra setup needed.

### Standalone

```shell
# Build (produces target/opentmf-mockserver-*.jar + target/libs/)
mvn clean package

# Start
java -Dmockserver.initializationClass=org.opentmf.mockserver.callback.JwksExpectationInitializer \
  -cp target/opentmf-mockserver-*.jar:target/libs/* \
  org.mockserver.cli.Main -serverPort 1080
```

## Keycloak Mock

### What You Get for Free

On startup, the following endpoints are automatically registered for each configured realm (no expectations to create):

| Endpoint                                               | Description                                        |
|--------------------------------------------------------|----------------------------------------------------|
| `GET /realms/{realm}/protocol/openid-connect/certs`    | JWKS (public keys for token verification)          |
| `GET /realms/{realm}/.well-known/openid-configuration` | OIDC discovery document                            |
| `POST /realms/{realm}/protocol/openid-connect/token`   | Token endpoint (issue JWTs)                        |
| `GET /admin/realms/{realm}/...`                        | Keycloak Admin REST API (users, groups, roles, ...) |
| `GET /.well-known/jwks.json`                           | Global JWKS (backward-compatible)                  |
| `GET /mockserver/openapi`                              | OpenAPI 3.1 specification (YAML)                   |

All issued tokens are **real, parsable, RSA-signed JWTs** with Keycloak-compatible claims (`iss`, `sub`, `azp`,
`realm_access`, `resource_access`, `preferred_username`, `exp`, etc.).

### Default Configuration

The built-in default configuration provides a ready-to-use setup:

**Realm:** `realm1`

**Clients:**

| Client ID  | Type         | Secret          | Allowed Grants              |
|------------|--------------|-----------------|-----------------------------|
| `client1`  | Confidential | `client1Secret` | `client_credentials`        |
| `client2`  | Confidential | `client2Secret` | `password`                  |
| `uiClient` | Public       | --              | `password`, `refresh_token` |

**Groups:** `admins`, `developers` (sub-groups: `backend`, `frontend`), `viewers`

**Users:**

| Username     | Password     | Roles                       | Groups        |
|--------------|--------------|-----------------------------|---------------|
| `admin_usr`  | `admin_pwd`  | `admin`, `writer`, `reader` | `admins`      |
| `writer_usr` | `writer_pwd` | `writer`, `reader`          | `developers`  |
| `reader_usr` | `reader_pwd` | `reader`                    | `viewers`     |

### Obtaining Tokens

```shell
# client_credentials (service account)
curl -s -X POST 'http://localhost:1080/realms/realm1/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=client_credentials&client_id=client1&client_secret=client1Secret'

# password grant (user login)
curl -s -X POST 'http://localhost:1080/realms/realm1/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=password&client_id=uiClient&username=admin_usr&password=admin_pwd'

# refresh_token grant
curl -s -X POST 'http://localhost:1080/realms/realm1/protocol/openid-connect/token' \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'grant_type=refresh_token&client_id=uiClient&refresh_token=<refresh_token_from_above>'
```

The response follows the standard OAuth 2.0 token response format:

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6...",
  "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6...",
  "scope": "openid profile email"
}
```

### OIDC Discovery and JWKS

```shell
# Discovery document
curl -s http://localhost:1080/realms/realm1/.well-known/openid-configuration | jq .

# JWKS (public keys)
curl -s http://localhost:1080/realms/realm1/protocol/openid-connect/certs | jq .
```

### Custom Keycloak Configuration

Override the default configuration by providing a JSON file:

```shell
# Docker
docker run -p 1080:1080 \
  -v /path/to/my-keycloak-config.json:/config/keycloak-mock.json \
  local/opentmf-mockserver:2.1.0-SNAPSHOT

# Or via environment variable
docker run -p 1080:1080 \
  -e KEYCLOAK_CONFIG=/config/my-config.json \
  -v /path/to/my-config.json:/config/my-config.json \
  local/opentmf-mockserver:2.1.0-SNAPSHOT
```

The JSON format:

```json
{
  "baseUrl": "http://localhost:1080",
  "realms": [
    {
      "name": "my-realm",
      "roles": ["admin", "user"],
      "groups": [
        { "name": "team-a", "subGroups": ["backend", "frontend"] },
        { "name": "team-b", "subGroups": [] }
      ],
      "clients": [
        {
          "clientId": "my-app",
          "clientSecret": "secret",
          "publicClient": false,
          "allowedGrantTypes": ["client_credentials", "password"],
          "serviceAccountRoles": ["admin"]
        },
        {
          "clientId": "my-spa",
          "publicClient": true,
          "allowedGrantTypes": ["password", "refresh_token"]
        }
      ],
      "users": [
        {
          "username": "alice",
          "password": "alice123",
          "email": "alice@example.com",
          "firstName": "Alice",
          "lastName": "Smith",
          "roles": ["admin", "user"],
          "groups": ["team-a"]
        }
      ]
    }
  ]
}
```

## Keycloak Admin REST API

A read-only subset of the [Keycloak Admin REST API](https://www.keycloak.org/docs-api/latest/rest-api/index.html) is
automatically registered for every configured realm. When token enforcement is enabled (`ENFORCE_TOKEN=true`), a valid
Bearer token with the `admin` role is required.

### Admin Endpoints

| Endpoint                                                   | Description                            |
|------------------------------------------------------------|----------------------------------------|
| `GET /admin/realms/{realm}`                                | Realm representation                   |
| `GET /admin/realms/{realm}/users`                          | List users (supports `username`, `search`, `first`, `max`) |
| `GET /admin/realms/{realm}/users/count`                    | User count                             |
| `GET /admin/realms/{realm}/users/{id}`                     | Single user by ID                      |
| `GET /admin/realms/{realm}/users/{id}/role-mappings/realm` | Realm roles for a user                 |
| `GET /admin/realms/{realm}/users/{id}/groups`              | Groups a user belongs to               |
| `GET /admin/realms/{realm}/groups`                         | List groups (supports `search`, `first`, `max`) |
| `GET /admin/realms/{realm}/groups/count`                   | Group count                            |
| `GET /admin/realms/{realm}/groups/{id}`                    | Single group by ID (includes sub-groups) |
| `GET /admin/realms/{realm}/groups/{id}/members`            | Members of a group                     |
| `GET /admin/realms/{realm}/roles`                          | List realm roles                       |
| `GET /admin/realms/{realm}/roles/{name}`                   | Single role by name                    |
| `GET /admin/realms/{realm}/roles/{name}/users`             | Users with a specific role             |
| `GET /admin/realms/{realm}/clients`                        | List clients                           |

All IDs are deterministic UUIDs derived from the realm and entity name, so they remain stable across restarts.

### Admin API Examples

```shell
# Get a token with admin role
TOKEN=$(curl -s -X POST 'http://localhost:1080/realms/realm1/protocol/openid-connect/token' \
  -d 'grant_type=client_credentials&client_id=client1&client_secret=client1Secret' | jq -r .access_token)

# List all users
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:1080/admin/realms/realm1/users | jq .

# Search users
curl -s -H "Authorization: Bearer $TOKEN" 'http://localhost:1080/admin/realms/realm1/users?search=admin' | jq .

# List groups
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:1080/admin/realms/realm1/groups | jq .

# List realm roles
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:1080/admin/realms/realm1/roles | jq .

# Get users with a specific role
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:1080/admin/realms/realm1/roles/admin/users | jq .
```

## Token Enforcement and Role-Based Access

Enable token validation on all dynamic callbacks with a single environment variable:

```shell
docker run -p 1080:1080 -e ENFORCE_TOKEN=true local/opentmf-mockserver:2.1.0-SNAPSHOT
```

When enabled, every request to a dynamic callback must include a valid `Authorization: Bearer <token>` header. The
token's signature, expiration, and (optionally) issuer are verified. In addition, the token's roles are checked against
the operation being performed.

### Validating Against an External Keycloak

You can point the enforcer at a real Keycloak (or any OIDC provider) instead of the built-in mock keys:

```shell
# Option 1: Explicit JWKS URI (takes precedence)
docker run -p 1080:1080 \
  -e ENFORCE_TOKEN=true \
  -e JWKS_URI=https://keycloak.example.com/realms/myrealm/protocol/openid-connect/certs \
  -e TOKEN_ISSUER=https://keycloak.example.com/realms/myrealm \
  local/opentmf-mockserver:2.1.0-SNAPSHOT

# Option 2: OIDC auto-discovery (JWKS URI is resolved from the issuer's discovery endpoint)
docker run -p 1080:1080 \
  -e ENFORCE_TOKEN=true \
  -e TOKEN_ISSUER=https://keycloak.example.com/realms/myrealm \
  local/opentmf-mockserver:2.1.0-SNAPSHOT
```

When `TOKEN_ISSUER` is set, the `iss` claim in the token is also validated against it.

### Role Matrix

Roles are extracted from the JWT's `realm_access.roles` claim (Keycloak standard).

| Operation                     | Required Role (any of)      |
|-------------------------------|-----------------------------|
| GET, GET List                 | `reader`, `writer`, `admin` |
| POST, JSON Patch, Merge Patch | `writer`, `admin`           |
| DELETE                        | `admin`                     |

Insufficient roles return **403 Forbidden**; missing/invalid tokens return **401 Unauthorized**.

## Dynamic Callbacks

All callbacks share these behaviors:

- Payloads are cached in-memory with a configurable TTL (default: 2 hours).
- GET, POST, and PATCH operations reset the eviction timer.
- `id`, `href`, `createdDate`, `createdBy`, `updatedDate`, `updatedBy`, `revision`, and state fields are managed
  automatically.
- Versioned entities are supported via `:(version=XYZ)` in the path or `?version=XYZ` query parameter.

### POST (DynamicPostCallback)

- Generates a UUID `id` if not provided; returns 400 if the ID already exists.
- Sets `createdDate`, `createdBy`, `revision`, `href`, and the initial state field.
- Sets `version="0"` for versioned entities if not provided.
- Returns **201 Created**.

State field mapping by path:

| Type      | Field             | Initial        | Final       |
|-----------|-------------------|----------------|-------------|
| Orders    | `state`           | `acknowledged` | `completed` |
| Inventory | `status`          | `created`      | `active`    |
| Catalog   | `lifecycleStatus` | `inStudy`      | `inDesign`  |
| Candidate | `lifecycleStatus` | `inStudy`      | `inDesign`  |
| Default   | `state`           | `acknowledged` | `completed` |

### GET by ID (DynamicGetCallback)

- Returns the cached payload for the given ID (404 if not found).
- On first GET, transitions the state field from initial to final value and sets `updatedDate`, `updatedBy`, `revision`.
- Returns **200 OK**.

### GET List (DynamicGetListCallback)

- Returns all cached payloads for the domain, filtered/sorted/paged per TMF-630.
- Supports query parameters: `offset`, `limit`, `sort`, `fields`, and attribute-based filtering.
- Sets `X-Total-Count`, `X-Result-Count`, and `Content-Range` headers.
- Returns **200 OK** or **416 Range Not Satisfiable** if offset exceeds total count.

### JSON Patch (DynamicJsonPatchCallback)

- Applies an [RFC 6902](https://datatracker.ietf.org/doc/html/rfc6902) JSON Patch to the cached payload.
- Sets `updatedDate`, `updatedBy` and increments `revision`.
- Returns **200 OK** (or 404/400 on error).

### Merge Patch (DynamicMergePatchCallback)

- Applies an [RFC 7396](https://datatracker.ietf.org/doc/html/rfc7396) JSON Merge Patch to the cached payload.
- Sets `updatedDate`, `updatedBy` and increments `revision`.
- Returns **200 OK** (or 404/400 on error).

### DELETE (DynamicDeleteCallback)

- Removes the cached payload for the given ID (404 if not found).
- Returns **204 No Content**.

## Create Expectations

Token and OIDC endpoints are auto-registered. For TMF resource endpoints, register expectations as needed:

```shell
# POST
curl -s -X PUT http://localhost:1080/mockserver/expectation -H "Content-Type: application/json" -d '{
  "httpRequest": { "method": "POST", "path": "/tmf-api/serviceOrdering/v4/serviceOrder" },
  "httpResponseClassCallback": { "callbackClass": "org.opentmf.mockserver.callback.DynamicPostCallback" }
}'

# GET by ID
curl -s -X PUT http://localhost:1080/mockserver/expectation -H "Content-Type: application/json" -d '{
  "httpRequest": { "method": "GET", "path": "/tmf-api/serviceOrdering/v4/serviceOrder/.*" },
  "httpResponseClassCallback": { "callbackClass": "org.opentmf.mockserver.callback.DynamicGetCallback" }
}'

# GET List
curl -s -X PUT http://localhost:1080/mockserver/expectation -H "Content-Type: application/json" -d '{
  "httpRequest": { "method": "GET", "path": "/tmf-api/serviceOrdering/v4/serviceOrder.*" },
  "httpResponseClassCallback": { "callbackClass": "org.opentmf.mockserver.callback.DynamicGetListCallback" }
}'

# JSON Patch
curl -s -X PUT http://localhost:1080/mockserver/expectation -H "Content-Type: application/json" -d '{
  "httpRequest": { "method": "PATCH", "path": "/tmf-api/serviceOrdering/v4/serviceOrder/.*",
                   "headers": { "Content-Type": ["application/json-patch+json"] } },
  "httpResponseClassCallback": { "callbackClass": "org.opentmf.mockserver.callback.DynamicJsonPatchCallback" }
}'

# Merge Patch
curl -s -X PUT http://localhost:1080/mockserver/expectation -H "Content-Type: application/json" -d '{
  "httpRequest": { "method": "PATCH", "path": "/tmf-api/serviceOrdering/v4/serviceOrder/.*",
                   "headers": { "Content-Type": ["application/merge-patch+json"] } },
  "httpResponseClassCallback": { "callbackClass": "org.opentmf.mockserver.callback.DynamicMergePatchCallback" }
}'

# DELETE
curl -s -X PUT http://localhost:1080/mockserver/expectation -H "Content-Type: application/json" -d '{
  "httpRequest": { "method": "DELETE", "path": "/tmf-api/serviceOrdering/v4/serviceOrder/.*" },
  "httpResponseClassCallback": { "callbackClass": "org.opentmf.mockserver.callback.DynamicDeleteCallback" }
}'
```

## Environment Variables

| Variable                    | Default                      | Description                                                       |
|-----------------------------|------------------------------|-------------------------------------------------------------------|
| `SERVER_PORT`               | `1080`                       | MockServer listen port                                            |
| `CACHE_DURATION_MILLIS`     | `7200000` (2h)               | Payload cache TTL in milliseconds                                 |
| `ADDITIONAL_FIELDS`         | --                           | Comma-separated `key` or `key=value` pairs added to POST payloads |
| `CONTENT_RANGE_OFFSET_BASE` | `1`                          | `0` or `1` -- base for Content-Range offset calculation           |
| `KEYCLOAK_CONFIG`           | `/config/keycloak-mock.json` | Path to Keycloak mock configuration JSON                          |
| `ENFORCE_TOKEN`             | `false`                      | `true` to require valid Bearer JWT on all dynamic callbacks       |
| `TOKEN_ISSUER`              | --                           | Expected `iss` claim; also enables OIDC auto-discovery            |
| `JWKS_URI`                  | --                           | Explicit JWKS endpoint URL (takes precedence over discovery)      |

## Content-Range Calculations

The `Content-Range` header follows the TMF-630 REST API Design Guidelines:

`Content-Range: items <start>-<end>/<total>`

Given 23 items in the domain (1-based offset, the default):

| Offset | Limit | Status | Content-Range    |
|:------:|:-----:|:------:|------------------|
|   0    |  10   |  200   | `items 1-10/23`  |
|   10   |  10   |  200   | `items 11-20/23` |
|   20   |  10   |  200   | `items 21-23/23` |
|   30   |  10   |  416   | `items */23`     |

Set `CONTENT_RANGE_OFFSET_BASE=0` for zero-based offset values. Default offset is 0, default limit is 10.

## API Reference

The full API is documented in [opentmf-mockserver-openapi.yaml](opentmf-mockserver-openapi.yaml) (OpenAPI 3.1). It
covers all three
endpoint groups: the MockServer control plane, the Keycloak OIDC mock, and the dynamic TMF
resource callbacks.

The OpenAPI spec is also served at runtime:

```
GET /mockserver/openapi
```

You can point any OpenAPI viewer (e.g. [Swagger Editor](https://editor.swagger.io),
VS Code OpenAPI extension) directly at `http://localhost:1080/mockserver/openapi`.

## MockServer Feature Matrix

This project embeds a tailored subset of MockServer. For a detailed breakdown of included
features (HTTP mocking, JSON matching, TLS, callbacks, forwarding) and excluded features
(OpenAPI, XML/XPath matching, dashboard UI, SOCKS proxy, template engines, Prometheus), see
[MOCKSERVER.md](MOCKSERVER.md).

## Acknowledgments

This project includes source code from [MockServer](https://github.com/mock-server/mockserver)
(v5.15.0) by James D Bloom, licensed under the
[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0). The `org.mockserver` package
contains code originally from the `mockserver-core`, `mockserver-netty`, and
`mockserver-client-java` modules of that project. The code has been modified to migrate from
Jackson 2.x to Jackson 3.x and to remove features not needed for TMF mock usage (UI dashboard,
proxy/SOCKS, template engines, XML/XPath body matching, OpenAPI/Swagger, and Prometheus metrics).

## Release Notes

See [CHANGELOG.md](CHANGELOG.md) for a detailed list of changes per version.

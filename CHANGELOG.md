# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0] - 2026-03-21

### Added
- **Real JWT token generation**: `KeycloakTokenCallback` produces real, parsable, RSA-signed JWTs.
- **JWKS endpoint**: `GET /.well-known/jwks.json` is served automatically at startup via
  `JwksExpectationInitializer`, allowing clients to validate issued tokens.
- **Keycloak mock**: Configurable multi-realm Keycloak simulation with support for
  `client_credentials`, `password`, and `refresh_token` grant types. Realm-specific endpoints:
  - `GET /realms/{realm}/protocol/openid-connect/certs` (JWKS)
  - `GET /realms/{realm}/.well-known/openid-configuration` (OIDC discovery)
  - `POST /realms/{realm}/protocol/openid-connect/token` (token endpoint)
- **Keycloak configuration**: Realms, clients (public/confidential), users, roles, and grant types
  are configurable via a JSON file (`KEYCLOAK_CONFIG` env var or
  `keycloak.config.path` system property). Ships with a sensible default configuration.
- **Token enforcement**: All dynamic callbacks (`Post`, `Get`, `GetList`, `JsonPatch`,
  `MergePatch`, `Delete`) can enforce Bearer JWT validation via the `ENFORCE_TOKEN` environment
  variable. Supports validation against:
  - Built-in mock JWKS keys
  - An external JWKS URI (`JWKS_URI` env var)
  - OIDC auto-discovery via issuer (`TOKEN_ISSUER` env var)
- **Issuer validation**: When `TOKEN_ISSUER` is set, the `iss` claim is validated in addition to
  the signature.
- **Role-based authorization**: When `ENFORCE_TOKEN=true`, each dynamic callback enforces
  role requirements from the JWT's `realm_access.roles` claim:
  - GET / GET List: requires `reader`, `writer`, or `admin`
  - POST / PATCH: requires `writer` or `admin`
  - DELETE: requires `admin`
- **Integration tests**: Keycloak Testcontainers integration test (`KeycloakIntegrationIT`) runs
  during `mvn integration-test` / `mvn verify` via the Maven Failsafe plugin, validating real
  Keycloak tokens against `TokenEnforcer`.

### Removed
- `OpenidTokenCallback`, `OpenidTokenGenerator`, `TokenGenerator`, and `TokenException` -- superseded
  by `KeycloakTokenCallback` which provides Keycloak-compatible token issuance with strict validation.

### Fixed
- `DynamicPostCallback` now returns HTTP 201 Created instead of 200 OK.
- `DynamicDeleteCallback` now retrieves version from payload when needed for versioned entities.
- `DynamicGetListCallback` `compare()` handles null values without throwing.
- `DynamicGetListCallback` sort extraction now uses `LinkedHashSet` to preserve sort order.
- `DynamicJsonPatchCallback` and `DynamicMergePatchCallback` now call `setUpdateFields()` after
  patching to correctly set `updatedDate`, `updatedBy`, and `revision`.
- `PayloadCache` is now thread-safe (singleton pattern, synchronized access, bounds-checked
  `allOf()`), with correct TTL eviction and `touch()` behavior.
- Removed unused `PayloadCache.clear(String, Id)` method.

## [1.1.1] - 2025-11-27

### Fixed
- Started returning 416 Range Not Satisfiable when `offset > 0` and `offset >= totalCount`.
- Started supporting `CONTENT_RANGE_OFFSET_BASE` environment variable (accepts 0 or 1, default 1).
- Started setting `X-Result-Count` header on `getList`.

## [1.1.0] - 2025-11-07

### Fixed
- Fixes the Docker image again. 1.0.8 and 1.0.9 did not behave as expected.

## [1.0.9] - 2025-11-07

### Fixed
- Fixes the Docker image.

## [1.0.8] - 2025-11-07

### Changed
- Applies query parameters filter to the cached domain payloads in GET LIST.

## [1.0.7] - 2025-09-28

### Fixed
- `RequestContext` initialization is performed on the decoded URL string.

## [1.0.6] - 2025-05-29

### Added
- Version resolution from query parameters (`?version=XYZ`).
- `ADDITIONAL_FIELDS` environment variable support.
- `CACHE_DURATION_MILLIS` environment variable support.

## [1.0.5] - 2025-05-25

### Fixed
- Fixed the target JAR file path in the release Dockerfile.

## [1.0.4] - 2025-05-22

### Added
- Introduced a release Dockerfile.

## [1.0.3] - 2025-05-22

### Changed
- Enhanced the version resolving algorithm.

## [1.0.2] - 2025-04-11

### Changed
- First open-source version.

## [1.0.1]

### Added
- Support for versioned entities (TMF-630 Part 4.2).

## [1.0.0]

### Added
- Initial release.

[Unreleased]: https://github.com/opentmf/opentmf-mockserver/compare/1.1.1...HEAD
[1.1.1]: https://github.com/opentmf/opentmf-mockserver/compare/1.1.0...1.1.1
[1.1.0]: https://github.com/opentmf/opentmf-mockserver/compare/1.0.9...1.1.0
[1.0.9]: https://github.com/opentmf/opentmf-mockserver/compare/1.0.8...1.0.9
[1.0.8]: https://github.com/opentmf/opentmf-mockserver/compare/opentmf-mockserver-1.0.7...1.0.8
[1.0.7]: https://github.com/opentmf/opentmf-mockserver/compare/opentmf-mockserver-1.0.6...opentmf-mockserver-1.0.7
[1.0.6]: https://github.com/opentmf/opentmf-mockserver/compare/opentmf-mockserver-1.0.5...opentmf-mockserver-1.0.6
[1.0.5]: https://github.com/opentmf/opentmf-mockserver/compare/opentmf-mockserver-1.0.4...opentmf-mockserver-1.0.5
[1.0.4]: https://github.com/opentmf/opentmf-mockserver/compare/opentmf-mockserver-1.0.3...opentmf-mockserver-1.0.4
[1.0.3]: https://github.com/opentmf/opentmf-mockserver/compare/opentmf-mockserver-1.0.2...opentmf-mockserver-1.0.3
[1.0.2]: https://github.com/opentmf/opentmf-mockserver/tag/opentmf-mockserver-1.0.2
[1.0.1]: https://github.com/opentmf/opentmf-mockserver/releases/tag/1.0.1
[1.0.0]: https://github.com/opentmf/opentmf-mockserver/releases/tag/1.0.0

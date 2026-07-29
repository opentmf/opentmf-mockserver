package org.opentmf.mockserver.testsupport;

import static org.mockserver.model.HttpRequest.request;

import org.mockserver.client.MockServerClient;
import org.opentmf.mockserver.callback.DynamicDeleteCallback;
import org.opentmf.mockserver.callback.DynamicGetCallback;
import org.opentmf.mockserver.callback.DynamicGetListCallback;
import org.opentmf.mockserver.callback.DynamicJsonPatchCallback;
import org.opentmf.mockserver.callback.DynamicJsonPatchCollectionCallback;
import org.opentmf.mockserver.callback.DynamicMergePatchCallback;
import org.opentmf.mockserver.callback.DynamicPostCallback;
import org.opentmf.mockserver.callback.DynamicPutCallback;

/**
 * Fluent registration of {@code org.opentmf.mockserver.callback.Dynamic*Callback}s for a
 * TMF resource. Each method wires one HTTP verb at a given collection path (single-resource
 * endpoints get {@code /{id}} appended automatically). {@link #crud(String)} does all five.
 *
 * <p>The {@code apiClientId} constructor argument is a label — it does not affect the URL
 * pattern; it exists so a follow-up
 * {@link MockServerSupport#redirectApiClients(org.springframework.test.context.DynamicPropertyRegistry,
 * String...)} call using the same id has an obvious pairing.
 */
public class TmfMockBuilder {

  private static final String PATCH = "PATCH";
  private static final String CONTENT_TYPE = "Content-Type";
  private static final String JSON_PATCH_CT = "application/json-patch\\+json.*";
  private static final String MERGE_PATCH_CT = "application/merge-patch\\+json.*";

  private final MockServerClient client;
  private final String apiClientId;

  TmfMockBuilder(MockServerClient client, String apiClientId) {
    this.client = client;
    this.apiClientId = apiClientId;
  }

  /** {@code POST <path>} → {@link DynamicPostCallback}. */
  public TmfMockBuilder post(String path) {
    client
        .when(request().withMethod("POST").withPath(path))
        .respond(new DynamicPostCallback());
    return this;
  }

  /** {@code GET <path>/{id}} → {@link DynamicGetCallback}. */
  public TmfMockBuilder get(String path) {
    client
        .when(request().withMethod("GET").withPath(withId(path)))
        .respond(new DynamicGetCallback());
    return this;
  }

  /** {@code GET <path>} → {@link DynamicGetListCallback}. */
  public TmfMockBuilder getList(String path) {
    client
        .when(request().withMethod("GET").withPath(path))
        .respond(new DynamicGetListCallback());
    return this;
  }

  /** {@code PUT <path>/{id}} → {@link DynamicPutCallback}. */
  public TmfMockBuilder put(String path) {
    client
        .when(request().withMethod("PUT").withPath(withId(path)))
        .respond(new DynamicPutCallback());
    return this;
  }

  /** {@code DELETE <path>/{id}} → {@link DynamicDeleteCallback}. */
  public TmfMockBuilder delete(String path) {
    client
        .when(request().withMethod("DELETE").withPath(withId(path)))
        .respond(new DynamicDeleteCallback());
    return this;
  }

  /**
   * {@code PATCH <path>/{id}} with {@code Content-Type: application/json-patch+json} →
   * {@link DynamicJsonPatchCallback}.
   */
  public TmfMockBuilder jsonPatch(String path) {
    client
        .when(
            request()
                .withMethod(PATCH)
                .withPath(withId(path))
                .withHeader(CONTENT_TYPE, JSON_PATCH_CT))
        .respond(new DynamicJsonPatchCallback());
    return this;
  }

  /**
   * {@code PATCH <path>/{id}} with {@code Content-Type: application/merge-patch+json} →
   * {@link DynamicMergePatchCallback}.
   */
  public TmfMockBuilder mergePatch(String path) {
    client
        .when(
            request()
                .withMethod(PATCH)
                .withPath(withId(path))
                .withHeader(CONTENT_TYPE, MERGE_PATCH_CT))
        .respond(new DynamicMergePatchCallback());
    return this;
  }

  /**
   * {@code PATCH <path>} with {@code Content-Type: application/json-patch+json} →
   * {@link DynamicJsonPatchCollectionCallback}. Batch collection patch.
   */
  public TmfMockBuilder jsonPatchCollection(String path) {
    client
        .when(
            request()
                .withMethod(PATCH)
                .withPath(path)
                .withHeader(CONTENT_TYPE, JSON_PATCH_CT))
        .respond(new DynamicJsonPatchCollectionCallback());
    return this;
  }

  /**
   * Register POST + GET + list + PUT + DELETE in one call. Equivalent to
   * {@code post(path).get(path).getList(path).put(path).delete(path)}.
   */
  public TmfMockBuilder crud(String path) {
    return post(path).get(path).getList(path).put(path).delete(path);
  }

  /** Label passed to the constructor. Useful for diagnostics/logging. */
  public String apiClientId() {
    return apiClientId;
  }

  private static String withId(String path) {
    String base = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    return base + "/[^/]+";
  }
}

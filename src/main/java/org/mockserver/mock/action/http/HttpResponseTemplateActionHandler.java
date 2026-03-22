package org.mockserver.mock.action.http;

import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpTemplate;

public class HttpResponseTemplateActionHandler {

  public HttpResponseTemplateActionHandler(
      MockServerLogger mockServerLogger, Configuration configuration) {}

  public HttpResponse handle(HttpTemplate httpTemplate, HttpRequest httpRequest) {
    throw new UnsupportedOperationException(
        "Template response handling is not supported in this build");
  }
}

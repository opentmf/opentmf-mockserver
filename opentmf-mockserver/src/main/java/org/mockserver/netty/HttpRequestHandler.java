package org.mockserver.netty;

import static io.netty.handler.codec.http.HttpHeaderNames.*;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.exception.ExceptionHandling.closeOnFlush;
import static org.mockserver.exception.ExceptionHandling.connectionClosedException;
import static org.mockserver.metrics.Metrics.Name.REQUESTS_RECEIVED_COUNT;
import static org.mockserver.mock.HttpState.PATH_PREFIX;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.PortBinding.portBinding;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;
import java.net.BindException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.mockserver.configuration.Configuration;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.metrics.Metrics;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.action.http.HttpActionHandler;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.MediaType;
import org.mockserver.model.PortBinding;
import org.mockserver.netty.responsewriter.NettyResponseWriter;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.PortBindingSerializer;
import org.slf4j.event.Level;

/**
 * @author jamesdbloom
 */
@ChannelHandler.Sharable
@SuppressWarnings("FieldMayBeFinal")
public class HttpRequestHandler extends SimpleChannelInboundHandler<HttpRequest> {

  public static final AttributeKey<Boolean> PROXYING = AttributeKey.valueOf("PROXYING");
  public static final AttributeKey<Set<String>> LOCAL_HOST_HEADERS =
      AttributeKey.valueOf("LOCAL_HOST_HEADERS");
  private final Configuration configuration;
  private LifeCycle server;
  private HttpState httpState;
  private Metrics metrics;
  private MockServerLogger mockServerLogger;
  private PortBindingSerializer portBindingSerializer;
  private HttpActionHandler httpActionHandler;

  public HttpRequestHandler(
      Configuration configuration,
      LifeCycle server,
      HttpState httpState,
      HttpActionHandler httpActionHandler) {
    super(false);
    this.configuration = configuration;
    this.server = server;
    this.httpState = httpState;
    this.metrics = new Metrics(configuration);
    this.mockServerLogger = httpState.getMockServerLogger();
    this.portBindingSerializer = new PortBindingSerializer(mockServerLogger);
    this.httpActionHandler = httpActionHandler;
  }

  private static boolean isProxyingRequest(ChannelHandlerContext ctx) {
    if (ctx != null && ctx.channel() != null && ctx.channel().attr(PROXYING).get() != null) {
      return ctx.channel().attr(PROXYING).get();
    }
    return false;
  }

  public static void setProxyingRequest(ChannelHandlerContext ctx, Boolean value) {
    if (ctx != null && ctx.channel() != null) {
      ctx.channel().attr(PROXYING).set(value);
    }
  }

  private static Set<String> getLocalAddresses(ChannelHandlerContext ctx) {
    if (ctx != null
        && ctx.channel().attr(LOCAL_HOST_HEADERS) != null
        && ctx.channel().attr(LOCAL_HOST_HEADERS).get() != null) {
      return ctx.channel().attr(LOCAL_HOST_HEADERS).get();
    }
    return new HashSet<>();
  }

  @Override
  protected void channelRead0(final ChannelHandlerContext ctx, final HttpRequest request) {

    if (configuration.metricsEnabled()) {
      metrics.increment(REQUESTS_RECEIVED_COUNT);
    }

    ResponseWriter responseWriter =
        new NettyResponseWriter(configuration, mockServerLogger, ctx, httpState.getScheduler());
    try {
      configuration.addSubjectAlternativeName(request.getFirstHeader(HOST.toString()));

      if (!httpState.handle(request, responseWriter, false)) {

        if (request.matches("PUT", PATH_PREFIX + "/status", "/status")
            || isNotBlank(configuration.livenessHttpGetPath())
                && request.matches("GET", configuration.livenessHttpGetPath())) {

          responseWriter.writeResponse(
              request,
              OK,
              portBindingSerializer.serialize(portBinding(server.getLocalPorts())),
              "application/json");

        } else if (request.matches("PUT", PATH_PREFIX + "/bind", "/bind")) {

          PortBinding requestedPortBindings =
              portBindingSerializer.deserialize(request.getBodyAsString());
          if (requestedPortBindings != null) {
            try {
              List<Integer> actualPortBindings =
                  server.bindServerPorts(requestedPortBindings.getPorts());
              responseWriter.writeResponse(
                  request,
                  OK,
                  portBindingSerializer.serialize(portBinding(actualPortBindings)),
                  "application/json");
            } catch (RuntimeException e) {
              if (e.getCause() instanceof BindException) {
                responseWriter.writeResponse(
                    request,
                    BAD_REQUEST,
                    e.getMessage() + " port already in use",
                    MediaType.create("text", "plain").toString());
              } else {
                throw e;
              }
            }
          }

        } else if (request.matches("PUT", PATH_PREFIX + "/stop", "/stop")) {

          ctx.writeAndFlush(response().withStatusCode(OK.code()));
          new Scheduler.SchedulerThreadFactory("MockServer Stop")
              .newThread(() -> server.stop())
              .start();

        } else if (request.getMethod().getValue().equals("CONNECT")) {

          responseWriter.writeResponse(
              request,
              BAD_REQUEST,
              "CONNECT method not supported - proxy functionality is disabled",
              MediaType.create("text", "plain").toString());

        } else {

          try {
            httpActionHandler.processAction(
                request,
                responseWriter,
                ctx,
                getLocalAddresses(ctx),
                isProxyingRequest(ctx),
                false);
          } catch (Throwable throwable) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setHttpRequest(request)
                    .setMessageFormat("exception processing request:{}error:{}")
                    .setArguments(request, throwable.getMessage())
                    .setThrowable(throwable));
          }
        }
      }
    } catch (IllegalArgumentException iae) {
      mockServerLogger.logEvent(
          new LogEntry()
              .setLogLevel(Level.ERROR)
              .setHttpRequest(request)
              .setMessageFormat("exception processing request:{}error:{}")
              .setArguments(request, iae.getMessage()));
      // send request without API CORS headers
      responseWriter.writeResponse(
          request, BAD_REQUEST, iae.getMessage(), MediaType.create("text", "plain").toString());
    } catch (Exception ex) {
      mockServerLogger.logEvent(
          new LogEntry()
              .setLogLevel(Level.ERROR)
              .setHttpRequest(request)
              .setMessageFormat("exception processing " + request)
              .setThrowable(ex));
      responseWriter.writeResponse(
          request, response().withStatusCode(BAD_REQUEST.code()).withBody(ex.getMessage()), true);
    }
  }

  @Override
  public void channelReadComplete(ChannelHandlerContext ctx) {
    ctx.flush();
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
    if (connectionClosedException(cause)) {
      mockServerLogger.logEvent(
          new LogEntry()
              .setLogLevel(Level.ERROR)
              .setMessageFormat(
                  "exception caught by "
                      + server.getClass()
                      + " handler -> closing pipeline "
                      + ctx.channel())
              .setThrowable(cause));
    }
    closeOnFlush(ctx.channel());
  }
}

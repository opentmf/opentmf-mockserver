package org.mockserver.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.FullHttpResponse;
import java.util.List;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mappers.FullHttpResponseToMockServerHttpResponse;

/**
 * @author jamesdbloom
 */
public class NettyHttpToMockServerHttpResponseDecoder
    extends MessageToMessageDecoder<FullHttpResponse> {

  private final FullHttpResponseToMockServerHttpResponse fullHttpResponseToMockServerResponse;

  NettyHttpToMockServerHttpResponseDecoder(MockServerLogger mockServerLogger) {
    fullHttpResponseToMockServerResponse =
        new FullHttpResponseToMockServerHttpResponse(mockServerLogger);
  }

  @Override
  protected void decode(
      ChannelHandlerContext ctx, FullHttpResponse fullHttpResponse, List<Object> out) {
    out.add(
        fullHttpResponseToMockServerResponse.mapFullHttpResponseToMockServerResponse(
            fullHttpResponse));
  }
}

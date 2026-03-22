package org.mockserver.codec;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import java.util.List;
import org.mockserver.model.BinaryMessage;

public class MockServerBinaryToNettyBinaryRequestEncoder
    extends MessageToMessageEncoder<BinaryMessage> {
  @Override
  protected void encode(ChannelHandlerContext ctx, BinaryMessage binaryMessage, List<Object> out) {
    out.add(Unpooled.copiedBuffer(binaryMessage.getBytes()));
  }
}

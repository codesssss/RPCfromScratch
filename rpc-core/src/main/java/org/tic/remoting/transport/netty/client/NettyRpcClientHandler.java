package org.tic.remoting.transport.netty.client;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.tic.enums.CompressTypeEnum;
import org.tic.enums.SerializationTypeEnum;
import org.tic.factory.SingletonFactory;
import org.tic.remoting.constants.RpcConstants;
import org.tic.remoting.dto.RpcMessage;
import org.tic.remoting.dto.RpcResponse;

import java.net.InetSocketAddress;

/**
 * @author codesssss
 * @date 18/8/2024 11:22 pm
 */
@Slf4j
public class NettyRpcClientHandler extends ChannelInboundHandlerAdapter {

    // Manager for unprocessed RPC requests
    private final UnprocessedRequests unprocessedRequests;

    // Reference to the Netty RPC client
    private final NettyRpcClient nettyRpcClient;

    public NettyRpcClientHandler() {
        // Initialize the manager for unprocessed requests
        this.unprocessedRequests = SingletonFactory.getInstance(UnprocessedRequests.class);
        // Initialize the RPC client
        this.nettyRpcClient = SingletonFactory.getInstance(NettyRpcClient.class);
    }

    /**
     * Handles the messages received from the server.
     *
     * @param ctx the channel handler context
     * @param msg the message received from the server
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            log.info("client receive msg: [{}]", msg);
            if (msg instanceof RpcMessage) {
                RpcMessage tmp = (RpcMessage) msg;
                byte messageType = tmp.getMessageType();
                if (messageType == RpcConstants.HEARTBEAT_RESPONSE_TYPE) {
                    log.info("Heartbeat response received: [{}]", tmp.getData());
                } else if (messageType == RpcConstants.RESPONSE_TYPE) {
                    RpcResponse<Object> rpcResponse = (RpcResponse<Object>) tmp.getData();
                    unprocessedRequests.complete(rpcResponse);
                }
            }
        } finally {
            // Release the reference to the message to prevent memory leaks
            ReferenceCountUtil.release(msg);
        }
    }

    /**
     * Handles special user events, such as idle state events.
     *
     * @param ctx the channel handler context
     * @param evt the event triggered
     * @throws Exception if an error occurs while handling the event
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleState state = ((IdleStateEvent) evt).state();
            if (state == IdleState.WRITER_IDLE) {
                log.info("Write idle detected for [{}]", ctx.channel().remoteAddress());
                Channel channel = nettyRpcClient.getChannel((InetSocketAddress) ctx.channel().remoteAddress());
                RpcMessage rpcMessage = new RpcMessage();
                rpcMessage.setCodec(SerializationTypeEnum.KRYO.getCode());
                rpcMessage.setCompress(CompressTypeEnum.GZIP.getCode());
                rpcMessage.setMessageType(RpcConstants.HEARTBEAT_REQUEST_TYPE);
                rpcMessage.setData(RpcConstants.PING);
                // Send the heartbeat request and close the channel if the write fails
                channel.writeAndFlush(rpcMessage).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    /**
     * Called when an exception occurs during the processing of a client message.
     *
     * @param ctx the channel handler context
     * @param cause the exception that was caught
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Client caught an exception:", cause);
        cause.printStackTrace();
        // Close the channel in case of an exception
        ctx.close();
    }
}


package org.tic.remoting.transport.netty.server;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.tic.enums.CompressTypeEnum;
import org.tic.enums.RpcResponseCodeEnum;
import org.tic.enums.SerializationTypeEnum;
import org.tic.factory.SingletonFactory;
import org.tic.remoting.constants.RpcConstants;
import org.tic.remoting.dto.RpcMessage;
import org.tic.remoting.dto.RpcRequest;
import org.tic.remoting.dto.RpcResponse;
import org.tic.remoting.handler.RpcRequestHandler;
import org.tic.utils.threadpoolutils.ThreadPoolFactoryUtil;

import java.util.concurrent.ExecutorService;

/**
 * @author codesssss
 * @date 18/8/2024 11:02 pm
 */
@Slf4j
public class NettyRpcServerHandler extends ChannelInboundHandlerAdapter {

    private final RpcRequestHandler rpcRequestHandler;
    private final ExecutorService bizExecutor;
    private final BackpressureLimiter limiter;
    private final ServerStateManager stateManager;
    private final String poolName;

    public NettyRpcServerHandler(ExecutorService bizExecutor, BackpressureLimiter limiter, ServerStateManager stateManager, String poolName) {
        this.rpcRequestHandler = SingletonFactory.getInstance(RpcRequestHandler.class);
        this.bizExecutor = bizExecutor;
        this.limiter = limiter;
        this.stateManager = stateManager;
        this.poolName = poolName;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            if (msg instanceof RpcMessage) {
                log.info("server receive msg: [{}] ", msg);
                byte messageType = ((RpcMessage) msg).getMessageType();
                RpcMessage rpcMessage = new RpcMessage();
                rpcMessage.setCodec(SerializationTypeEnum.KRYO.getCode());
                rpcMessage.setCompress(CompressTypeEnum.GZIP.getCode());
                if (messageType == RpcConstants.HEARTBEAT_REQUEST_TYPE) {
                    rpcMessage.setMessageType(RpcConstants.HEARTBEAT_RESPONSE_TYPE);
                    rpcMessage.setData(RpcConstants.PONG);
                    ctx.writeAndFlush(rpcMessage).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                } else {
                    RpcRequest rpcRequest = (RpcRequest) ((RpcMessage) msg).getData();
                    if (!stateManager.tryEnterRequest()) {
                        log.warn("Server draining/stopped, reject request: {}", rpcRequest.getRequestId());
                        sendOverload(ctx, rpcRequest, rpcMessage);
                        return;
                    }
                    ThreadPoolFactoryUtil.ThreadPoolStats stats = ThreadPoolFactoryUtil.getThreadPoolStats(poolName);
                    int queueSize = stats == null ? 0 : stats.queueSize;
                    int inflight = stateManager.getInflight();
                    if (!limiter.allow(inflight, queueSize)) {
                        stateManager.onRequestComplete();
                        log.warn("Backpressure triggered. inflight={}, queue={}", inflight, queueSize);
                        sendOverload(ctx, rpcRequest, rpcMessage);
                        return;
                    }
                    try {
                        bizExecutor.execute(() -> handleRequest(ctx, rpcRequest, rpcMessage));
                    } catch (Exception e) {
                        stateManager.onRequestComplete();
                        log.error("Submit to biz executor failed", e);
                        sendOverload(ctx, rpcRequest, rpcMessage);
                    }
                }
            }
        } finally {
            //Ensure that ByteBuf is released, otherwise there may be memory leaks
            ReferenceCountUtil.release(msg);
        }
    }

    private void handleRequest(ChannelHandlerContext ctx, RpcRequest rpcRequest, RpcMessage rpcMessage) {
        try {
            // Execute the target method (the method the client needs to execute) and return the method result
            Object result = rpcRequestHandler.handle(rpcRequest);
            log.info(String.format("server get result: %s", result.toString()));
            rpcMessage.setMessageType(RpcConstants.RESPONSE_TYPE);
            if (ctx.channel().isActive() && ctx.channel().isWritable()) {
                RpcResponse<Object> rpcResponse = RpcResponse.success(result, rpcRequest.getRequestId());
                rpcMessage.setData(rpcResponse);
            } else {
                RpcResponse<Object> rpcResponse = RpcResponse.fail(RpcResponseCodeEnum.FAIL);
                rpcMessage.setData(rpcResponse);
                log.error("not writable now, message dropped");
            }
            ctx.writeAndFlush(rpcMessage).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        } finally {
            stateManager.onRequestComplete();
        }
    }

    private void sendOverload(ChannelHandlerContext ctx, RpcRequest rpcRequest, RpcMessage rpcMessage) {
        rpcMessage.setMessageType(RpcConstants.RESPONSE_TYPE);
        RpcResponse<Object> rpcResponse = RpcResponse.fail(RpcResponseCodeEnum.TOO_MANY_REQUESTS);
        rpcResponse.setRequestId(rpcRequest.getRequestId());
        rpcMessage.setData(rpcResponse);
        ctx.writeAndFlush(rpcMessage).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleState state = ((IdleStateEvent) evt).state();
            if (state == IdleState.READER_IDLE) {
                log.info("idle check happen, so close the connection");
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("server catch exception");
        cause.printStackTrace();
        ctx.close();
    }
}

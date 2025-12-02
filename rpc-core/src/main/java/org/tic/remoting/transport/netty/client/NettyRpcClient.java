package org.tic.remoting.transport.netty.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;
import org.tic.config.ConfigResolver;
import org.tic.enums.CompressTypeEnum;
import org.tic.enums.RpcConfigEnum;
import org.tic.enums.SerializationTypeEnum;
import org.tic.enums.ServiceDiscoveryEnum;
import org.tic.exception.RpcException;
import org.tic.extension.ExtensionLoader;
import org.tic.factory.SingletonFactory;
import org.tic.registry.ServiceDiscovery;
import org.tic.remoting.constants.RpcConstants;
import org.tic.remoting.dto.RpcMessage;
import org.tic.remoting.dto.RpcRequest;
import org.tic.remoting.dto.RpcResponse;
import org.tic.remoting.transport.RpcRequestTransport;
import org.tic.remoting.transport.netty.codec.RpcMessageDecoder;
import org.tic.remoting.transport.netty.codec.RpcMessageEncoder;
import org.tic.remoting.transport.netty.client.InstanceHealthTracker;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Netty RPC client with connection retry and channel health check.
 * 
 * @author codesssss
 * @date 18/8/2024 11:21 pm
 */
@Slf4j
public final class NettyRpcClient implements RpcRequestTransport {
    
    /**
     * Default retry count for connection attempts
     */
    private static final int DEFAULT_RETRY_COUNT = 3;
    
    /**
     * Default base interval for retry in milliseconds
     */
    private static final long DEFAULT_RETRY_INTERVAL_MS = 1000L;
    
    /**
     * Connection timeout in milliseconds
     */
    private static final int CONNECT_TIMEOUT_MS = 5000;
    
    private final ServiceDiscovery serviceDiscovery;
    private final UnprocessedRequests unprocessedRequests;
    private final ChannelProvider channelProvider;
    private final Bootstrap bootstrap;
    private final EventLoopGroup eventLoopGroup;
    private final InstanceHealthTracker healthTracker;
    private final int retryCount;
    private final long retryIntervalMs;

    public NettyRpcClient() {
        // Load configuration
        this.retryCount = loadRetryCount();
        this.retryIntervalMs = loadRetryInterval();
        
        // initialize resources such as EventLoopGroup, Bootstrap
        eventLoopGroup = new NioEventLoopGroup();
        bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                //  The timeout period of the connection.
                //  If this time is exceeded or the connection cannot be established, the connection fails.
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MS)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline p = ch.pipeline();
                        // If no data is sent to the server within 5 seconds, a heartbeat request is sent
                        p.addLast(new IdleStateHandler(0, 5, 0, TimeUnit.SECONDS));
                        p.addLast(new RpcMessageEncoder());
                        p.addLast(new RpcMessageDecoder());
                        p.addLast(new NettyRpcClientHandler());
                    }
                });
        this.serviceDiscovery = ExtensionLoader.getExtensionLoader(ServiceDiscovery.class).getExtension(ServiceDiscoveryEnum.ZK.getName());
        this.unprocessedRequests = SingletonFactory.getInstance(UnprocessedRequests.class);
        this.channelProvider = SingletonFactory.getInstance(ChannelProvider.class);
        this.healthTracker = SingletonFactory.getInstance(InstanceHealthTracker.class);
    }
    
    /**
     * Load retry count from configuration
     */
    private int loadRetryCount() {
        return ConfigResolver.getInt(RpcConfigEnum.RPC_CONNECT_RETRY_COUNT.getPropertyValue(), DEFAULT_RETRY_COUNT);
    }

    /**
     * Load retry interval from configuration
     */
    private long loadRetryInterval() {
        return ConfigResolver.getLong(RpcConfigEnum.RPC_CONNECT_RETRY_INTERVAL_MS.getPropertyValue(), DEFAULT_RETRY_INTERVAL_MS);
    }

    /**
     * Connect to server with exponential backoff retry.
     *
     * @param inetSocketAddress server address
     * @return the channel
     * @throws RpcException if all retry attempts fail
     */
    public Channel doConnect(InetSocketAddress inetSocketAddress) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= retryCount; attempt++) {
            final int currentAttempt = attempt;
            try {
                CompletableFuture<Channel> completableFuture = new CompletableFuture<>();
                
                bootstrap.connect(inetSocketAddress).addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        log.info("The client has connected [{}] successful! (attempt {})", inetSocketAddress, currentAttempt);
                        completableFuture.complete(future.channel());
                    } else {
                        completableFuture.completeExceptionally(future.cause());
                    }
                });
                
                // Wait for connection with timeout
                Channel channel = completableFuture.get(CONNECT_TIMEOUT_MS + 1000, TimeUnit.MILLISECONDS);
                return channel;
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RpcException("Connection interrupted to " + inetSocketAddress, e);
            } catch (ExecutionException | TimeoutException e) {
                lastException = e;
                healthTracker.recordFailure(formatAddress(inetSocketAddress));
                log.warn("Failed to connect to [{}], attempt {}/{}, error: {}", 
                        inetSocketAddress, attempt, retryCount, e.getMessage());
                
                if (attempt < retryCount) {
                    // Exponential backoff: interval * 2^(attempt-1)
                    long sleepTime = retryIntervalMs * (1L << (attempt - 1));
                    log.info("Retrying connection to [{}] in {}ms...", inetSocketAddress, sleepTime);
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RpcException("Connection retry interrupted to " + inetSocketAddress, ie);
                    }
                }
            }
        }
        
        // All retries failed
        String errorMsg = String.format("Failed to connect to [%s] after %d attempts", inetSocketAddress, retryCount);
        log.error(errorMsg);
        throw new RpcException(errorMsg, lastException);
    }

    @Override
    public Object sendRpcRequest(RpcRequest rpcRequest) {
        // build return value
        CompletableFuture<RpcResponse<Object>> resultFuture = new CompletableFuture<>();
        // get server address
        InetSocketAddress inetSocketAddress = serviceDiscovery.lookupService(rpcRequest);
        // get server address related channel (with health check)
        Channel channel = getChannel(inetSocketAddress);
        if (channel.isActive()) {
            // put unprocessed request
            unprocessedRequests.put(rpcRequest.getRequestId(), resultFuture);
            RpcMessage rpcMessage = RpcMessage.builder().data(rpcRequest)
                    .codec(SerializationTypeEnum.KRYO.getCode())
                    .compress(CompressTypeEnum.GZIP.getCode())
                    .messageType(RpcConstants.REQUEST_TYPE).build();
            channel.writeAndFlush(rpcMessage).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    log.info("client send message: [{}]", rpcMessage);
                    healthTracker.recordSuccess(formatAddress(inetSocketAddress));
                } else {
                    future.channel().close();
                    // Remove from unprocessed requests
                    unprocessedRequests.remove(rpcRequest.getRequestId());
                    healthTracker.recordFailure(formatAddress(inetSocketAddress));
                    resultFuture.completeExceptionally(future.cause());log.error("Send failed:", future.cause());
                }
            });
        } else {
            // Channel is not active, remove it and throw exception
            channelProvider.remove(inetSocketAddress);
            healthTracker.recordFailure(formatAddress(inetSocketAddress));
            throw new RpcException("Channel is not active for address: " + inetSocketAddress);
        } // 1. 这里添加括号，关闭 else 块

        return resultFuture; // 2. 这个 return 现在位于 else 之外
    } // 3. 这里添加括号，关闭 sendRpcRequest 方法

    /**
     * Get channel for the given address with health check.
     * If the cached channel is inactive, it will be removed and a new connection will be established.
     *
     * @param inetSocketAddress server address
     * @return active channel
     */
    public Channel getChannel(InetSocketAddress inetSocketAddress) {
        Channel channel = channelProvider.get(inetSocketAddress);
        
        // Check if channel exists and is active
        if (channel != null) {
            if (channel.isActive()) {
                return channel;
            } else {
                // Channel is inactive, remove it from cache
                log.warn("Cached channel for [{}] is inactive, removing and reconnecting...", inetSocketAddress);
                channelProvider.remove(inetSocketAddress);
            }
        }
        
        // Create new connection
        channel = doConnect(inetSocketAddress);
        channelProvider.set(inetSocketAddress, channel);
        return channel;
    }

    String formatAddress(InetSocketAddress address) {
        return address.getHostString() + ":" + address.getPort();
    }

    public void close() {
        eventLoopGroup.shutdownGracefully();
    }
}

package org.tic;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Xuhang Shi
 * @date 27/11/2023 10:11 pm
 */
public class NettyClient {
    private static final Logger logger = LoggerFactory.getLogger(NettyClient.class);
    private final String host;
    private final int port;
    private static final Bootstrap b;

    public NettyClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // 初始化相关资源比如 EventLoopGroup, Bootstrap
    static {
        EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        b = new Bootstrap();
        KryoSerializer kryoSerializer = new KryoSerializer();
        b.group(eventLoopGroup)
                .channel(NioSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                // 连接的超时时间，超过这个时间还是建立不上的话则代表连接失败
                //  如果 15 秒之内没有发送数据给服务端的话，就发送一次心跳请求
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        /*
                         自定义序列化编解码器
                         */
                        // RpcResponse -> ByteBuf
                        ch.pipeline().addLast(new NettyKryoDecoder(kryoSerializer, RpcResponse.class));
                        // ByteBuf -> RpcRequest
                        ch.pipeline().addLast(new NettyKryoEncoder(kryoSerializer, RpcRequest.class));
                        ch.pipeline().addLast(new NettyClientHandler());
                    }
                });
    }    /**
     * 发送消息到服务端
     *
     * @param rpcRequest 消息体
     * @return 服务端返回的数据
     */
    public RpcResponse sendMessage(RpcRequest rpcRequest) {
        try {
            ChannelFuture f = b.connect(host, port).sync();
            logger.info("client connect  {}", host + ":" + port);
            Channel futureChannel = f.channel();
            logger.info("send message");
            if (futureChannel != null) {
                futureChannel.writeAndFlush(rpcRequest).addListener(future -> {
                    if (future.isSuccess()) {
                        logger.info("client send message: [{}]", rpcRequest.toString());
                    } else {
                        logger.error("Send failed:", future.cause());
                    }
                });
                //阻塞等待 ，直到Channel关闭
                futureChannel.closeFuture().sync();
                // 将服务端返回的数据也就是RpcResponse对象取出
                AttributeKey<RpcResponse> key = AttributeKey.valueOf("rpcResponse");
                return futureChannel.attr(key).get();
            }
        } catch (InterruptedException e) {
            logger.error("occur exception when connect server:", e);
        }
        return null;
    }
}

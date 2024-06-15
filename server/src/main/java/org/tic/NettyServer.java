package org.tic;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tic.serialize.kyro.KryoSerializer;
import org.tic.transport.netty.NettyKryoDecoder;
import org.tic.transport.netty.NettyKryoEncoder;

/**
 * @author codesssss
 * @date 27/11/2023 11:25 pm
 */
public class NettyServer {
    private static final Logger logger = LoggerFactory.getLogger(NettyServer.class);
    private final int port;

    NettyServer(int port) {
        this.port = port;
    }

    void run() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        KryoSerializer kryoSerializer = new KryoSerializer();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // TCP usually enables Nagle's algorithm, which aims to send larger packets to reduce network transmission. TCP_NODELAY option is used to control whether Nagle's algorithm is enabled.
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    // Enables the TCP underlying heartbeat mechanism
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    // This represents the maximum length of the queue for temporarily storing requests that have completed the TCP three-way handshake. If connections are frequent and the server is slow to create new connections, consider increasing this value.
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new NettyKryoDecoder(kryoSerializer, RpcRequest.class));
                            ch.pipeline().addLast(new NettyKryoEncoder(kryoSerializer, RpcResponse.class));
                            ch.pipeline().addLast(new NettyServerHandler());
                        }
                    });

            // Bind to the port and wait synchronously for it to complete
            ChannelFuture f = b.bind(port).sync();
            // Wait for the server socket channel to close
            f.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            logger.error("occur exception when start server:", e);
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}

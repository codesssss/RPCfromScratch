package org.tic.config;

import lombok.extern.slf4j.Slf4j;
import org.tic.registry.zk.utils.CuratorUtils;
import org.tic.remoting.transport.netty.server.NettyRpcServer;
import org.tic.remoting.transport.netty.server.ServerStateManager;
import org.tic.utils.threadpoolutils.ThreadPoolFactoryUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * @author codesssss
 * @date 18/8/2024 11:04 pm
 */
@Slf4j
public class CustomShutdownHook {
    private static final CustomShutdownHook CUSTOM_SHUTDOWN_HOOK = new CustomShutdownHook();

    public static CustomShutdownHook getCustomShutdownHook() {
        return CUSTOM_SHUTDOWN_HOOK;
    }

    public void clearAll() {
        register(null, null, null);
    }

    public void register(ServerStateManager stateManager, InetSocketAddress address, Runnable closeServer, EventLoopGroup bossGroup, EventLoopGroup workerGroup) {
        log.info("addShutdownHook for graceful shutdown");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (stateManager != null) {
                stateManager.beginDrain();
            }
            if (closeServer != null) {
                closeServer.run();
            }
            if (stateManager != null) {
                stateManager.awaitDrain();
                stateManager.markStopped();
            }
            try {
                InetSocketAddress inetSocketAddress = Objects.requireNonNullElseGet(address, () -> {
                    try {
                        return new InetSocketAddress(InetAddress.getLocalHost().getHostAddress(), NettyRpcServer.PORT);
                    } catch (UnknownHostException e) {
                        return null;
                    }
                });
                if (inetSocketAddress != null) {
                    CuratorUtils.clearRegistry(CuratorUtils.getZkClient(), inetSocketAddress);
                }
            } catch (Exception e) {
                log.error("Failed to clear registry on shutdown", e);
            }
            ThreadPoolFactoryUtil.shutDownAllThreadPool();
            // Ensure netty event loops are closed even if not in try/finally path
            if (bossGroup != null) {
                bossGroup.shutdownGracefully();
            }
            if (workerGroup != null) {
                workerGroup.shutdownGracefully();
            }
        }));
    }
}

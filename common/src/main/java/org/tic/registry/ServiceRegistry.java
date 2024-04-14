package org.tic.registry;

import java.net.InetSocketAddress;

/**
 * @author Xuhang Shi
 * @date 14/4/2024 11:34 pm
 */
public interface ServiceRegistry {
    void registerService(String rpcServiceName, InetSocketAddress inetSocketAddress);
}

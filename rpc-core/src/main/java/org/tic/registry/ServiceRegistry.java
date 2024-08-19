package org.tic.registry;

import org.tic.extension.SPI;

import java.net.InetSocketAddress;

/**
 * @author codesssss
 * @date 14/4/2024 11:34 pm
 */
@SPI
public interface ServiceRegistry {
    void registerService(String rpcServiceName, InetSocketAddress inetSocketAddress);
}

package org.tic.registry;

import org.tic.RpcRequest;

import java.net.InetSocketAddress;

/**
 * @author codesssss
 * @date 14/4/2024 11:18 pm
 */
public interface ServiceDiscovery {
    InetSocketAddress lookupService(RpcRequest rpcRequest);
}

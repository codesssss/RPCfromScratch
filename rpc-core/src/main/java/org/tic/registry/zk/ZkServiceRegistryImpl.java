package org.tic.registry.zk;

import org.apache.curator.framework.CuratorFramework;
import org.tic.config.ConfigResolver;
import org.tic.registry.ServiceRegistry;
import org.tic.registry.zk.utils.CuratorUtils;
import org.tic.enums.RpcConfigEnum;

import java.net.InetSocketAddress;

/**
 * @author codesssss
 * @date 14/4/2024 11:49 pm
 */
public class ZkServiceRegistryImpl implements ServiceRegistry {
    @Override
    public void registerService(String rpcServiceName, InetSocketAddress inetSocketAddress) {
        String suffix = inetSocketAddress.toString();
        String servicePath = CuratorUtils.ZK_REGISTER_ROOT_PATH + "/" + rpcServiceName + suffix;
        CuratorFramework zkClient = CuratorUtils.getZkClient();
        // read instance weight from config (optional)
        int weight = ConfigResolver.getInt(RpcConfigEnum.INSTANCE_WEIGHT.getPropertyValue(), 100);
        byte[] data = String.valueOf(weight).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // create ephemeral node with weight data
        CuratorUtils.createEphemeralNode(zkClient, servicePath, data);
    }
}

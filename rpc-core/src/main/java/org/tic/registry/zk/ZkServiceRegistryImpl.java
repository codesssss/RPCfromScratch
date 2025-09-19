package org.tic.registry.zk;

import org.apache.curator.framework.CuratorFramework;
import org.tic.registry.ServiceRegistry;
import org.tic.registry.zk.utils.CuratorUtils;
import org.tic.enums.RpcConfigEnum;
import org.tic.utils.PropertiesFileUtil;

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
        int weight = 100;
        try {
            java.util.Properties p = PropertiesFileUtil.readPropertiesFile(RpcConfigEnum.RPC_CONFIG_PATH.getPropertyValue());
            if (p != null) {
                String val = p.getProperty(RpcConfigEnum.INSTANCE_WEIGHT.getPropertyValue());
                if (val != null) {
                    weight = Integer.parseInt(val);
                }
            }
        } catch (Exception ignored) {}
        byte[] data = String.valueOf(weight).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // create ephemeral node with weight data
        CuratorUtils.createEphemeralNode(zkClient, servicePath, data);
    }
}

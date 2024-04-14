package org.tic.registry.zk;

import org.apache.curator.framework.CuratorFramework;
import org.tic.registry.ServiceRegistry;
import org.tic.registry.zk.utils.CuratorUtils;

import java.net.InetSocketAddress;

/**
 * @author Xuhang Shi
 * @date 14/4/2024 11:49 pm
 */
public class ZkServiceRegistryImpl implements ServiceRegistry {
    @Override
    public void registerService(String rpcServiceName, InetSocketAddress inetSocketAddress) {
        String servicePath = CuratorUtils.ZK_REGISTER_ROOT_PATH + "/" + rpcServiceName + inetSocketAddress.toString();
        CuratorFramework zkClient = CuratorUtils.getZkClient();
        CuratorUtils.createPersistentNode(zkClient, servicePath);
    }
}

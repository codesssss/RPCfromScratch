package org.tic.registry.zk;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.tic.RpcRequest;
import org.tic.enums.RpcErrorMessageEnum;
import org.tic.exception.RpcException;
import org.tic.registry.ServiceDiscovery;
import org.tic.registry.zk.utils.CuratorUtils;

import java.net.InetSocketAddress;
import java.util.List;


/**
 * @author Xuhang Shi
 * @date 14/4/2024 11:49 pm
 */
@Slf4j
public class ZkServiceDiscoveryImpl implements ServiceDiscovery {
    @Override
    public InetSocketAddress lookupService(RpcRequest rpcRequest) {
        String rpcServiceName = rpcRequest.getRpcServiceName();
        CuratorFramework zkClient = CuratorUtils.getZkClient();
        List<String> serviceUrlList = CuratorUtils.getChildrenNodes(zkClient, rpcServiceName);
        if (serviceUrlList.isEmpty()) {
            throw new RpcException(RpcErrorMessageEnum.SERVICE_CAN_NOT_BE_FOUND, rpcServiceName);
        }
        // load balancing
        String targetServiceUrl = serviceUrlList.get(0);
        log.info("Successfully found the service address:[{}]", targetServiceUrl);
        String[] socketAddressArray = targetServiceUrl.split(":");
        String host = socketAddressArray[0];
        int port = Integer.parseInt(socketAddressArray[1]);
        return new InetSocketAddress(host, port);
    }
}

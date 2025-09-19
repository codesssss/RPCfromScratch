package org.tic.registry.zk;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.tic.enums.LoadBalanceEnum;
import org.tic.enums.RpcErrorMessageEnum;
import org.tic.exception.RpcException;
import org.tic.extension.ExtensionLoader;
import org.tic.loadbalance.LoadBalance;
import org.tic.registry.ServiceDiscovery;
import org.tic.registry.zk.utils.CuratorUtils;
import org.tic.remoting.dto.RpcRequest;
import org.tic.utils.CollectionUtil;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.ArrayList;


/**
 * @author codesssss
 * @date 14/4/2024 11:49 pm
 */
@Slf4j
public class ZkServiceDiscoveryImpl implements ServiceDiscovery {
    private final LoadBalance loadBalance;

    public ZkServiceDiscoveryImpl() {
        this.loadBalance = ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension(LoadBalanceEnum.LOADBALANCE.getName());
    }

    @Override
    public InetSocketAddress lookupService(RpcRequest rpcRequest) {
        String rpcServiceName = rpcRequest.getRpcServiceName();
        CuratorFramework zkClient = CuratorUtils.getZkClient();
        List<String> serviceUrlList = CuratorUtils.getChildrenNodes(zkClient, rpcServiceName);
        if (CollectionUtil.isEmpty(serviceUrlList)) {
            throw new RpcException(RpcErrorMessageEnum.SERVICE_CAN_NOT_BE_FOUND, rpcServiceName);
        }
        // optional: expand addresses by weight if node carries weight data
        List<String> weightedList = buildWeightedList(zkClient, rpcServiceName, serviceUrlList);
        List<String> candidate = weightedList.isEmpty() ? serviceUrlList : weightedList;
        // load balancing
        String targetServiceUrl = loadBalance.selectServiceAddress(candidate, rpcRequest);
        log.info("Successfully found the service address:[{}]", targetServiceUrl);
        String[] socketAddressArray = targetServiceUrl.split(":");
        String host = socketAddressArray[0];
        int port = Integer.parseInt(socketAddressArray[1]);
        return new InetSocketAddress(host, port);
    }

    private List<String> buildWeightedList(org.apache.curator.framework.CuratorFramework zkClient, String rpcServiceName, List<String> serviceUrlList) {
        List<String> result = new ArrayList<>();
        String base = CuratorUtils.ZK_REGISTER_ROOT_PATH + "/" + rpcServiceName;
        for (String url : serviceUrlList) {
            String path = base + "/" + url;
            byte[] data = CuratorUtils.getNodeData(zkClient, path);
            int weight = 0;
            if (data != null) {
                try {
                    weight = Integer.parseInt(new String(data, java.nio.charset.StandardCharsets.UTF_8));
                } catch (NumberFormatException ignored) {}
            }
            if (weight <= 0) {
                // fallback to single entry when no weight
                result.add(url);
            } else {
                // cap weight to avoid huge lists
                int capped = Math.min(weight, 200);
                for (int i = 0; i < capped; i++) {
                    result.add(url);
                }
            }
        }
        return result;
    }
}

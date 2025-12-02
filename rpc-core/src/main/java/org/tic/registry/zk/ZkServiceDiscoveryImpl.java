package org.tic.registry.zk;

import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.tic.config.ConfigResolver;
import org.tic.enums.LoadBalanceEnum;
import org.tic.enums.RpcErrorMessageEnum;
import org.tic.exception.RpcException;
import org.tic.extension.ExtensionLoader;
import org.tic.factory.SingletonFactory;
import org.tic.loadbalance.LoadBalance;
import org.tic.registry.ServiceDiscovery;
import org.tic.registry.zk.utils.CuratorUtils;
import org.tic.remoting.dto.RpcRequest;
import org.tic.remoting.transport.netty.client.InstanceHealthTracker;
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
    private final InstanceHealthTracker healthTracker;

    public ZkServiceDiscoveryImpl() {
        String strategy = ConfigResolver.getString(org.tic.enums.RpcConfigEnum.LOAD_BALANCE_STRATEGY.getPropertyValue(), LoadBalanceEnum.CONSISTENT_HASH.getName());
        LoadBalance lb;
        try {
            lb = ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension(strategy);
        } catch (Exception e) {
            lb = ExtensionLoader.getExtensionLoader(LoadBalance.class).getExtension(LoadBalanceEnum.CONSISTENT_HASH.getName());
        }
        this.loadBalance = lb;
        this.healthTracker = SingletonFactory.getInstance(InstanceHealthTracker.class);
    }

    @Override
    public InetSocketAddress lookupService(RpcRequest rpcRequest) {
        String rpcServiceName = rpcRequest.getRpcServiceName();
        CuratorFramework zkClient = CuratorUtils.getZkClient();
        List<String> serviceUrlList = CuratorUtils.getChildrenNodes(zkClient, rpcServiceName);
        if (CollectionUtil.isEmpty(serviceUrlList)) {
            throw new RpcException(RpcErrorMessageEnum.SERVICE_CAN_NOT_BE_FOUND, rpcServiceName);
        }
        List<String> healthyList = healthTracker.filterCandidates(serviceUrlList);
        // optional: expand addresses by weight if node carries weight data
        List<String> weightedList = buildWeightedList(zkClient, rpcServiceName, healthyList);
        List<String> candidate = weightedList.isEmpty() ? healthyList : weightedList;
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
            int healthWeight = healthTracker.healthWeight(url);
            if (healthWeight == 0) {
                continue;
            }
            if (weight <= 0) {
                // fallback to single entry when no weight
                result.add(url);
            } else {
                // cap weight to avoid huge lists
                int capped = Math.min((weight * healthWeight) / 100, 200);
                capped = Math.max(capped, 1);
                for (int i = 0; i < capped; i++) {
                    result.add(url);
                }
            }
        }
        return result;
    }
}

package org.tic.loadbalance.loadbalancer;

import org.tic.loadbalance.AbstractLoadBalance;
import org.tic.remoting.dto.RpcRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Weighted round-robin based on frequency of addresses in the provided list.
 */
public class WeightedRoundRobinLoadBalance extends AbstractLoadBalance {

    private final Map<String, Integer> sequences = new ConcurrentHashMap<>();

    @Override
    protected String doSelect(List<String> serviceAddresses, RpcRequest rpcRequest) {
        if (serviceAddresses.isEmpty()) {
            return null;
        }
        Map<String, Integer> weightMap = new LinkedHashMap<>();
        for (String addr : serviceAddresses) {
            weightMap.put(addr, weightMap.getOrDefault(addr, 0) + 1);
        }
        int totalWeight = weightMap.values().stream().mapToInt(Integer::intValue).sum();
        int idx = sequences.merge(rpcRequest.getRpcServiceName(), 1, Integer::sum);
        int offset = idx % totalWeight;
        for (Map.Entry<String, Integer> entry : weightMap.entrySet()) {
            offset -= entry.getValue();
            if (offset < 0) {
                return entry.getKey();
            }
        }
        return serviceAddresses.get(0);
    }
}

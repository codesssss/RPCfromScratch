package org.tic.loadbalance.loadbalancer;

import org.tic.loadbalance.AbstractLoadBalance;
import org.tic.remoting.dto.RpcRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Weighted random load balancer based on frequency of addresses in the list.
 * If the list contains duplicates (e.g., expanded by weight), they will increase probability.
 */
public class WeightedRandomLoadBalance extends AbstractLoadBalance {

    private final Random random = new Random();

    @Override
    protected String doSelect(List<String> serviceAddresses, RpcRequest rpcRequest) {
        if (serviceAddresses.isEmpty()) {
            return null;
        }
        Map<String, Integer> weightMap = new HashMap<>();
        for (String addr : serviceAddresses) {
            weightMap.put(addr, weightMap.getOrDefault(addr, 0) + 1);
        }
        int totalWeight = weightMap.values().stream().mapToInt(Integer::intValue).sum();
        int offset = random.nextInt(totalWeight);
        for (Map.Entry<String, Integer> entry : weightMap.entrySet()) {
            offset -= entry.getValue();
            if (offset < 0) {
                return entry.getKey();
            }
        }
        return serviceAddresses.get(0);
    }
}

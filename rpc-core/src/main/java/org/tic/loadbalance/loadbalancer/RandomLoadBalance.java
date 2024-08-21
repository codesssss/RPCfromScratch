package org.tic.loadbalance.loadbalancer;

import org.tic.loadbalance.AbstractLoadBalance;
import org.tic.remoting.dto.RpcRequest;

import java.util.List;
import java.util.Random;

/**
 * @author codesssss
 * @date 21/8/2024 11:09 am
 */
public class RandomLoadBalance extends AbstractLoadBalance {
    @Override
    protected String doSelect(List<String> serviceAddresses, RpcRequest rpcRequest) {
        Random random = new Random();
        return serviceAddresses.get(random.nextInt(serviceAddresses.size()));
    }
}

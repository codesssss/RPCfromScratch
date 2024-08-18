package org.tic.loadbalance;

import org.tic.extension.SPI;
import org.tic.remoting.dto.RpcRequest;

import java.util.List;

/**
 * @author codesssss
 * @date 27/7/2024 12:40 am
 */
@SPI
public interface LoadBalance {
    /**
     * Choose one from the list of existing service addresses list
     *
     * @param serviceUrlList Service address list
     * @param rpcRequest
     * @return target service address
     */
    String selectServiceAddress(List<String> serviceUrlList, RpcRequest rpcRequest);
}

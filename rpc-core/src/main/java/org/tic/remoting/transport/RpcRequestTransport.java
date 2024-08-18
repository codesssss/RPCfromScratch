package org.tic.remoting.transport;

import org.tic.extension.SPI;
import org.tic.remoting.dto.RpcRequest;

/**
 * @author codesssss
 * @date 18/8/2024 5:53 pm
 */
@SPI
public interface RpcRequestTransport {
    /**
     * send rpc request to server and get result
     *
     * @param rpcRequest message body
     * @return data from server
     */
    Object sendRpcRequest(RpcRequest rpcRequest);
}

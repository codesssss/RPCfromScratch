package org.tic.proxy;

import lombok.extern.slf4j.Slf4j;
import org.tic.config.ConfigResolver;
import org.tic.config.RpcServiceConfig;
import org.tic.enums.RpcConfigEnum;
import org.tic.enums.RpcErrorMessageEnum;
import org.tic.enums.RpcResponseCodeEnum;
import org.tic.exception.RpcException;
import org.tic.remoting.dto.RpcRequest;
import org.tic.remoting.dto.RpcResponse;
import org.tic.remoting.transport.RpcRequestTransport;
import org.tic.remoting.transport.netty.client.NettyRpcClient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author codesssss
 * @date 18/8/2024 11:06 pm
 */
@Slf4j
public class RpcClientProxy implements InvocationHandler {

    private static final String INTERFACE_NAME = "interfaceName";
    /**
     * Default request timeout in milliseconds (30 seconds)
     */
    private static final long DEFAULT_REQUEST_TIMEOUT_MS = 30000L;

    /**
     * Used to send requests to the server.And there are two implementations: socket and netty
     */
    private final RpcRequestTransport rpcRequestTransport;
    private final RpcServiceConfig rpcServiceConfig;
    private final long requestTimeoutMs;

    public RpcClientProxy(RpcRequestTransport rpcRequestTransport, RpcServiceConfig rpcServiceConfig) {
        this.rpcRequestTransport = rpcRequestTransport;
        this.rpcServiceConfig = rpcServiceConfig;
        this.requestTimeoutMs = loadRequestTimeout();
    }


    public RpcClientProxy(RpcRequestTransport rpcRequestTransport) {
        this.rpcRequestTransport = rpcRequestTransport;
        this.rpcServiceConfig = new RpcServiceConfig();
        this.requestTimeoutMs = loadRequestTimeout();
    }

    /**
     * Load request timeout from configuration file
     */
    private long loadRequestTimeout() {
        return ConfigResolver.getLong(RpcConfigEnum.RPC_REQUEST_TIMEOUT_MS.getPropertyValue(), DEFAULT_REQUEST_TIMEOUT_MS);
    }

    /**
     * get the proxy object
     */
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> clazz) {
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class<?>[]{clazz}, this);
    }

    /**
     * This method is actually called when you use a proxy object to call a method.
     * The proxy object is the object you get through the getProxy method.
     */
    @SuppressWarnings("unchecked")
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        log.info("invoked method: [{}]", method.getName());
        RpcRequest rpcRequest = RpcRequest.builder().methodName(method.getName())
                .parameters(args)
                .interfaceName(method.getDeclaringClass().getName())
                .paramTypes(method.getParameterTypes())
                .requestId(UUID.randomUUID().toString())
                .group(rpcServiceConfig.getGroup())
                .version(rpcServiceConfig.getVersion())
                .build();
        RpcResponse<Object> rpcResponse = null;
        if (rpcRequestTransport instanceof NettyRpcClient) {
            CompletableFuture<RpcResponse<Object>> completableFuture = (CompletableFuture<RpcResponse<Object>>) rpcRequestTransport.sendRpcRequest(rpcRequest);
            try {
                // Use timeout to prevent indefinite blocking
                rpcResponse = completableFuture.get(requestTimeoutMs, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                // Cancel the future and clean up
                completableFuture.cancel(true);
                log.error("RPC request timeout after {}ms, requestId: {}, interface: {}, method: {}", 
                        requestTimeoutMs, rpcRequest.getRequestId(), rpcRequest.getInterfaceName(), rpcRequest.getMethodName());
                throw new RpcException(RpcErrorMessageEnum.SERVICE_INVOCATION_FAILURE, 
                        "Request timeout after " + requestTimeoutMs + "ms, " + INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("RPC request interrupted, requestId: {}", rpcRequest.getRequestId());
                throw new RpcException(RpcErrorMessageEnum.SERVICE_INVOCATION_FAILURE, 
                        "Request interrupted, " + INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
            } catch (ExecutionException e) {
                log.error("RPC request execution failed, requestId: {}, cause: {}", rpcRequest.getRequestId(), e.getCause());
                throw new RpcException(RpcErrorMessageEnum.SERVICE_INVOCATION_FAILURE, 
                        "Request execution failed: " + e.getCause().getMessage() + ", " + INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
            }
        }
        this.check(rpcResponse, rpcRequest);
        return rpcResponse.getData();
    }

    private void check(RpcResponse<Object> rpcResponse, RpcRequest rpcRequest) {
        if (rpcResponse == null) {
            throw new RpcException(RpcErrorMessageEnum.SERVICE_INVOCATION_FAILURE, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
        }

        if (!rpcRequest.getRequestId().equals(rpcResponse.getRequestId())) {
            throw new RpcException(RpcErrorMessageEnum.REQUEST_NOT_MATCH_RESPONSE, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
        }

        if (rpcResponse.getCode() == null || !rpcResponse.getCode().equals(RpcResponseCodeEnum.SUCCESS.getCode())) {
            throw new RpcException(RpcErrorMessageEnum.SERVICE_INVOCATION_FAILURE, INTERFACE_NAME + ":" + rpcRequest.getInterfaceName());
        }
    }
}

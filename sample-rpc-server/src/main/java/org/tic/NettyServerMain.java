package org.tic;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.tic.annotation.RpcScan;
import org.tic.config.RpcServiceConfig;
import org.tic.remoting.transport.netty.server.NettyRpcServer;
import org.tic.serviceimpl.HelloServiceImpl2;

/**
 * @author codesssss
 * @date 19/8/2024 10:29 am
 */
@RpcScan(basePackage = {"org.tic"})
public class NettyServerMain {
    public static void main(String[] args) {
        // Register service via annotation
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(NettyServerMain.class);
        NettyRpcServer nettyRpcServer = (NettyRpcServer) applicationContext.getBean("nettyRpcServer");
        // Register service manually
        HelloService helloService2 = new HelloServiceImpl2();
        RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
                .group("test2").version("version2").service(helloService2).build();
        nettyRpcServer.registerService(rpcServiceConfig);
        nettyRpcServer.start();
    }
}

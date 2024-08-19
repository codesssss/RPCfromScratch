package org.tic;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.tic.annotation.RpcScan;

/**
 * @author codesssss
 * @date 19/8/2024 10:21 am
 */
@RpcScan(basePackage = {"org.tic"})
public class NettyClientMain {
    public static void main(String[] args) throws InterruptedException {
        AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext(NettyClientMain.class);
        HelloController helloController = (HelloController) applicationContext.getBean("helloController");
        helloController.test();
    }
}
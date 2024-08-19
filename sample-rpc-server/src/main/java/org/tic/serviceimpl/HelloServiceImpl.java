package org.tic.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.tic.Hello;
import org.tic.HelloService;
import org.tic.annotation.RpcService;

/**
 * @author codesssss
 * @date 19/8/2024 10:27 am
 */
@Slf4j
@RpcService(group = "test1", version = "version1")
public class HelloServiceImpl implements HelloService {

    static {
        System.out.println("HelloServiceImpl被创建");
    }

    @Override
    public String hello(Hello hello) {
        log.info("HelloServiceImpl收到: {}.", hello.getMessage());
        String result = "Hello description is " + hello.getDescription();
        log.info("HelloServiceImpl返回: {}.", result);
        return result;
    }
}

package org.tic.serviceimpl;

import lombok.extern.slf4j.Slf4j;
import org.tic.Hello;
import org.tic.HelloService;

/**
 * @author codesssss
 * @date 19/8/2024 10:29 am
 */
@Slf4j
public class HelloServiceImpl2 implements HelloService {

    static {
        System.out.println("HelloServiceImpl2被创建");
    }

    @Override
    public String hello(Hello hello) {
        log.info("HelloServiceImpl2收到: {}.", hello.getMessage());
        String result = "Hello description is " + hello.getDescription();
        log.info("HelloServiceImpl2返回: {}.", result);
        return result;
    }
}

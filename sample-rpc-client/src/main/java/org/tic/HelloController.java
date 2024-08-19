package org.tic;

import org.springframework.stereotype.Component;
import org.tic.annotation.RpcReference;

/**
 * @author codesssss
 * @date 19/8/2024 10:24 am
 */
@Component
public class HelloController {

    @RpcReference(version = "version1", group = "test1")
    private HelloService helloService;

    public void test() throws InterruptedException {
        String hello = this.helloService.hello(new Hello("111", "222"));
        // If you want to use assert, you need to add the following parameter to VM options: -ea
        assert "Hello description is 222".equals(hello);
        Thread.sleep(12000);
        for (int i = 0; i < 10; i++) {
            System.out.println(helloService.hello(new Hello("111", "222")));
        }
    }
}

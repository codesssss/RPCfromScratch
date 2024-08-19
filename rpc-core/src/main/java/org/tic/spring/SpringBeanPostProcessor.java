package org.tic.spring;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.tic.annotation.RpcReference;
import org.tic.annotation.RpcService;
import org.tic.config.RpcServiceConfig;
import org.tic.enums.RpcRequestTransportEnum;
import org.tic.extension.ExtensionLoader;
import org.tic.factory.SingletonFactory;
import org.tic.provider.ServiceProvider;
import org.tic.provider.impl.ZkServiceProviderImpl;
import org.tic.proxy.RpcClientProxy;
import org.tic.remoting.transport.RpcRequestTransport;

import java.lang.reflect.Field;

/**
 * @author codesssss
 * @date 18/8/2024 4:42 pm
 */
@Slf4j
@Component
public class SpringBeanPostProcessor implements BeanPostProcessor {

    // ServiceProvider used to publish RPC services
    private final ServiceProvider serviceProvider;
    // RpcRequestTransport used to create client proxies
    private final RpcRequestTransport rpcClient;

    // Constructor initializes ServiceProvider and RpcRequestTransport
    public SpringBeanPostProcessor() {
        this.serviceProvider = SingletonFactory.getInstance(ZkServiceProviderImpl.class);
        this.rpcClient = ExtensionLoader.getExtensionLoader(RpcRequestTransport.class)
                .getExtension(RpcRequestTransportEnum.NETTY.getName());
    }

    @SneakyThrows
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        // Check if the bean is annotated with @RpcService
        if (bean.getClass().isAnnotationPresent(RpcService.class)) {
            log.info("[{}] is annotated with  [{}]", bean.getClass().getName(), RpcService.class.getCanonicalName());
            // Get the @RpcService annotation
            RpcService rpcService = bean.getClass().getAnnotation(RpcService.class);
            // Build RpcServiceConfig using the annotation's attributes
            RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
                    .group(rpcService.group())
                    .version(rpcService.version())
                    .service(bean).build();
            // Publish the service using the ServiceProvider
            serviceProvider.publishService(rpcServiceConfig);
        }
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // Get the class of the bean
        Class<?> targetClass = bean.getClass();
        // Get all declared fields of the bean
        Field[] declaredFields = targetClass.getDeclaredFields();
        for (Field declaredField : declaredFields) {
            // Check if the field is annotated with @RpcReference
            RpcReference rpcReference = declaredField.getAnnotation(RpcReference.class);
            if (rpcReference != null) {
                // Build RpcServiceConfig using the annotation's attributes
                RpcServiceConfig rpcServiceConfig = RpcServiceConfig.builder()
                        .group(rpcReference.group())
                        .version(rpcReference.version()).build();
                // Create a client proxy for the field's type
                RpcClientProxy rpcClientProxy = new RpcClientProxy(rpcClient, rpcServiceConfig);
                Object clientProxy = rpcClientProxy.getProxy(declaredField.getType());
                declaredField.setAccessible(true);
                try {
                    // Inject the proxy into the field
                    declaredField.set(bean, clientProxy);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
        return bean;
    }
}

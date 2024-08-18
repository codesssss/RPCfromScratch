package org.tic.annotation;

import org.springframework.context.annotation.Import;
import org.tic.spring.CustomScannerRegistrar;

import java.lang.annotation.*;

/**
 * @author codesssss
 * @date 18/8/2024 3:59 pm
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Import(CustomScannerRegistrar.class)
@Documented
public @interface RpcScan {

    String[] basePackage();

}
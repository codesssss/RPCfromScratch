package org.tic.annotation;

import java.lang.annotation.*;

/**
 * @author codesssss
 * @date 18/8/2024 3:59 pm
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Inherited
public @interface RpcService {

    /**
     * Service version, default value is empty string
     */
    String version() default "";

    /**
     * Service group, default value is empty string
     */
    String group() default "";

}
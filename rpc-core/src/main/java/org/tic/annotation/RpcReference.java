package org.tic.annotation;

import java.lang.annotation.*;

/**
 * @author codesssss
 * @date 18/8/2024 4:00 pm
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD})
@Inherited
public @interface RpcReference {

    /**
     * Service version, default value is empty string
     */
    String version() default "";

    /**
     * Service group, default value is empty string
     */
    String group() default "";

}

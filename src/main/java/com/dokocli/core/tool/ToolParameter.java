package com.dokocli.core.tool;

import java.lang.annotation.*;

/**
 * 工具参数注解
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolParameter {
    /**
     * 参数描述
     */
    String description();

    /**
     * 是否必需
     */
    boolean required() default true;
}

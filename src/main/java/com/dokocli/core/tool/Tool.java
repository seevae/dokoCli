package com.dokocli.core.tool;

import java.lang.annotation.*;

/**
 * 工具注解，标记可被模型调用的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Tool {
    /**
     * 工具名称
     */
    String name();

    /**
     * 工具描述
     */
    String description();
}

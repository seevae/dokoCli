package com.joyeffi.service.demo;

/**
 * 示例工具类 - 用于演示新功能
 * Created on 2025-04-14
 */
public class DemoHelper {

    /**
     * 获取项目版本信息
     * @return 版本号
     */
    public static String getVersion() {
        return "1.0.0-demo";
    }

    /**
     * 简单的问候方法
     * @param name 用户名
     * @return 问候语
     */
    public static String sayHello(String name) {
        return "Hello, " + (name != null ? name : "World") + "!";
    }

    /**
     * 计算平方
     * @param number 数字
     * @return 平方结果
     */
    public static double square(double number) {
        return number * number;
    }
}

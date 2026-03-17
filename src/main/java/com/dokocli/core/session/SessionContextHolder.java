package com.dokocli.core.session;

/**
 * 当前请求对应的会话上下文（ThreadLocal），供在工具执行等链路中获取 Session。
 * 由 AgentService 在进入 agent 循环前设置，退出后清除。
 */
public final class SessionContextHolder {

    private static final ThreadLocal<Session> HOLDER = new ThreadLocal<>();

    public static void setSession(Session session) {
        HOLDER.set(session);
    }

    public static Session getSession() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    private SessionContextHolder() {}
}

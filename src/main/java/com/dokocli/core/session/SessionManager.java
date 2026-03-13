package com.dokocli.core.session;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器
 */
@Component
public class SessionManager {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public Session createSession() {
        Session session = new Session();
        sessions.put(session.getId(), session);
        return session;
    }

    public Session getSession(String id) {
        return sessions.get(id);
    }

    public void removeSession(String id) {
        sessions.remove(id);
    }

    public Session getOrCreate(String id) {
        return sessions.computeIfAbsent(id, k -> new Session());
    }
}

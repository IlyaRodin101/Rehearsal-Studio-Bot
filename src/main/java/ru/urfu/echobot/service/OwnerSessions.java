package ru.urfu.echobot.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Хранит диалоги владельцев по их Telegram ID.
final class OwnerSessions {
    /// Сессии владельцев.
    private final Map<Long, OwnerSession> sessions = new ConcurrentHashMap<>();

    /// Возвращает сессию владельца, создавая её при первом обращении.
    ///
    /// @param userId идентификатор владельца
    /// @return сессия владельца
    OwnerSession get(long userId) {
        return sessions.computeIfAbsent(userId, ignored -> new OwnerSession());
    }

    /// Возвращает существующую сессию или null, если владелец ещё не начинал настройку.
    ///
    /// @param userId идентификатор владельца
    /// @return сессия или null
    OwnerSession existing(long userId) {
        return sessions.get(userId);
    }

    /// Прерывает незавершённую настройку владельца.
    ///
    /// @param userId идентификатор владельца
    void cancel(long userId) {
        OwnerSession session = sessions.get(userId);
        if (session != null) {
            session.reset();
        }
    }
}

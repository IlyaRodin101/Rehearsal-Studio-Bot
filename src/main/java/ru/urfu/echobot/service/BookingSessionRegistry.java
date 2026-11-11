package ru.urfu.echobot.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Хранит диалоги бронирования всех пользователей и удаляет давно неактивные.
final class BookingSessionRegistry {
    /// Через сколько минут неактивности диалог сбрасывается.
    private static final long SESSION_TIMEOUT_MINUTES = 5;

    /// Состояния диалогов, индексированные по идентификатору пользователя.
    private final Map<Long, BookingFlow> flows = new ConcurrentHashMap<>();

    /// Возвращает диалог пользователя, создавая его при первом обращении.
    ///
    /// @param userId идентификатор пользователя
    /// @return состояние диалога
    BookingFlow flow(long userId) {
        BookingFlow flow = flows.computeIfAbsent(userId, ignored -> new BookingFlow());
        flow.updateLastActivity();
        return flow;
    }

    /// Удаляет диалоги, в которых не было активности дольше тайм-аута.
    void removeExpired() {
        Iterator<Map.Entry<Long, BookingFlow>> iterator = flows.entrySet().iterator();
        LocalDateTime currentTime = LocalDateTime.now();
        while (iterator.hasNext()) {
            BookingFlow flow = iterator.next().getValue();
            long inactiveMinutes = ChronoUnit.MINUTES.between(flow.getLastActivityAt(), currentTime);
            if (inactiveMinutes >= SESSION_TIMEOUT_MINUTES) {
                iterator.remove();
            }
        }
    }
}

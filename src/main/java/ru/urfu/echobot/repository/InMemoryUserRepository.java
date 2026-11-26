package ru.urfu.echobot.repository;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import ru.urfu.echobot.model.TelegramUser;

/// Хранит известных пользователей в памяти. Используется, когда постоянное хранилище не передано.
public final class InMemoryUserRepository implements UserRepository {
    /// Пользователи по их Telegram ID.
    private final Map<Long, TelegramUser> users = new ConcurrentHashMap<>();

    /// Запоминает Telegram ID и имя пользователя.
    ///
    /// @param userId   Telegram ID пользователя
    /// @param username имя пользователя Telegram
    @Override
    public void remember(long userId, String username) {
        String normalized = normalize(username);
        if (!normalized.isEmpty()) {
            users.put(userId, new TelegramUser(userId, normalized));
        }
    }

    /// Ищет пользователя по имени Telegram без учёта регистра.
    ///
    /// @param username имя пользователя
    /// @return найденный пользователь или пустой результат
    @Override
    public Optional<TelegramUser> findByUsername(String username) {
        String normalized = normalize(username);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return users.values().stream()
                .filter(user -> user.username().equals(normalized))
                .findFirst();
    }

    /// Приводит имя пользователя к единому виду.
    ///
    /// @param username имя пользователя Telegram
    /// @return имя без символа `@` в нижнем регистре
    private String normalize(String username) {
        String value = username == null ? "" : username.trim();
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        return value.toLowerCase(Locale.ROOT);
    }
}

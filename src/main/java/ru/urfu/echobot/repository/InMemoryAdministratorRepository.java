package ru.urfu.echobot.repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ru.urfu.echobot.model.TelegramUser;

/// Хранит администраторов в памяти. Используется, когда постоянное хранилище не передано.
public final class InMemoryAdministratorRepository implements AdministratorRepository {
    /// Администраторы по их Telegram ID.
    private final Map<Long, TelegramUser> administrators = new ConcurrentHashMap<>();

    /// Возвращает всех администраторов.
    ///
    /// @return администраторы, отсортированные по имени пользователя
    @Override
    public List<TelegramUser> findAll() {
        return administrators.values().stream()
                .sorted((first, second) -> first.username().compareTo(second.username()))
                .toList();
    }

    /// Проверяет, является ли пользователь администратором.
    ///
    /// @param userId Telegram ID пользователя
    /// @return true, если пользователь есть в списке администраторов
    @Override
    public boolean isAdministrator(long userId) {
        return administrators.containsKey(userId);
    }

    /// Добавляет пользователя в администраторы.
    ///
    /// @param user пользователь, который станет администратором
    @Override
    public void add(TelegramUser user) {
        administrators.put(user.id(), user);
    }

    /// Удаляет пользователя из администраторов.
    ///
    /// @param userId Telegram ID пользователя
    @Override
    public void remove(long userId) {
        administrators.remove(userId);
    }
}

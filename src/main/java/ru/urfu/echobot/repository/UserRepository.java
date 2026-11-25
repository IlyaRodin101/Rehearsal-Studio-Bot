package ru.urfu.echobot.repository;

import java.util.Optional;
import ru.urfu.echobot.model.TelegramUser;

/// Хранилище пользователей, которые хотя бы раз писали боту.
public interface UserRepository {

    /// Запоминает Telegram ID и имя пользователя, обновляя прежнее имя при необходимости.
    ///
    /// @param userId   Telegram ID пользователя
    /// @param username имя пользователя Telegram
    void remember(long userId, String username);

    /// Ищет пользователя по имени Telegram без учёта регистра и символа `@`.
    ///
    /// @param username имя пользователя
    /// @return найденный пользователь или пустой результат
    Optional<TelegramUser> findByUsername(String username);
}

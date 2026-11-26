package ru.urfu.echobot.repository;

import java.util.List;
import ru.urfu.echobot.model.TelegramUser;

/// Хранилище администраторов баз.
public interface AdministratorRepository {

    /// Возвращает всех администраторов.
    ///
    /// @return администраторы, отсортированные по имени пользователя
    List<TelegramUser> findAll();

    /// Проверяет, является ли пользователь администратором.
    ///
    /// @param userId Telegram ID пользователя
    /// @return true, если пользователь есть в списке администраторов
    boolean isAdministrator(long userId);

    /// Добавляет пользователя в администраторы. Повторный вызов ничего не меняет.
    ///
    /// @param user пользователь, который станет администратором
    void add(TelegramUser user);

    /// Удаляет пользователя из администраторов.
    ///
    /// @param userId Telegram ID пользователя
    void remove(long userId);
}

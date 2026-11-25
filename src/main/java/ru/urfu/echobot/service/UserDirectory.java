package ru.urfu.echobot.service;

import java.util.List;
import java.util.Optional;
import ru.urfu.echobot.model.TelegramUser;
import ru.urfu.echobot.repository.AdministratorRepository;
import ru.urfu.echobot.repository.InMemoryAdministratorRepository;
import ru.urfu.echobot.repository.InMemoryUserRepository;
import ru.urfu.echobot.repository.UserRepository;

/// Объединяет поиск пользователей бота и список администраторов.
public final class UserDirectory {
    /// Хранилище пользователей, которые писали боту.
    private final UserRepository users;
    /// Хранилище администраторов.
    private final AdministratorRepository administrators;

    /// Создаёт справочник пользователей и администраторов.
    ///
    /// @param users          хранилище известных пользователей
    /// @param administrators хранилище администраторов
    public UserDirectory(UserRepository users, AdministratorRepository administrators) {
        this.users = users;
        this.administrators = administrators;
    }

    /// Создаёт справочник с хранилищем в памяти.
    ///
    /// @return справочник без постоянного хранилища
    public static UserDirectory inMemory() {
        return new UserDirectory(new InMemoryUserRepository(), new InMemoryAdministratorRepository());
    }

    /// Запоминает пользователя, написавшего боту.
    ///
    /// @param userId   Telegram ID пользователя
    /// @param username имя пользователя Telegram
    public void remember(long userId, String username) {
        users.remember(userId, username);
    }

    /// Ищет пользователя по имени Telegram.
    ///
    /// @param username имя пользователя
    /// @return найденный пользователь или пустой результат
    public Optional<TelegramUser> findByUsername(String username) {
        return users.findByUsername(username);
    }

    /// Возвращает администраторов.
    ///
    /// @return администраторы, отсортированные по имени пользователя
    public List<TelegramUser> administrators() {
        return administrators.findAll();
    }

    /// Проверяет, является ли пользователь администратором.
    ///
    /// @param userId Telegram ID пользователя
    /// @return true, если пользователь администратор
    public boolean isAdministrator(long userId) {
        return administrators.isAdministrator(userId);
    }

    /// Добавляет администратора.
    ///
    /// @param user пользователь, который станет администратором
    public void addAdministrator(TelegramUser user) {
        administrators.add(user);
    }

    /// Удаляет администратора.
    ///
    /// @param userId Telegram ID пользователя
    public void removeAdministrator(long userId) {
        administrators.remove(userId);
    }
}

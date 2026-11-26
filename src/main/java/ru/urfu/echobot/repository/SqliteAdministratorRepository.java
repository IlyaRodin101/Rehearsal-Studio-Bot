package ru.urfu.echobot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.model.TelegramUser;

/// Хранит список администраторов в SQLite.
public final class SqliteAdministratorRepository implements AdministratorRepository {
    /// Позиция Telegram ID в запросах администраторов.
    private static final int USER_ID_PARAMETER = 1;

    /// База данных, через которую выполняются запросы.
    private final SqliteDatabase database;

    /// Создаёт хранилище администраторов.
    ///
    /// @param database база данных SQLite
    public SqliteAdministratorRepository(SqliteDatabase database) {
        this.database = database;
    }

    /// Возвращает всех администраторов.
    ///
    /// @return администраторы, отсортированные по имени пользователя
    @Override
    public List<TelegramUser> findAll() {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT u.id,u.username FROM administrators a "
                             + "JOIN known_users u ON u.id=a.user_id ORDER BY u.username");
             ResultSet rows = statement.executeQuery()) {
            List<TelegramUser> administrators = new ArrayList<>();
            while (rows.next()) {
                administrators.add(new TelegramUser(rows.getLong("id"), rows.getString("username")));
            }
            return administrators;
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось прочитать администраторов", exception);
        }
    }

    /// Проверяет, является ли пользователь администратором.
    ///
    /// @param userId Telegram ID пользователя
    /// @return true, если пользователь есть в списке администраторов
    @Override
    public boolean isAdministrator(long userId) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM administrators WHERE user_id=? LIMIT 1")) {
            statement.setLong(USER_ID_PARAMETER, userId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось проверить администратора", exception);
        }
    }

    /// Добавляет пользователя в администраторы.
    ///
    /// @param user пользователь, который станет администратором
    @Override
    public void add(TelegramUser user) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO administrators(user_id) VALUES(?)")) {
            statement.setLong(USER_ID_PARAMETER, user.id());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось добавить администратора", exception);
        }
    }

    /// Удаляет пользователя из администраторов.
    ///
    /// @param userId Telegram ID пользователя
    @Override
    public void remove(long userId) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM administrators WHERE user_id=?")) {
            statement.setLong(USER_ID_PARAMETER, userId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось удалить администратора", exception);
        }
    }
}

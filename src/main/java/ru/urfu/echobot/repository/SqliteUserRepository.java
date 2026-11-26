package ru.urfu.echobot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import ru.urfu.echobot.model.TelegramUser;

/// Хранит пользователей, которые писали боту, в SQLite.
public final class SqliteUserRepository implements UserRepository {
    /// Первая позиция параметра в запросах пользователей.
    private static final int FIRST_PARAMETER = 1;
    /// Вторая позиция параметра в запросах пользователей.
    private static final int SECOND_PARAMETER = 2;

    /// База данных, через которую выполняются запросы.
    private final SqliteDatabase database;

    /// Создаёт хранилище пользователей.
    ///
    /// @param database база данных SQLite
    public SqliteUserRepository(SqliteDatabase database) {
        this.database = database;
    }

    /// Запоминает Telegram ID и имя пользователя.
    ///
    /// @param userId   Telegram ID пользователя
    /// @param username имя пользователя Telegram
    @Override
    public void remember(long userId, String username) {
        String normalized = normalize(username);
        if (normalized.isEmpty()) {
            return;
        }
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO known_users(id,username) VALUES(?,?) "
                             + "ON CONFLICT(id) DO UPDATE SET username=excluded.username")) {
            statement.setLong(FIRST_PARAMETER, userId);
            statement.setString(SECOND_PARAMETER, normalized);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось сохранить пользователя", exception);
        }
    }

    /// Ищет пользователя по имени Telegram.
    ///
    /// @param username имя пользователя
    /// @return найденный пользователь или пустой результат
    @Override
    public Optional<TelegramUser> findByUsername(String username) {
        String normalized = normalize(username);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id,username FROM known_users WHERE username=? COLLATE NOCASE "
                             + "ORDER BY id DESC LIMIT 1")) {
            statement.setString(FIRST_PARAMETER, normalized);
            try (ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    return Optional.of(new TelegramUser(rows.getLong("id"), rows.getString("username")));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось найти пользователя", exception);
        }
    }

    /// Приводит имя пользователя к виду, в котором оно хранится.
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

package ru.urfu.echobot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/// Хранит подписки клиентов на «горячие» слоты баз в SQLite.
public final class SqliteSubscriptionRepository implements SubscriptionRepository {
    /// Позиция первого параметра в запросах подписок.
    private static final int FIRST_PARAMETER = 1;
    /// Позиция второго параметра в запросах подписок.
    private static final int SECOND_PARAMETER = 2;

    /// База данных, через которую выполняются запросы.
    private final SqliteDatabase database;

    /// Создаёт хранилище подписок.
    ///
    /// @param database база данных SQLite
    public SqliteSubscriptionRepository(SqliteDatabase database) {
        this.database = database;
    }

    /// Возвращает базы, на которые подписан клиент.
    ///
    /// @param userId идентификатор клиента
    /// @return идентификаторы баз в порядке возрастания
    @Override
    public List<Long> findBaseIdsByUser(long userId) {
        return query("SELECT base_id FROM subscriptions WHERE user_id=? ORDER BY base_id", userId);
    }

    /// Возвращает подписчиков базы.
    ///
    /// @param baseId идентификатор базы
    /// @return идентификаторы клиентов в порядке возрастания
    @Override
    public List<Long> findUserIdsByBase(long baseId) {
        return query("SELECT user_id FROM subscriptions WHERE base_id=? ORDER BY user_id", baseId);
    }

    /// Проверяет, подписан ли клиент на базу.
    ///
    /// @param userId идентификатор клиента
    /// @param baseId идентификатор базы
    /// @return true, если подписка существует
    @Override
    public boolean isSubscribed(long userId, long baseId) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM subscriptions WHERE user_id=? AND base_id=? LIMIT 1")) {
            statement.setLong(FIRST_PARAMETER, userId);
            statement.setLong(SECOND_PARAMETER, baseId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось проверить подписку", exception);
        }
    }

    /// Добавляет подписку клиента на базу. Повторная подписка не создаёт дубль.
    ///
    /// @param userId идентификатор клиента
    /// @param baseId идентификатор базы
    @Override
    public void subscribe(long userId, long baseId) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO subscriptions(user_id,base_id) VALUES(?,?)")) {
            statement.setLong(FIRST_PARAMETER, userId);
            statement.setLong(SECOND_PARAMETER, baseId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось сохранить подписку", exception);
        }
    }

    /// Удаляет подписку клиента на базу.
    ///
    /// @param userId идентификатор клиента
    /// @param baseId идентификатор базы
    @Override
    public void unsubscribe(long userId, long baseId) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM subscriptions WHERE user_id=? AND base_id=?")) {
            statement.setLong(FIRST_PARAMETER, userId);
            statement.setLong(SECOND_PARAMETER, baseId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось удалить подписку", exception);
        }
    }

    /// Читает идентификаторы по запросу с одним параметром.
    ///
    /// @param sql       запрос выборки идентификаторов
    /// @param parameter значение параметра
    /// @return найденные идентификаторы
    private List<Long> query(String sql, long parameter) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(FIRST_PARAMETER, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (rows.next()) {
                    ids.add(rows.getLong(1));
                }
                return ids;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось прочитать подписки", exception);
        }
    }
}

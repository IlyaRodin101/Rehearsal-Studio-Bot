package ru.urfu.echobot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ru.urfu.echobot.model.Base;

/// Читает базы вместе с их графиком из SQLite.
final class SqliteBaseReader {
    /// Позиция единственного параметра в запросах выборки.
    private static final int SINGLE_PARAMETER = 1;

    /// База данных, через которую выполняются запросы.
    private final SqliteDatabase database;
    /// Хранилище графика баз.
    private final SqliteBaseSchedule schedule;

    /// Создаёт читатель баз.
    ///
    /// @param database база данных SQLite
    /// @param schedule хранилище графика баз
    SqliteBaseReader(SqliteDatabase database, SqliteBaseSchedule schedule) {
        this.database = database;
        this.schedule = schedule;
    }

    /// Возвращает все базы.
    ///
    /// @return список баз в порядке добавления
    List<Base> findAll() {
        return query("SELECT * FROM bases ORDER BY id", null);
    }

    /// Ищет базу по идентификатору.
    ///
    /// @param id идентификатор базы
    /// @return база или пустой результат, если такой нет
    Optional<Base> findById(long id) {
        return query("SELECT * FROM bases WHERE id = ?", id).stream().findFirst();
    }

    /// Возвращает базы владельца.
    ///
    /// @param ownerId идентификатор владельца
    /// @return базы владельца в порядке добавления
    List<Base> findByOwnerId(long ownerId) {
        return query("SELECT * FROM bases WHERE owner_id = ? ORDER BY id", ownerId);
    }

    /// Читает базы по запросу с необязательным параметром.
    ///
    /// @param sql       запрос выборки баз
    /// @param parameter параметр запроса или null, если параметра нет
    /// @return найденные базы
    private List<Base> query(String sql, Long parameter) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) {
                statement.setLong(SINGLE_PARAMETER, parameter);
            }
            try (ResultSet rows = statement.executeQuery()) {
                List<Base> bases = new ArrayList<>();
                while (rows.next()) {
                    bases.add(readBase(connection, rows));
                }
                return bases;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось прочитать базы", exception);
        }
    }

    /// Создаёт базу из текущей строки результата.
    ///
    /// @param connection соединение с базой данных
    /// @param rows       строка результата запроса
    /// @return прочитанная база
    /// @throws SQLException если график базы не удалось прочитать
    private Base readBase(Connection connection, ResultSet rows) throws SQLException {
        long id = rows.getLong("id");
        return new Base(id, rows.getLong("owner_id"), rows.getString("name"),
                rows.getString("address"), rows.getString("timezone"), rows.getInt("booking_lead_hours"),
                rows.getString("equipment"), schedule.load(connection, id));
    }
}

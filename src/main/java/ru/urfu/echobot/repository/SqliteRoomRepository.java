package ru.urfu.echobot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ru.urfu.echobot.model.Room;

/// Хранит залы в SQLite. Удалённые залы остаются неактивными, чтобы сохранить историю броней.
public final class SqliteRoomRepository implements RoomRepository {
    /// Позиция базы в запросах залов.
    private static final int BASE_ID_PARAMETER = 1;
    /// Позиция названия при вставке зала.
    private static final int INSERT_ROOM_NAME_PARAMETER = 2;
    /// Позиция названия при обновлении зала.
    private static final int UPDATE_ROOM_NAME_PARAMETER = 1;
    /// Позиция флага комментария при вставке зала.
    private static final int INSERT_COMMENT_REQUIRED_PARAMETER = 3;
    /// Позиция флага комментария при обновлении зала.
    private static final int UPDATE_COMMENT_REQUIRED_PARAMETER = 2;
    /// Позиция идентификатора зала при обновлении.
    private static final int UPDATE_ROOM_ID_PARAMETER = 3;
    /// Позиция единственного параметра в простых запросах.
    private static final int SINGLE_PARAMETER = 1;

    /// База данных, через которую выполняются запросы.
    private final SqliteDatabase database;

    /// Создаёт хранилище залов.
    ///
    /// @param database база данных SQLite
    public SqliteRoomRepository(SqliteDatabase database) {
        this.database = database;
    }

    /// Возвращает активные залы базы.
    ///
    /// @param baseId идентификатор базы
    /// @return активные залы в порядке добавления
    @Override
    public List<Room> findByBaseId(long baseId) {
        return query("SELECT * FROM rooms WHERE base_id=? AND active=1 ORDER BY id", baseId);
    }

    /// Ищет зал по идентификатору, включая неактивные.
    ///
    /// @param roomId идентификатор зала
    /// @return зал или пустой результат, если такого нет
    @Override
    public Optional<Room> findById(long roomId) {
        return query("SELECT * FROM rooms WHERE id=?", roomId).stream().findFirst();
    }

    /// Добавляет активный зал в базу.
    ///
    /// @param room сохраняемый зал
    /// @return сохранённый зал с присвоенным идентификатором
    @Override
    public Room save(Room room) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO rooms(base_id,name,comment_required,active) VALUES(?,?,?,1)",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(BASE_ID_PARAMETER, room.baseId());
            statement.setString(INSERT_ROOM_NAME_PARAMETER, room.name());
            statement.setInt(INSERT_COMMENT_REQUIRED_PARAMETER, room.commentRequired() ? 1 : 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite не вернул идентификатор нового зала");
                }
                return new Room(keys.getLong(SINGLE_PARAMETER), room.baseId(), room.name(),
                        room.commentRequired());
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось сохранить зал", exception);
        }
    }

    /// Обновляет название и флаг комментария активного зала.
    ///
    /// @param room зал с новыми значениями
    @Override
    public void update(Room room) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE rooms SET name=?,comment_required=? WHERE id=? AND active=1")) {
            statement.setString(UPDATE_ROOM_NAME_PARAMETER, room.name());
            statement.setInt(UPDATE_COMMENT_REQUIRED_PARAMETER, room.commentRequired() ? 1 : 0);
            statement.setLong(UPDATE_ROOM_ID_PARAMETER, room.id());
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Активный зал не найден: " + room.id());
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось обновить зал", exception);
        }
    }

    /// Помечает зал неактивным, сохраняя его историю.
    ///
    /// @param roomId идентификатор зала
    @Override
    public void deactivate(long roomId) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement("UPDATE rooms SET active=0 WHERE id=?")) {
            statement.setLong(SINGLE_PARAMETER, roomId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Зал не найден: " + roomId);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось удалить зал", exception);
        }
    }

    /// Выполняет запрос выборки залов с одним параметром.
    ///
    /// @param sql       запрос выборки
    /// @param parameter параметр запроса
    /// @return найденные залы
    private List<Room> query(String sql, long parameter) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(SINGLE_PARAMETER, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                List<Room> rooms = new ArrayList<>();
                while (rows.next()) {
                    rooms.add(new Room(rows.getLong("id"), rows.getLong("base_id"), rows.getString("name"),
                            rows.getInt("comment_required") != 0, rows.getInt("active") != 0));
                }
                return rooms;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось прочитать залы", exception);
        }
    }
}

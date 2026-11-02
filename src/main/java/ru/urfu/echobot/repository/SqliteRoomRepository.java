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

/// SQLite repository for rooms. Deleted rooms are retained as inactive rows for booking history.
public final class SqliteRoomRepository implements RoomRepository {
    private static final int BASE_ID_PARAMETER = 1;
    private static final int INSERT_ROOM_NAME_PARAMETER = 2;
    private static final int UPDATE_ROOM_NAME_PARAMETER = 1;
    private static final int INSERT_COMMENT_REQUIRED_PARAMETER = 3;
    private static final int UPDATE_COMMENT_REQUIRED_PARAMETER = 2;
    private static final int ROOM_ID_PARAMETER = 3;
    private final SqliteDatabase database;

    public SqliteRoomRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public List<Room> findByBaseId(long baseId) {
        return query("SELECT * FROM rooms WHERE base_id=? AND active=1 ORDER BY id", baseId);
    }

    @Override
    public Optional<Room> findById(long roomId) {
        return query("SELECT * FROM rooms WHERE id=?", roomId).stream().findFirst();
    }

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
                    throw new SQLException("SQLite did not return an ID for the new room");
                }
                return new Room(keys.getLong(1), room.baseId(), room.name(), room.commentRequired());
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not save room", exception);
        }
    }

    @Override
    public void update(Room room) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE rooms SET name=?,comment_required=? WHERE id=? AND active=1")) {
            statement.setString(UPDATE_ROOM_NAME_PARAMETER, room.name());
            statement.setInt(UPDATE_COMMENT_REQUIRED_PARAMETER, room.commentRequired() ? 1 : 0);
            statement.setLong(ROOM_ID_PARAMETER, room.id());
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Active room does not exist: " + room.id());
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not update room", exception);
        }
    }

    @Override
    public void deactivate(long roomId) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement("UPDATE rooms SET active=0 WHERE id=?")) {
            statement.setLong(BASE_ID_PARAMETER, roomId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("Room does not exist: " + roomId);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not delete room", exception);
        }
    }

    private List<Room> query(String sql, long parameter) {
        try (Connection connection = database.open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(BASE_ID_PARAMETER, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                List<Room> rooms = new ArrayList<>();
                while (rows.next()) {
                    rooms.add(new Room(rows.getLong("id"), rows.getLong("base_id"), rows.getString("name"),
                            rows.getInt("comment_required") != 0, rows.getInt("active") != 0));
                }
                return rooms;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read rooms", exception);
        }
    }
}

package ru.urfu.echobot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.model.Booking;

/// Хранит бронирования в SQLite. Проверка пересечения и вставка выполняются в одной транзакции.
public final class SqliteBookingRepository implements BookingRepository {
    /// Позиция зала в запросе вставки брони.
    private static final int INSERT_ROOM_PARAMETER = 1;
    /// Позиция пользователя в запросе вставки брони.
    private static final int INSERT_USER_PARAMETER = 2;
    /// Позиция времени начала в запросе вставки брони.
    private static final int INSERT_START_PARAMETER = 3;
    /// Позиция времени окончания в запросе вставки брони.
    private static final int INSERT_END_PARAMETER = 4;
    /// Позиция длительности в запросе вставки брони.
    private static final int INSERT_HOURS_PARAMETER = 5;
    /// Позиция имени клиента в запросе вставки брони.
    private static final int INSERT_CUSTOMER_PARAMETER = 6;
    /// Позиция телефона в запросе вставки брони.
    private static final int INSERT_PHONE_PARAMETER = 7;
    /// Позиция комментария в запросе вставки брони.
    private static final int INSERT_COMMENT_PARAMETER = 8;
    /// Позиция пользователя или зала в запросе поиска броней.
    private static final int FIND_PARAMETER = 1;
    /// Позиция пользователя в запросе удаления брони.
    private static final int DELETE_USER_PARAMETER = 1;
    /// Позиция зала в запросе удаления брони.
    private static final int DELETE_ROOM_PARAMETER = 2;
    /// Позиция времени начала в запросе удаления брони.
    private static final int DELETE_START_PARAMETER = 3;
    /// Позиция зала в запросе проверки пересечения.
    private static final int CONFLICT_ROOM_PARAMETER = 1;
    /// Позиция конца проверяемого промежутка в запросе пересечения.
    private static final int CONFLICT_END_PARAMETER = 2;
    /// Позиция начала проверяемого промежутка в запросе пересечения.
    private static final int CONFLICT_START_PARAMETER = 3;

    /// База данных, через которую выполняются запросы.
    private final SqliteDatabase database;

    /// Создаёт хранилище броней.
    ///
    /// @param database база данных SQLite
    public SqliteBookingRepository(SqliteDatabase database) {
        this.database = database;
    }

    /// Добавляет бронь, если зал в это время свободен.
    ///
    /// @param booking новая бронь
    /// @return true, если бронь добавлена; false, если время уже занято
    @Override
    public boolean addIfFree(Booking booking) {
        try (Connection connection = database.open();
             SqliteTransaction transaction = new SqliteTransaction(connection)) {
            transaction.beginImmediate();
            if (hasConflict(connection, booking.roomId(), booking.start(), booking.end())) {
                transaction.rollback();
                return false;
            }
            insert(connection, booking);
            transaction.commit();
            return true;
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось сохранить бронь", exception);
        }
    }

    /// Проверяет, пересекается ли промежуток времени с существующими бронями зала.
    ///
    /// @param roomId идентификатор зала
    /// @param start  начало промежутка
    /// @param end    конец промежутка
    /// @return true, если зал в этот промежуток занят хотя бы частично
    @Override
    public boolean hasConflict(long roomId, LocalDateTime start, LocalDateTime end) {
        try (Connection connection = database.open()) {
            return hasConflict(connection, roomId, start, end);
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось проверить доступность зала", exception);
        }
    }

    /// Возвращает все брони клиента, включая прошедшие.
    ///
    /// @param userId идентификатор клиента
    /// @return брони клиента в порядке начала
    @Override
    public List<Booking> findByUser(long userId) {
        return find("SELECT * FROM bookings WHERE user_id=? ORDER BY start_time,id", userId);
    }

    /// Возвращает все брони зала, включая прошедшие.
    ///
    /// @param roomId идентификатор зала
    /// @return брони зала в порядке начала
    @Override
    public List<Booking> findByRoom(long roomId) {
        return find("SELECT * FROM bookings WHERE room_id=? ORDER BY start_time,id", roomId);
    }

    /// Читает брони по запросу с одним параметром.
    ///
    /// @param sql       запрос выборки броней
    /// @param parameter значение параметра
    /// @return найденные брони
    private List<Booking> find(String sql, long parameter) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(FIND_PARAMETER, parameter);
            try (ResultSet rows = statement.executeQuery()) {
                List<Booking> bookings = new ArrayList<>();
                while (rows.next()) {
                    bookings.add(readBooking(rows));
                }
                return bookings;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось прочитать брони", exception);
        }
    }

    /// Создаёт бронь из текущей строки результата.
    ///
    /// @param rows строка результата запроса
    /// @return прочитанная бронь
    /// @throws SQLException если поля брони не прочитаны
    private Booking readBooking(ResultSet rows) throws SQLException {
        return new Booking(rows.getLong("room_id"), rows.getLong("user_id"),
                LocalDateTime.parse(rows.getString("start_time")), rows.getInt("hours"),
                rows.getString("customer_name"), rows.getString("phone"), rows.getString("comment"));
    }

    /// Удаляет бронь пользователя в указанном зале и время.
    ///
    /// @param userId идентификатор клиента
    /// @param roomId идентификатор зала
    /// @param start  время начала брони
    /// @return true, если удалена ровно одна бронь
    @Override
    public boolean deleteByUserAndRoomAndStart(long userId, long roomId, LocalDateTime start) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM bookings WHERE user_id=? AND room_id=? AND start_time=?")) {
            statement.setLong(DELETE_USER_PARAMETER, userId);
            statement.setLong(DELETE_ROOM_PARAMETER, roomId);
            statement.setString(DELETE_START_PARAMETER, start.toString());
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось удалить бронь", exception);
        }
    }

    /// Вставляет бронь в открытом соединении.
    ///
    /// @param connection соединение с базой данных
    /// @param booking    сохраняемая бронь
    /// @throws SQLException если вставка не удалась
    private void insert(Connection connection, Booking booking) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bookings(room_id,user_id,start_time,end_time,hours,customer_name,phone,comment) "
                        + "VALUES(?,?,?,?,?,?,?,?)")) {
            statement.setLong(INSERT_ROOM_PARAMETER, booking.roomId());
            statement.setLong(INSERT_USER_PARAMETER, booking.userId());
            statement.setString(INSERT_START_PARAMETER, booking.start().toString());
            statement.setString(INSERT_END_PARAMETER, booking.end().toString());
            statement.setInt(INSERT_HOURS_PARAMETER, booking.hours());
            statement.setString(INSERT_CUSTOMER_PARAMETER, booking.customerName());
            statement.setString(INSERT_PHONE_PARAMETER, booking.phone());
            statement.setString(INSERT_COMMENT_PARAMETER, booking.comment());
            statement.executeUpdate();
        }
    }

    /// Проверяет пересечение броней в открытом соединении.
    ///
    /// @param connection соединение с базой данных
    /// @param roomId     идентификатор зала
    /// @param start      начало промежутка
    /// @param end        конец промежутка
    /// @return true, если зал занят хотя бы частично
    /// @throws SQLException если запрос не удался
    private boolean hasConflict(Connection connection, long roomId, LocalDateTime start, LocalDateTime end)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM bookings WHERE room_id=? AND start_time<? AND end_time>? LIMIT 1")) {
            statement.setLong(CONFLICT_ROOM_PARAMETER, roomId);
            statement.setString(CONFLICT_END_PARAMETER, end.toString());
            statement.setString(CONFLICT_START_PARAMETER, start.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }
}

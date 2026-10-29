package ru.urfu.echobot.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import ru.urfu.echobot.model.Booking;

/// Хранилище бронирований в SQLite, сохраняющее записи между запусками бота.
public final class SqliteBookingRepository implements BookingRepository, AutoCloseable {
    /// SQL для создания таблицы бронирований.
    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS bookings ("
            + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
            + "room_id INTEGER NOT NULL, "
            + "user_id INTEGER NOT NULL, "
            + "starts_at TEXT NOT NULL, "
            + "hours INTEGER NOT NULL CHECK (hours > 0), "
            + "customer_name TEXT NOT NULL, "
            + "phone TEXT NOT NULL, "
            + "comment TEXT NOT NULL DEFAULT '', "
            + "is_initial INTEGER NOT NULL DEFAULT 0, "
            + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)";
    /// SQL для получения всех броней конкретного зала.
    private static final String SELECT_ROOM_BOOKINGS = "SELECT room_id, user_id, starts_at, hours, "
            + "customer_name, phone, comment FROM bookings WHERE room_id = ?";
    /// SQL для добавления бронирования.
    private static final String INSERT_BOOKING = "INSERT INTO bookings "
            + "(room_id, user_id, starts_at, hours, customer_name, phone, comment, is_initial) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    /// Соединение с базой данных.
    private final Connection connection;

    /// Открывает файл базы данных и создаёт таблицу, если она ещё не существует.
    ///
    /// @param databasePath путь к файлу SQLite
    /// @throws SQLException если не удалось подключиться к базе или подготовить таблицу
    public SqliteBookingRepository(Path databasePath) throws SQLException {
        Path absolutePath = databasePath.toAbsolutePath();
        Path parent = absolutePath.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw new SQLException("Не удалось создать каталог базы данных", exception);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + absolutePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute(CREATE_TABLE);
            ensureInitialFlagColumn();
            statement.execute("UPDATE bookings SET is_initial = 1 WHERE user_id = 0 AND is_initial = 0");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_bookings_room "
                    + "ON bookings(room_id)");
        }
    }

    /// Обновляет демонстрационные записи при запуске, не затрагивая записи клиентов.
    /// Служебные записи всегда отражают завтра и послезавтра; клиентские записи остаются в SQLite.
    /// Если слот демонстрационной записи уже занят клиентом, демонстрационная запись пропускается.
    ///
    /// @param initialBookings текущие начальные записи из начальных данных задачи 1
    /// @throws SQLException если обновление начальных записей завершилось ошибкой
    public synchronized void refreshInitialBookings(List<Booking> initialBookings) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM bookings WHERE is_initial = 1");
            }
            for (Booking booking : initialBookings) {
                if (!hasConflictInternal(booking.roomId(), booking.start(), booking.end())) {
                    insert(booking, true);
                }
            }
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    /// Атомарно добавляет бронь, если она не пересекается с другой бронью того же зала.
    /// Синхронизация сериализует проверки и вставки в общем экземпляре репозитория.
    ///
    /// @param booking новая бронь
    /// @return true, если бронь добавлена; false, если время уже занято
    @Override
    public synchronized boolean addIfFree(Booking booking) {
        try {
            connection.setAutoCommit(false);
            if (hasConflictInternal(booking.roomId(), booking.start(), booking.end())) {
                connection.rollback();
                return false;
            }
            insert(booking, false);
            connection.commit();
            return true;
        } catch (SQLException exception) {
            rollbackAfterFailure(exception);
            throw new IllegalStateException("Не удалось сохранить бронирование в SQLite", exception);
        } finally {
            restoreAutoCommit();
        }
    }

    /// Проверяет пересечение выбранного промежутка с сохранёнными бронями.
    ///
    /// @param roomId идентификатор зала
    /// @param start начало проверяемого промежутка
    /// @param end конец проверяемого промежутка
    /// @return true, если зал занят хотя бы частично
    @Override
    public synchronized boolean hasConflict(long roomId, LocalDateTime start, LocalDateTime end) {
        try {
            return hasConflictInternal(roomId, start, end);
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось проверить доступность времени в SQLite", exception);
        }
    }

    /// Возвращает сохранённые брони пользователя в хронологическом порядке.
    ///
    /// @param userId идентификатор пользователя Telegram
    /// @return список всех броней пользователя, включая прошедшие
    @Override
    public synchronized List<Booking> findByUser(long userId) {
        String sql = "SELECT room_id, user_id, starts_at, hours, customer_name, phone, comment "
                + "FROM bookings WHERE user_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Booking> bookings = new ArrayList<>();
                while (resultSet.next()) {
                    bookings.add(readBooking(resultSet));
                }
                return bookings.stream().sorted(Comparator.comparing(Booking::start)).toList();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось прочитать бронирования из SQLite", exception);
        }
    }

    /// Закрывает соединение с файлом базы данных.
    ///
    /// @throws SQLException если соединение не удалось закрыть
    @Override
    public synchronized void close() throws SQLException {
        connection.close();
    }

    /// Добавляет признак демонстрационной записи в существующие базы данных прежней версии.
    ///
    /// @throws SQLException если структуру таблицы не удалось проверить или обновить
    private void ensureInitialFlagColumn() throws SQLException {
        boolean columnExists = false;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(bookings)")) {
            while (resultSet.next()) {
                if ("is_initial".equals(resultSet.getString("name"))) {
                    columnExists = true;
                    break;
                }
            }
        }
        if (!columnExists) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE bookings ADD COLUMN is_initial INTEGER NOT NULL DEFAULT 0");
            }
        }
    }

    /// Проверяет пересечение, не открывая новую транзакцию.
    ///
    /// @param roomId идентификатор зала
    /// @param start начало проверяемого промежутка
    /// @param end конец проверяемого промежутка
    /// @return true, если найдено пересечение
    /// @throws SQLException если чтение завершилось ошибкой
    private boolean hasConflictInternal(long roomId, LocalDateTime start, LocalDateTime end) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ROOM_BOOKINGS)) {
            statement.setLong(1, roomId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Booking existing = readBooking(resultSet);
                    if (existing.start().isBefore(end) && existing.end().isAfter(start)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /// Добавляет бронирование в текущей транзакции.
    ///
    /// @param booking бронь для сохранения
    /// @param initial true для демонстрационной записи, false для клиентской
    /// @throws SQLException если вставка завершилась ошибкой
    private void insert(Booking booking, boolean initial) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_BOOKING)) {
            statement.setLong(1, booking.roomId());
            statement.setLong(2, booking.userId());
            statement.setString(3, booking.start().toString());
            statement.setInt(4, booking.hours());
            statement.setString(5, booking.customerName());
            statement.setString(6, booking.phone());
            statement.setString(7, booking.comment());
            statement.setInt(8, initial ? 1 : 0);
            statement.executeUpdate();
        }
    }

    /// Преобразует текущую строку результата SQL в объект бронирования.
    ///
    /// @param resultSet строка результата запроса
    /// @return соответствующая бронь
    /// @throws SQLException если поле не удалось прочитать
    private Booking readBooking(ResultSet resultSet) throws SQLException {
        return new Booking(resultSet.getLong("room_id"), resultSet.getLong("user_id"),
                LocalDateTime.parse(resultSet.getString("starts_at")), resultSet.getInt("hours"),
                resultSet.getString("customer_name"), resultSet.getString("phone"),
                resultSet.getString("comment"));
    }

    /// Пытается откатить неудачную транзакцию, сохраняя исходную ошибку.
    ///
    /// @param cause причина отката
    private void rollbackAfterFailure(SQLException cause) {
        try {
            connection.rollback();
        } catch (SQLException rollbackException) {
            cause.addSuppressed(rollbackException);
        }
    }

    /// Возвращает соединение в режим автоматической фиксации после транзакции.
    private void restoreAutoCommit() {
        try {
            if (!connection.isClosed() && !connection.getAutoCommit()) {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось восстановить режим соединения SQLite", exception);
        }
    }
}

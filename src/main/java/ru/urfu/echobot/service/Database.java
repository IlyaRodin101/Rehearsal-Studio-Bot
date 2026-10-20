package ru.urfu.echobot.service;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public final class Database {
    private final String jdbcUrl;
    private final Object writeLock = new Object();

    public Database(String path) {
        this.jdbcUrl = "jdbc:sqlite:" + path;
    }

    public void initialize() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("CREATE TABLE IF NOT EXISTS bases (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, address TEXT NOT NULL," +
                    "timezone TEXT NOT NULL, booking_lead_hours INTEGER NOT NULL DEFAULT 2," +
                    "equipment TEXT NOT NULL DEFAULT '')");
            statement.execute("CREATE TABLE IF NOT EXISTS rooms (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, base_id INTEGER NOT NULL REFERENCES bases(id)," +
                    "name TEXT NOT NULL, comment_required INTEGER NOT NULL DEFAULT 0, active INTEGER NOT NULL DEFAULT 1)");
            statement.execute("CREATE TABLE IF NOT EXISTS opening_hours (" +
                    "base_id INTEGER NOT NULL REFERENCES bases(id) ON DELETE CASCADE," +
                    "weekday INTEGER NOT NULL, open_hour INTEGER, close_hour INTEGER," +
                    "PRIMARY KEY (base_id, weekday))");
            statement.execute("CREATE TABLE IF NOT EXISTS bookings (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, room_id INTEGER NOT NULL REFERENCES rooms(id)," +
                    "user_id INTEGER NOT NULL, starts_at TEXT NOT NULL, hours INTEGER NOT NULL," +
                    "customer_name TEXT NOT NULL, phone TEXT NOT NULL, comment TEXT NOT NULL DEFAULT ''," +
                    "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            statement.execute("CREATE INDEX IF NOT EXISTS bookings_room_start ON bookings(room_id, starts_at)");
        }
    }

    synchronized List<Base> getBases() throws SQLException {
        List<Base> result = new ArrayList<Base>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM bases ORDER BY id");
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                result.add(readBase(rows));
            }
        }
        return result;
    }

    synchronized Base getBase(long id) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM bases WHERE id=?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? readBase(rows) : null;
            }
        }
    }

    private Base readBase(ResultSet rows) throws SQLException {
        return new Base(rows.getLong("id"), rows.getString("name"), rows.getString("address"),
                rows.getString("timezone"), rows.getInt("booking_lead_hours"), rows.getString("equipment"));
    }

    synchronized List<Room> getRooms(long baseId) throws SQLException {
        List<Room> result = new ArrayList<Room>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id,base_id,name,comment_required FROM rooms WHERE base_id=? AND active=1 ORDER BY id")) {
            statement.setLong(1, baseId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.add(new Room(rows.getLong("id"), rows.getLong("base_id"),
                            rows.getString("name"), rows.getInt("comment_required") != 0));
                }
            }
        }
        return result;
    }

    synchronized Room getRoom(long roomId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id,base_id,name,comment_required FROM rooms WHERE id=? AND active=1")) {
            statement.setLong(1, roomId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                return new Room(rows.getLong("id"), rows.getLong("base_id"),
                        rows.getString("name"), rows.getInt("comment_required") != 0);
            }
        }
    }

    synchronized int[] getOpeningHours(long baseId, int weekday) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT open_hour,close_hour FROM opening_hours WHERE base_id=? AND weekday=?")) {
            statement.setLong(1, baseId);
            statement.setInt(2, weekday);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next() || rows.getObject("open_hour") == null) {
                    return null;
                }
                return new int[]{rows.getInt("open_hour"), rows.getInt("close_hour")};
            }
        }
    }

    synchronized void createBase(String name, String address, String timezone, int bookingLeadHours,
                                 String equipment, int[][] schedule, List<String> roomNames,
                                 boolean[] commentRequired) throws SQLException {
        synchronized (writeLock) {
            try (Connection connection = connect()) {
                connection.setAutoCommit(false);
                try {
                    long baseId;
                    try (PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO bases(name,address,timezone,booking_lead_hours,equipment) VALUES(?,?,?,?,?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                        statement.setString(1, name);
                        statement.setString(2, address);
                        statement.setString(3, timezone);
                        statement.setInt(4, bookingLeadHours);
                        statement.setString(5, equipment);
                        statement.executeUpdate();
                        try (ResultSet keys = statement.getGeneratedKeys()) {
                            if (!keys.next()) {
                                throw new SQLException("Не удалось получить идентификатор базы");
                            }
                            baseId = keys.getLong(1);
                        }
                    }
                    saveSchedule(connection, baseId, schedule);
                    for (int i = 0; i < roomNames.size(); i++) {
                        insertRoom(connection, baseId, roomNames.get(i), commentRequired[i]);
                    }
                    connection.commit();
                } catch (SQLException ex) {
                    connection.rollback();
                    throw ex;
                }
            }
        }
    }

    synchronized void updateBaseField(long id, String field, String value) throws SQLException {
        if (!"name".equals(field) && !"address".equals(field) && !"timezone".equals(field)
                && !"equipment".equals(field)) {
            throw new IllegalArgumentException("Недопустимое поле базы");
        }
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("UPDATE bases SET " + field + "=? WHERE id=?")) {
            statement.setString(1, value);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    synchronized void updateLeadHours(long id, int hours) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE bases SET booking_lead_hours=? WHERE id=?")) {
            statement.setInt(1, hours);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    synchronized void updateScheduleDay(long baseId, int weekday, int[] hours) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO opening_hours(base_id,weekday,open_hour,close_hour) VALUES(?,?,?,?) " +
                             "ON CONFLICT(base_id,weekday) DO UPDATE SET open_hour=excluded.open_hour," +
                             "close_hour=excluded.close_hour")) {
            statement.setLong(1, baseId);
            statement.setInt(2, weekday);
            if (hours == null) {
                statement.setNull(3, java.sql.Types.INTEGER);
                statement.setNull(4, java.sql.Types.INTEGER);
            } else {
                statement.setInt(3, hours[0]);
                statement.setInt(4, hours[1]);
            }
            statement.executeUpdate();
        }
    }

    synchronized long addRoom(long baseId, String name, boolean commentRequired) throws SQLException {
        try (Connection connection = connect()) {
            return insertRoom(connection, baseId, name, commentRequired);
        }
    }

    private long insertRoom(Connection connection, long baseId, String name, boolean commentRequired)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO rooms(base_id,name,comment_required) VALUES(?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, baseId);
            statement.setString(2, name);
            statement.setInt(3, commentRequired ? 1 : 0);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1;
            }
        }
    }

    synchronized void updateRoom(long roomId, String name, boolean commentRequired) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE rooms SET name=?,comment_required=? WHERE id=? AND active=1")) {
            statement.setString(1, name);
            statement.setInt(2, commentRequired ? 1 : 0);
            statement.setLong(3, roomId);
            statement.executeUpdate();
        }
    }

    synchronized void deactivateRoom(long roomId) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement("UPDATE rooms SET active=0 WHERE id=?")) {
            statement.setLong(1, roomId);
            statement.executeUpdate();
        }
    }

    synchronized List<Booking> getUpcomingBookings(long userId) throws SQLException {
        List<Booking> result = new ArrayList<Booking>();
        String sql = "SELECT b.id,b.room_id,r.name room_name,ba.name base_name,ba.timezone,b.starts_at," +
                "b.hours,b.customer_name,b.phone,b.comment FROM bookings b JOIN rooms r ON r.id=b.room_id " +
                "JOIN bases ba ON ba.id=r.base_id WHERE b.user_id=? ORDER BY b.starts_at";
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    LocalDateTime start = LocalDateTime.parse(rows.getString("starts_at"));
                    if (start.atZone(ZoneId.of(rows.getString("timezone")))
                            .isAfter(ZonedDateTime.now(ZoneId.of(rows.getString("timezone"))))) {
                        result.add(new Booking(rows.getLong("id"), rows.getLong("room_id"),
                                rows.getString("room_name"), rows.getString("base_name"),
                                rows.getString("timezone"), start, rows.getInt("hours"),
                                rows.getString("customer_name"), rows.getString("phone"), rows.getString("comment")));
                    }
                }
            }
        }
        return result;
    }

    synchronized boolean hasRoomConflict(long roomId, LocalDateTime start, LocalDateTime end) throws SQLException {
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT 1 FROM bookings WHERE room_id=? " +
                             "AND CAST(strftime('%s',starts_at) AS INTEGER) < CAST(strftime('%s',?) AS INTEGER) " +
                             "AND CAST(strftime('%s',starts_at) AS INTEGER) + hours * 3600 > " +
                             "CAST(strftime('%s',?) AS INTEGER) LIMIT 1")) {
            statement.setLong(1, roomId);
            statement.setString(2, end.toString());
            statement.setString(3, start.toString());
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    synchronized boolean createBooking(long roomId, long userId, LocalDateTime start, int hours,
                                       String name, String phone, String comment) throws SQLException {
        synchronized (writeLock) {
            try (Connection connection = connect(); Statement lock = connection.createStatement()) {
                lock.execute("BEGIN IMMEDIATE");
                try {
                    String timezone;
                    int leadHours;
                    try (PreparedStatement settings = connection.prepareStatement(
                            "SELECT b.timezone,b.booking_lead_hours,oh.open_hour,oh.close_hour " +
                                    "FROM rooms r JOIN bases b ON b.id=r.base_id " +
                                    "LEFT JOIN opening_hours oh ON oh.base_id=b.id AND oh.weekday=? " +
                                    "WHERE r.id=? AND r.active=1")) {
                        settings.setInt(1, start.getDayOfWeek().getValue());
                        settings.setLong(2, roomId);
                        try (ResultSet row = settings.executeQuery()) {
                            if (!row.next() || row.getObject("open_hour") == null) {
                                lock.execute("ROLLBACK");
                                return false;
                            }
                            int openHour = row.getInt("open_hour");
                            int closeHour = row.getInt("close_hour");
                            timezone = row.getString("timezone");
                            leadHours = row.getInt("booking_lead_hours");
                            if (start.getHour() < openHour || start.getHour() + hours > closeHour
                                    || start.atZone(ZoneId.of(timezone))
                                    .isBefore(ZonedDateTime.now(ZoneId.of(timezone)).plusHours(leadHours))) {
                                lock.execute("ROLLBACK");
                                return false;
                            }
                        }
                    }
                    LocalDateTime end = start.plusHours(hours);
                    try (PreparedStatement conflict = connection.prepareStatement(
                            "SELECT 1 FROM bookings WHERE room_id=? " +
                                    "AND CAST(strftime('%s',starts_at) AS INTEGER) < CAST(strftime('%s',?) AS INTEGER) " +
                                    "AND CAST(strftime('%s',starts_at) AS INTEGER) + hours * 3600 > " +
                                    "CAST(strftime('%s',?) AS INTEGER) LIMIT 1")) {
                        conflict.setLong(1, roomId);
                        conflict.setString(2, end.toString());
                        conflict.setString(3, start.toString());
                        try (ResultSet rows = conflict.executeQuery()) {
                            if (rows.next()) {
                                lock.execute("ROLLBACK");
                                return false;
                            }
                        }
                    }
                    try (PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO bookings(room_id,user_id,starts_at,hours,customer_name,phone,comment) " +
                                    "VALUES(?,?,?,?,?,?,?)")) {
                        insert.setLong(1, roomId);
                        insert.setLong(2, userId);
                        insert.setString(3, start.toString());
                        insert.setInt(4, hours);
                        insert.setString(5, name);
                        insert.setString(6, phone);
                        insert.setString(7, comment);
                        insert.executeUpdate();
                    }
                    lock.execute("COMMIT");
                    return true;
                } catch (SQLException ex) {
                    try {
                        lock.execute("ROLLBACK");
                    } catch (SQLException rollbackException) {
                        ex.addSuppressed(rollbackException);
                    }
                    throw ex;
                }
            }
        }
    }

    private void saveSchedule(Connection connection, long baseId, int[][] schedule) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO opening_hours(base_id,weekday,open_hour,close_hour) VALUES(?,?,?,?)")) {
            for (int day = 1; day <= 7; day++) {
                statement.setLong(1, baseId);
                statement.setInt(2, day);
                if (schedule[day - 1] == null) {
                    statement.setNull(3, java.sql.Types.INTEGER);
                    statement.setNull(4, java.sql.Types.INTEGER);
                } else {
                    statement.setInt(3, schedule[day - 1][0]);
                    statement.setInt(4, schedule[day - 1][1]);
                }
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    static final class Base {
        final long id;
        final String name;
        final String address;
        final String timezone;
        final int bookingLeadHours;
        final String equipment;

        Base(long id, String name, String address, String timezone, int bookingLeadHours, String equipment) {
            this.id = id;
            this.name = name;
            this.address = address;
            this.timezone = timezone;
            this.bookingLeadHours = bookingLeadHours;
            this.equipment = equipment;
        }
    }

    static final class Room {
        final long id;
        final long baseId;
        final String name;
        final boolean commentRequired;

        Room(long id, long baseId, String name, boolean commentRequired) {
            this.id = id;
            this.baseId = baseId;
            this.name = name;
            this.commentRequired = commentRequired;
        }
    }

    static final class Booking {
        final long id;
        final long roomId;
        final String roomName;
        final String baseName;
        final String timezone;
        final LocalDateTime start;
        final int hours;
        final String customerName;
        final String phone;
        final String comment;

        Booking(long id, long roomId, String roomName, String baseName, String timezone,
                LocalDateTime start, int hours, String customerName, String phone, String comment) {
            this.id = id;
            this.roomId = roomId;
            this.roomName = roomName;
            this.baseName = baseName;
            this.timezone = timezone;
            this.start = start;
            this.hours = hours;
            this.customerName = customerName;
            this.phone = phone;
            this.comment = comment;
        }
    }
}

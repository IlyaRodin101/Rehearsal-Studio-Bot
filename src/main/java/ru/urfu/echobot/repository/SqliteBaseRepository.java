package ru.urfu.echobot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.OpeningHours;

/// SQLite repository for bases and their weekly schedules.
public final class SqliteBaseRepository implements BaseRepository {
    private static final int OWNER_ID_PARAMETER = 1;
    private static final int BASE_NAME_PARAMETER = 2;
    private static final int ADDRESS_PARAMETER = 3;
    private static final int TIMEZONE_PARAMETER = 4;
    private static final int LEAD_HOURS_PARAMETER = 5;
    private static final int EQUIPMENT_PARAMETER = 6;
    private static final int BASE_ID_PARAMETER = 7;
    private static final int DAY_PARAMETER = 2;
    private static final int OPEN_HOUR_PARAMETER = 3;
    private static final int CLOSE_HOUR_PARAMETER = 4;
    private final SqliteDatabase database;

    public SqliteBaseRepository(SqliteDatabase database) {
        this.database = database;
    }

    @Override
    public List<Base> findAll() {
        return queryBases("SELECT * FROM bases ORDER BY id", null);
    }

    @Override
    public Optional<Base> findById(long id) {
        return queryBases("SELECT * FROM bases WHERE id = ?", id).stream().findFirst();
    }

    @Override
    public List<Base> findByOwnerId(long ownerId) {
        return queryBases("SELECT * FROM bases WHERE owner_id = ? ORDER BY id", ownerId);
    }

    @Override
    public Base save(Base base) {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                long id = insertBase(connection, base);
                saveSchedule(connection, id, base.openingHours());
                connection.commit();
                return new Base(id, base.ownerId(), base.name(), base.address(), base.timezone(),
                        base.bookingLeadHours(), base.equipment(), base.openingHours());
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not save base", exception);
        }
    }

    @Override
    public void update(Base base) {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE bases SET owner_id=?,name=?,address=?,timezone=?,booking_lead_hours=?,equipment=? "
                                + "WHERE id=?")) {
                    bindBase(statement, base);
                    statement.setLong(BASE_ID_PARAMETER, base.id());
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalArgumentException("Base does not exist: " + base.id());
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM opening_hours WHERE base_id=?")) {
                    statement.setLong(1, base.id());
                    statement.executeUpdate();
                }
                saveSchedule(connection, base.id(), base.openingHours());
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not update base", exception);
        }
    }

    @Override
    public boolean deleteByIdAndOwnerId(long id, long ownerId) {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                if (!baseExists(connection, id, ownerId)) {
                    connection.rollback();
                    return false;
                }
                deleteRelatedRows(connection, "DELETE FROM bookings WHERE room_id IN "
                        + "(SELECT id FROM rooms WHERE base_id=?)", id);
                deleteRelatedRows(connection, "DELETE FROM rooms WHERE base_id=?", id);
                deleteRelatedRows(connection, "DELETE FROM opening_hours WHERE base_id=?", id);
                try (PreparedStatement statement = connection.prepareStatement(
                        "DELETE FROM bases WHERE id=? AND owner_id=?")) {
                    statement.setLong(1, id);
                    statement.setLong(2, ownerId);
                    if (statement.executeUpdate() != 1) {
                        throw new IllegalStateException("Base changed while it was being deleted: " + id);
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not delete base", exception);
        }
    }

    private boolean baseExists(Connection connection, long id, long ownerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM bases WHERE id=? AND owner_id=?")) {
            statement.setLong(1, id);
            statement.setLong(2, ownerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    private void deleteRelatedRows(Connection connection, String sql, long baseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, baseId);
            statement.executeUpdate();
        }
    }

    private List<Base> queryBases(String sql, Long parameter) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) {
                statement.setLong(OWNER_ID_PARAMETER, parameter);
            }
            try (ResultSet rows = statement.executeQuery()) {
                List<Base> bases = new ArrayList<>();
                while (rows.next()) {
                    long id = rows.getLong("id");
                    Map<DayOfWeek, OpeningHours> schedule = loadSchedule(connection, id);
                    bases.add(new Base(id, rows.getLong("owner_id"), rows.getString("name"),
                            rows.getString("address"), rows.getString("timezone"), rows.getInt("booking_lead_hours"),
                            rows.getString("equipment"), schedule));
                }
                return bases;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not read bases", exception);
        }
    }

    private Map<DayOfWeek, OpeningHours> loadSchedule(Connection connection, long baseId) throws SQLException {
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT day_of_week,open_hour,close_hour FROM opening_hours WHERE base_id=?")) {
            statement.setLong(1, baseId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    schedule.put(DayOfWeek.of(rows.getInt("day_of_week")),
                            new OpeningHours(rows.getInt("open_hour"), rows.getInt("close_hour")));
                }
            }
        }
        return schedule;
    }

    private void saveSchedule(Connection connection, long baseId, Map<DayOfWeek, OpeningHours> schedule)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO opening_hours(base_id,day_of_week,open_hour,close_hour) VALUES(?,?,?,?)")) {
            for (Map.Entry<DayOfWeek, OpeningHours> entry : schedule.entrySet()) {
                statement.setLong(OWNER_ID_PARAMETER, baseId);
                statement.setInt(DAY_PARAMETER, entry.getKey().getValue());
                statement.setInt(OPEN_HOUR_PARAMETER, entry.getValue().openHour());
                statement.setInt(CLOSE_HOUR_PARAMETER, entry.getValue().closeHour());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void bindBase(PreparedStatement statement, Base base) throws SQLException {
        statement.setLong(OWNER_ID_PARAMETER, base.ownerId());
        statement.setString(BASE_NAME_PARAMETER, base.name());
        statement.setString(ADDRESS_PARAMETER, base.address());
        statement.setString(TIMEZONE_PARAMETER, base.timezone());
        statement.setInt(LEAD_HOURS_PARAMETER, base.bookingLeadHours());
        statement.setString(EQUIPMENT_PARAMETER, base.equipment());
    }

    private long insertBase(Connection connection, Base base) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bases(owner_id,name,address,timezone,booking_lead_hours,equipment) "
                        + "VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            bindBase(statement, base);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite did not return an ID for the new base");
                }
                return keys.getLong(OWNER_ID_PARAMETER);
            }
        }
    }
}

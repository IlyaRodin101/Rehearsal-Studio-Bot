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

/// Хранит репетиционные базы и их недельный график в SQLite.
public final class SqliteBaseRepository implements BaseRepository {
    /// Запрос удаления графика базы.
    private static final String DELETE_SCHEDULE_SQL = "DELETE FROM opening_hours WHERE base_id=?";
    /// Позиция владельца в запросах баз.
    private static final int OWNER_ID_PARAMETER = 1;
    /// Позиция названия в запросах баз.
    private static final int BASE_NAME_PARAMETER = 2;
    /// Позиция адреса в запросах баз.
    private static final int ADDRESS_PARAMETER = 3;
    /// Позиция часового пояса в запросах баз.
    private static final int TIMEZONE_PARAMETER = 4;
    /// Позиция срока бронирования в запросах баз.
    private static final int LEAD_HOURS_PARAMETER = 5;
    /// Позиция оборудования в запросах баз.
    private static final int EQUIPMENT_PARAMETER = 6;
    /// Позиция идентификатора базы в запросах баз.
    private static final int BASE_ID_PARAMETER = 7;
    /// Позиция дня недели в запросах графика.
    private static final int DAY_PARAMETER = 2;
    /// Позиция часа открытия в запросах графика.
    private static final int OPEN_HOUR_PARAMETER = 3;
    /// Позиция часа закрытия в запросах графика.
    private static final int CLOSE_HOUR_PARAMETER = 4;
    /// Позиция единственного параметра в простых запросах.
    private static final int SINGLE_PARAMETER = 1;
    /// Позиция первого параметра в запросах с двумя условиями.
    private static final int FIRST_PARAMETER = 1;
    /// Позиция второго параметра в запросах с двумя условиями.
    private static final int SECOND_PARAMETER = 2;

    /// База данных, через которую выполняются запросы.
    private final SqliteDatabase database;

    /// Создаёт хранилище баз.
    ///
    /// @param database база данных SQLite
    public SqliteBaseRepository(SqliteDatabase database) {
        this.database = database;
    }

    /// Возвращает все базы.
    ///
    /// @return список баз в порядке добавления
    @Override
    public List<Base> findAll() {
        return queryBases("SELECT * FROM bases ORDER BY id", null);
    }

    /// Ищет базу по идентификатору.
    ///
    /// @param id идентификатор базы
    /// @return база или пустой результат, если такой нет
    @Override
    public Optional<Base> findById(long id) {
        return queryBases("SELECT * FROM bases WHERE id = ?", id).stream().findFirst();
    }

    /// Возвращает базы владельца.
    ///
    /// @param ownerId идентификатор владельца
    /// @return базы владельца в порядке добавления
    @Override
    public List<Base> findByOwnerId(long ownerId) {
        return queryBases("SELECT * FROM bases WHERE owner_id = ? ORDER BY id", ownerId);
    }

    /// Добавляет новую базу вместе с графиком.
    ///
    /// @param base сохраняемая база
    /// @return сохранённая база с присвоенным идентификатором
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
            throw new IllegalStateException("Не удалось сохранить базу", exception);
        }
    }

    /// Обновляет базу с тем же идентификатором и заменяет её график.
    ///
    /// @param base обновлённая база
    @Override
    public void update(Base base) {
        try (Connection connection = database.open()) {
            connection.setAutoCommit(false);
            try {
                updateBaseRow(connection, base);
                deleteSchedule(connection, base.id());
                saveSchedule(connection, base.id(), base.openingHours());
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось обновить базу", exception);
        }
    }

    /// Удаляет базу вместе с залами, графиком и бронями.
    ///
    /// @param id      идентификатор базы
    /// @param ownerId идентификатор владельца
    /// @return true, если база удалена
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
                deleteRelatedRows(connection, DELETE_SCHEDULE_SQL, id);
                deleteBaseRow(connection, id, ownerId);
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Не удалось удалить базу", exception);
        }
    }

    /// Проверяет, что база существует и принадлежит владельцу.
    ///
    /// @param connection соединение с базой данных
    /// @param id         идентификатор базы
    /// @param ownerId    идентификатор владельца
    /// @return true, если база найдена
    /// @throws SQLException если запрос не удался
    private boolean baseExists(Connection connection, long id, long ownerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM bases WHERE id=? AND owner_id=?")) {
            statement.setLong(FIRST_PARAMETER, id);
            statement.setLong(SECOND_PARAMETER, ownerId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next();
            }
        }
    }

    /// Удаляет строки, связанные с базой, по указанному запросу.
    ///
    /// @param connection соединение с базой данных
    /// @param sql        запрос удаления с одним параметром
    /// @param baseId     идентификатор базы
    /// @throws SQLException если удаление не удалось
    private void deleteRelatedRows(Connection connection, String sql, long baseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(SINGLE_PARAMETER, baseId);
            statement.executeUpdate();
        }
    }

    /// Удаляет строку базы, проверяя владельца.
    ///
    /// @param connection соединение с базой данных
    /// @param id         идентификатор базы
    /// @param ownerId    идентификатор владельца
    /// @throws SQLException если база изменилась во время удаления
    private void deleteBaseRow(Connection connection, long id, long ownerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM bases WHERE id=? AND owner_id=?")) {
            statement.setLong(FIRST_PARAMETER, id);
            statement.setLong(SECOND_PARAMETER, ownerId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("База изменилась во время удаления: " + id);
            }
        }
    }

    /// Обновляет строку базы.
    ///
    /// @param connection соединение с базой данных
    /// @param base       обновлённая база
    /// @throws SQLException если база не найдена
    private void updateBaseRow(Connection connection, Base base) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE bases SET owner_id=?,name=?,address=?,timezone=?,booking_lead_hours=?,equipment=? "
                        + "WHERE id=?")) {
            bindBase(statement, base);
            statement.setLong(BASE_ID_PARAMETER, base.id());
            if (statement.executeUpdate() != 1) {
                throw new IllegalArgumentException("База не существует: " + base.id());
            }
        }
    }

    /// Удаляет график базы.
    ///
    /// @param connection соединение с базой данных
    /// @param baseId     идентификатор базы
    /// @throws SQLException если удаление не удалось
    private void deleteSchedule(Connection connection, long baseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_SCHEDULE_SQL)) {
            statement.setLong(SINGLE_PARAMETER, baseId);
            statement.executeUpdate();
        }
    }

    /// Читает базы по запросу с необязательным параметром.
    ///
    /// @param sql       запрос выборки баз
    /// @param parameter параметр запроса или null, если параметра нет
    /// @return найденные базы
    private List<Base> queryBases(String sql, Long parameter) {
        try (Connection connection = database.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (parameter != null) {
                statement.setLong(SINGLE_PARAMETER, parameter);
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
            throw new IllegalStateException("Не удалось прочитать базы", exception);
        }
    }

    /// Загружает график базы.
    ///
    /// @param connection соединение с базой данных
    /// @param baseId     идентификатор базы
    /// @return график по дням недели
    /// @throws SQLException если запрос не удался
    private Map<DayOfWeek, OpeningHours> loadSchedule(Connection connection, long baseId) throws SQLException {
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT day_of_week,open_hour,close_hour FROM opening_hours WHERE base_id=?")) {
            statement.setLong(SINGLE_PARAMETER, baseId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    schedule.put(DayOfWeek.of(rows.getInt("day_of_week")),
                            new OpeningHours(rows.getInt("open_hour"), rows.getInt("close_hour")));
                }
            }
        }
        return schedule;
    }

    /// Сохраняет график базы.
    ///
    /// @param connection соединение с базой данных
    /// @param baseId     идентификатор базы
    /// @param schedule   график по дням недели
    /// @throws SQLException если вставка не удалась
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

    /// Подставляет поля базы в запрос.
    ///
    /// @param statement запрос с параметрами базы
    /// @param base      база с новыми значениями
    /// @throws SQLException если подстановка не удалась
    private void bindBase(PreparedStatement statement, Base base) throws SQLException {
        statement.setLong(OWNER_ID_PARAMETER, base.ownerId());
        statement.setString(BASE_NAME_PARAMETER, base.name());
        statement.setString(ADDRESS_PARAMETER, base.address());
        statement.setString(TIMEZONE_PARAMETER, base.timezone());
        statement.setInt(LEAD_HOURS_PARAMETER, base.bookingLeadHours());
        statement.setString(EQUIPMENT_PARAMETER, base.equipment());
    }

    /// Вставляет базу и возвращает её идентификатор.
    ///
    /// @param connection соединение с базой данных
    /// @param base       сохраняемая база
    /// @return идентификатор новой базы
    /// @throws SQLException если вставка не удалась
    private long insertBase(Connection connection, Base base) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bases(owner_id,name,address,timezone,booking_lead_hours,equipment) "
                        + "VALUES(?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            bindBase(statement, base);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("SQLite не вернул идентификатор новой базы");
                }
                return keys.getLong(OWNER_ID_PARAMETER);
            }
        }
    }
}

package ru.urfu.echobot.repository;

import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/// Начальные данные бота: две базы с залами и две записи на чужое имя.
/// Создаются заново при каждом запуске, поэтому после перезапуска состояние всегда одинаковое.
public final class InitialData {
    /// Часовой пояс обеих баз.
    private static final String TIMEZONE = "Europe/Moscow";
    /// Разделитель строк в описании оборудования.
    private static final String LINE_SEPARATOR = "\n";
    /// Название первого зала на обеих базах.
    private static final String FIRST_ROOM_NAME = "Зал 1";
    /// Час открытия во все рабочие дни.
    private static final int OPEN_HOUR = 9;
    /// Час закрытия во все рабочие дни.
    private static final int CLOSE_HOUR = 24;
    /// За сколько часов до начала ещё можно бронировать.
    private static final int LEAD_HOURS = 2;
    /// Идентификатор базы «Центр».
    private static final long CENTER_ID = 1;
    /// Идентификатор базы «Север».
    private static final long NORTH_ID = 2;
    /// Идентификатор первого зала базы «Центр».
    private static final long CENTER_FIRST_ROOM_ID = 1;
    /// Идентификатор второго зала базы «Центр».
    private static final long CENTER_SECOND_ROOM_ID = 2;
    /// Идентификатор первого зала базы «Север».
    private static final long NORTH_FIRST_ROOM_ID = 3;
    /// Идентификатор студии базы «Север».
    private static final long NORTH_STUDIO_ID = 4;
    /// Идентификатор служебного клиента, на которого оформлены начальные записи.
    private static final long SERVICE_USER_ID = 0;
    /// Час начала начальной записи в «Центре».
    private static final int CENTER_BOOKING_HOUR = 18;
    /// Длительность начальной записи в «Центре».
    private static final int CENTER_BOOKING_HOURS = 2;
    /// Час начала начальной записи на «Севере».
    private static final int NORTH_BOOKING_HOUR = 13;
    /// Длительность начальной записи на «Севере».
    private static final int NORTH_BOOKING_HOURS = 3;
    /// Имя клиента в начальных записях.
    private static final String SERVICE_CUSTOMER = "Служебная запись";
    /// Телефон в начальных записях.
    private static final String SERVICE_PHONE = "+70000000000";

    /// Часы, от которых считаются даты начальных записей.
    private final Clock clock;

    /// Создаёт набор начальных данных.
    ///
    /// @param clock источник текущего времени
    public InitialData(Clock clock) {
        this.clock = clock;
    }

    /// Возвращает базы «Центр» и «Север».
    ///
    /// @return список баз
    public List<Base> bases() {
        String centerEquipment = String.join(LINE_SEPARATOR, "Барабаны: акустическая установка",
                "Комбоусилители: 2 шт.", "Микрофоны: 4 шт.", "Микшерный пульт: 1 шт.");
        String northEquipment = "Барабаны, 2 комбоусилителя, 4 микрофона, микшерный пульт";
        return List.of(
                new Base(CENTER_ID, "Центр", "ул. Ленина, 1", TIMEZONE, LEAD_HOURS, centerEquipment, schedule()),
                new Base(NORTH_ID, "Север", "ул. Ленина, 15", TIMEZONE, LEAD_HOURS, northEquipment, schedule()));
    }

    /// Возвращает залы обеих баз.
    ///
    /// @return список залов
    public List<Room> rooms() {
        return List.of(
                new Room(CENTER_FIRST_ROOM_ID, CENTER_ID, FIRST_ROOM_NAME, false),
                new Room(CENTER_SECOND_ROOM_ID, CENTER_ID, "Зал 2", false),
                new Room(NORTH_FIRST_ROOM_ID, NORTH_ID, FIRST_ROOM_NAME, false),
                new Room(NORTH_STUDIO_ID, NORTH_ID, "Студия", true));
    }

    /// Возвращает начальные записи: «Центр», Зал 1, завтра, и «Север», Студия, послезавтра.
    /// Если нужный день воскресенье, запись переносится на понедельник.
    ///
    /// @return список записей на служебного клиента
    public List<Booking> bookings() {
        return List.of(
                new Booking(CENTER_FIRST_ROOM_ID, SERVICE_USER_ID, workDay(1).atTime(CENTER_BOOKING_HOUR, 0),
                        CENTER_BOOKING_HOURS, SERVICE_CUSTOMER, SERVICE_PHONE, ""),
                new Booking(NORTH_STUDIO_ID, SERVICE_USER_ID, workDay(2).atTime(NORTH_BOOKING_HOUR, 0),
                        NORTH_BOOKING_HOURS, SERVICE_CUSTOMER, SERVICE_PHONE, SERVICE_CUSTOMER));
    }

    /// Возвращает график: понедельник-суббота с 9:00 до 24:00, воскресенье выходной.
    ///
    /// @return часы работы по дням недели
    private Map<DayOfWeek, OpeningHours> schedule() {
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day != DayOfWeek.SUNDAY) {
                schedule.put(day, new OpeningHours(OPEN_HOUR, CLOSE_HOUR));
            }
        }
        return schedule;
    }

    /// Возвращает ближайший рабочий день через указанное число дней от сегодня.
    ///
    /// @param daysAhead сколько дней прибавить к сегодняшнему
    /// @return дата, не приходящаяся на воскресенье
    private LocalDate workDay(int daysAhead) {
        LocalDate date = LocalDate.now(clock.withZone(ZoneId.of(TIMEZONE))).plusDays(daysAhead);
        return date.getDayOfWeek() == DayOfWeek.SUNDAY ? date.plusDays(1) : date;
    }
}

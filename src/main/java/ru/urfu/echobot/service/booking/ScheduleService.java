package ru.urfu.echobot.service.booking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Собирает данные расписания баз для администраторов и владельцев без Telegram.
public final class ScheduleService {
    /// Число месяцев, показываемых в расписании.
    private static final int MONTH_COUNT = 6;
    /// Число дней в неделе.
    private static final int DAYS_PER_WEEK = 7;

    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Хранилище залов.
    private final RoomRepository roomRepository;
    /// Хранилище броней.
    private final BookingRepository bookingRepository;
    /// Правила бронирования для определения дат базы.
    private final BookingRules rules;

    /// Создаёт сервис расписания.
    ///
    /// @param baseRepository    хранилище баз
    /// @param roomRepository    хранилище залов
    /// @param bookingRepository хранилище броней
    /// @param rules             правила бронирования
    public ScheduleService(BaseRepository baseRepository, RoomRepository roomRepository,
                           BookingRepository bookingRepository, BookingRules rules) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.rules = rules;
    }

    /// Возвращает все базы. Клиенты сюда не обращаются: доступ проверяет контроллер.
    ///
    /// @return список баз в порядке добавления
    public List<Base> bases() {
        return baseRepository.findAll();
    }

    /// Возвращает текущий месяц базы и следующие пять месяцев.
    ///
    /// @param base база, для которой строится список месяцев
    /// @return шесть месяцев, начиная с текущего месяца базы
    public List<YearMonth> months(@NotNull Base base) {
        YearMonth current = YearMonth.from(rules.today(base));
        List<YearMonth> months = new ArrayList<>(MONTH_COUNT);
        for (int offset = 0; offset < MONTH_COUNT; offset++) {
            months.add(current.plusMonths(offset));
        }
        return months;
    }

    /// Возвращает понедельники, с которых начинаются недели внутри месяца.
    ///
    /// Первая неделя начинается с первого понедельника месяца, поэтому частичные
    /// недели до первого понедельника в список не входят. Последняя неделя может
    /// заканчиваться в следующем месяце.
    ///
    /// @param base  база, для которой строится список недель
    /// @param month выбранный месяц
    /// @return понедельники месяца по возрастанию
    public List<LocalDate> weekStarts(@NotNull Base base, @NotNull YearMonth month) {
        LocalDate cursor = month.atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        LocalDate lastDay = month.atEndOfMonth();
        List<LocalDate> starts = new ArrayList<>();
        while (!cursor.isAfter(lastDay)) {
            starts.add(cursor);
            cursor = cursor.plusWeeks(1);
        }
        return starts;
    }

    /// Возвращает семь дней недели, начиная с понедельника.
    ///
    /// @param weekStart первый день недели
    /// @return семь дат от понедельника до воскресенья
    public List<LocalDate> days(@NotNull LocalDate weekStart) {
        List<LocalDate> days = new ArrayList<>(DAYS_PER_WEEK);
        for (int offset = 0; offset < DAYS_PER_WEEK; offset++) {
            days.add(weekStart.plusDays(offset));
        }
        return days;
    }

    /// Возвращает все брони выбранного дня, сгруппированные по активным залам базы.
    ///
    /// Записи внутри зала отсортированы по времени начала. Пустые залы не включаются.
    ///
    /// @param base база, для которой строится расписание
    /// @param date выбранный день
    /// @return расписание дня
    public ScheduleDay day(@NotNull Base base, @NotNull LocalDate date) {
        List<RoomSchedule> rooms = new ArrayList<>();
        for (Room room : roomRepository.findByBaseId(base.id())) {
            List<Booking> bookings = bookingRepository.findByRoom(room.id()).stream()
                    .filter(booking -> booking.start().toLocalDate().equals(date))
                    .sorted(Comparator.comparing(Booking::start))
                    .toList();
            if (!bookings.isEmpty()) {
                rooms.add(new RoomSchedule(room, bookings));
            }
        }
        return new ScheduleDay(base, date, rooms);
    }

    /// Ищет базу по идентификатору.
    ///
    /// @param baseId идентификатор базы
    /// @return база или пустой результат
    public Optional<Base> findBase(long baseId) {
        return baseRepository.findById(baseId);
    }

    /// Ищет зал по идентификатору.
    ///
    /// @param roomId идентификатор зала
    /// @return зал или пустой результат
    public Optional<Room> findRoom(long roomId) {
        return roomRepository.findById(roomId);
    }

    /// Ищет бронь зала по времени начала.
    ///
    /// @param roomId идентификатор зала
    /// @param start  время начала
    /// @return бронь или пустой результат
    public Optional<Booking> findBooking(long roomId, @NotNull LocalDateTime start) {
        return bookingRepository.findByRoomAndStart(roomId, start);
    }

    /// Проверяет, что бронь ещё не началась.
    ///
    /// @param base    база, в которой находится зал брони
    /// @param booking бронь
    /// @return true, если бронь начинается в будущем
    public boolean isUpcoming(@NotNull Base base, @NotNull Booking booking) {
        return rules.isUpcoming(base, booking);
    }
}

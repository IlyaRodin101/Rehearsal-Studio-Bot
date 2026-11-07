package ru.urfu.echobot.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Формирует сведения о будущих бронях и справочную информацию о репетиционных базах.
public final class BookingInformationService {
    /// Число дней в неделе.
    private static final int DAYS_IN_WEEK = 7;
    /// Текст приглашения выбрать базу.
    private static final String SELECT_BASE = "Выберите базу:";
    /// Формат даты начала брони в callback-кнопках.
    private static final DateTimeFormatter CALLBACK_START_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Хранилище залов.
    private final RoomRepository roomRepository;
    /// Хранилище бронирований.
    private final BookingRepository bookingRepository;
    /// Правила определения будущих броней.
    private final BookingRules rules;
    /// Форматер дат и времени.
    private final RussianDateFormatter formatter;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards = new BookingKeyboardFactory();
    private final String back = "Назад";

    /// Создаёт сервис отображения броней и информации о базах.
    ///
    /// @param baseRepository    хранилище баз
    /// @param roomRepository    хранилище залов
    /// @param bookingRepository хранилище броней
    /// @param rules             правила бронирования
    /// @param formatter         форматер дат и времени
    public BookingInformationService(BaseRepository baseRepository, RoomRepository roomRepository,
                                     BookingRepository bookingRepository, BookingRules rules,
                                     RussianDateFormatter formatter) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.rules = rules;
        this.formatter = formatter;
    }

    /// Возвращает текст со списком будущих броней выбранного пользователя.
    ///
    /// @param userId идентификатор пользователя
    /// @return текст для раздела «Мои записи»
    public String myBookingsMessage(long userId) {
        return myBookingsMessage(upcomingBookings(userId));
    }

    private String myBookingsMessage(List<UpcomingBooking> bookings) {
        StringBuilder text = new StringBuilder("Ваши будущие записи:\n");
        for (UpcomingBooking item : bookings) {
            Booking booking = item.booking();
            text.append("• ").append(item.base().name()).append(", ").append(item.room().name()).append(", ")
                    .append(formatter.dateTime(booking.start())).append("-")
                    .append(formatter.endTime(booking.start(), booking.end())).append("\n");
        }
        return bookings.isEmpty() ? "У вас пока нет будущих записей." : text.toString();
    }

    /// Показывает записи клиента с кнопками их отмены.
    ///
    /// @param chatId идентификатор Telegram-чата
    /// @param userId идентификатор клиента
    /// @param bot    бот для отправки сообщений
    public void showMyBookings(long chatId, long userId, Bot bot) {
        List<UpcomingBooking> bookings = upcomingBookings(userId);
        if (bookings.isEmpty()) {
            send(bot, chatId, "У вас пока нет будущих записей.",
                    keyboards.choices(keyboards.button(back, "menu:home")));
            return;
        }
        List<List<Button>> rows = new ArrayList<>();
        for (UpcomingBooking item : bookings) {
            Booking booking = item.booking();
            String callback = "mine:cancel:" + booking.roomId() + ":"
                    + booking.start().format(CALLBACK_START_FORMAT);
            keyboards.addButton(rows, keyboards.button("Отменить • " + item.base().name()
                    + " • " + item.room().name(), callback), 1);
        }
        rows.add(keyboards.row(keyboards.button(back, "menu:home")));
        send(bot, chatId, myBookingsMessage(bookings), keyboards.markup(rows));
    }

    /// Просит клиента подтвердить отмену его будущей записи.
    ///
    /// @param chatId идентификатор Telegram-чата
    /// @param userId идентификатор клиента
    /// @param roomId идентификатор зала
    /// @param start  начало записи
    /// @param bot    бот для отправки сообщений
    public void confirmBookingCancellation(long chatId, long userId, long roomId, LocalDateTime start, Bot bot) {
        UpcomingBooking booking = findUpcomingBooking(userId, roomId, start);
        if (booking == null) {
            send(bot, chatId, "Запись не найдена или уже неактуальна.", backToBookingsKeyboard());
            return;
        }
        String callback = "mine:cancelyes:" + roomId + ":" + start.format(CALLBACK_START_FORMAT);
        send(bot, chatId, "Отменить запись в базе «" + booking.base().name() + "», зал «"
                + booking.room().name() + "» на " + formatter.dateTime(start) + "?",
                keyboards.choices(keyboards.button("Подтвердить отмену", callback),
                        keyboards.button(back, "mine:list")));
    }

    /// Удаляет будущую запись только у клиента, которому она принадлежит.
    ///
    /// @param chatId идентификатор Telegram-чата
    /// @param userId идентификатор клиента
    /// @param roomId идентификатор зала
    /// @param start  начало записи
    /// @param bot    бот для отправки сообщений
    public void cancelBooking(long chatId, long userId, long roomId, LocalDateTime start, Bot bot) {
        if (findUpcomingBooking(userId, roomId, start) == null
                || !bookingRepository.deleteByUserAndRoomAndStart(userId, roomId, start)) {
            send(bot, chatId, "Запись не найдена или уже неактуальна.", backToBookingsKeyboard());
            return;
        }
        send(bot, chatId, "Запись отменена.", List.of());
        showMyBookings(chatId, userId, bot);
    }

    private List<UpcomingBooking> upcomingBookings(long userId) {
        List<UpcomingBooking> upcoming = new ArrayList<>();
        for (Booking booking : bookingRepository.findByUser(userId)) {
            UpcomingBooking item = resolveBooking(booking);
            if (item != null && rules.isUpcoming(item.base(), booking)) {
                upcoming.add(item);
            }
        }
        return upcoming;
    }

    private UpcomingBooking findUpcomingBooking(long userId, long roomId, LocalDateTime start) {
        return bookingRepository.findByUser(userId).stream()
                .filter(booking -> booking.roomId() == roomId && booking.start().equals(start))
                .map(this::resolveBooking)
                .filter(item -> item != null && rules.isUpcoming(item.base(), item.booking()))
                .findFirst()
                .orElse(null);
    }

    private UpcomingBooking resolveBooking(Booking booking) {
        Room room = roomRepository.findById(booking.roomId()).orElse(null);
        Base base = room == null ? null : baseRepository.findById(room.baseId()).orElse(null);
        return base == null ? null : new UpcomingBooking(booking, room, base);
    }

    private List<List<Button>> backToBookingsKeyboard() {
        return keyboards.choices(keyboards.button(back, "mine:list"));
    }

    /// Показывает список баз для просмотра справочной информации.
    ///
    /// @param chatId идентификатор Telegram-чата
    /// @param bot    бот для отправки сообщений
    public void showAboutBases(long chatId, Bot bot) {
        List<Base> bases = baseRepository.findAll();
        String home = "menu:home";
        if (bases.isEmpty()) {
            send(bot, chatId, "Пока нет доступных баз.",
                    keyboards.choices(keyboards.button(back, home)));
            return;
        }
        List<List<Button>> rows = new ArrayList<>();
        for (Base base : bases) {
            keyboards.addButton(rows, keyboards.button(base.name(), "about:base:" + base.id()), 2);
        }
        rows.add(keyboards.row(keyboards.button(back, home)));
        send(bot, chatId, SELECT_BASE, keyboards.markup(rows));
    }

    /// Показывает адрес, расписание и оборудование выбранной базы.
    ///
    /// @param chatId идентификатор Telegram-чата
    /// @param baseId идентификатор базы
    /// @param bot    бот для отправки сообщений
    public void showBaseInformation(long chatId, long baseId, Bot bot) {
        Base base = baseRepository.findById(baseId).orElse(null);
        if (base == null) {
            showAboutBases(chatId, bot);
            return;
        }
        StringBuilder text = new StringBuilder("База «").append(base.name()).append("»\nАдрес: ")
                .append(base.address()).append("\nЧасовой пояс: ").append(base.timezone()).append("\n");
        for (int day = 1; day <= DAYS_IN_WEEK; day++) {
            OpeningHours hours = base.hoursOn(DayOfWeek.of(day)).orElse(null);
            text.append(formatter.dayName(DayOfWeek.of(day))).append(": ")
                    .append(hours == null
                            ? "выходной"
                            : formatter.hour(hours.openHour()) + "-" + formatter.hour(hours.closeHour()))
                    .append(day == DAYS_IN_WEEK ? "\n" : ", ");
        }
        text.append("Оборудование:\n").append(base.equipment().isEmpty() ? "не указано" : base.equipment());
        send(bot, chatId, text.toString(), keyboards.choices(keyboards.button(back, "about:list")));
    }

    /// Отправляет текст и клавиатуру через бот.
    ///
    /// @param bot      транспорт для отправки
    /// @param chatId   идентификатор Telegram-чата
    /// @param text     текст сообщения
    /// @param keyboard клавиатура сообщения
    private void send(Bot bot, long chatId, String text, List<List<Button>> keyboard) {
        bot.sendMessage(new Message(text, null, keyboard), chatId);
    }

    private record UpcomingBooking(Booking booking, Room room, Base base) {
    }
}

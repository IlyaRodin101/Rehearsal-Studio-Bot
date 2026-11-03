package ru.urfu.echobot.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Отвечает за кнопочные этапы выбора базы, зала, даты, времени и длительности бронирования.
public final class BookingSelectionService {
    /// Подпись кнопки возврата.
    private static final String BACK = "Назад";
    /// Callback главного меню для начала бронирования.
    private static final String MENU_BOOK = "menu:book";
    /// Callback главного меню.
    private static final String MENU_HOME = "menu:home";
    /// Callback-префикс выбора базы.
    private static final String BOOK_BASE = "book:base:";
    /// Callback-префикс выбора месяца.
    private static final String BOOK_MONTH = "book:month:";
    /// Callback-префикс переключения страниц месяцев.
    private static final String BOOK_MONTHS = "book:months:";
    /// Callback-префикс выбора недели.
    private static final String BOOK_WEEK = "book:week:";
    /// Callback-префикс выбора дня.
    private static final String BOOK_DAY = "book:day:";
    /// Текст приглашения выбрать базу.
    private static final String SELECT_BASE = "Выберите базу:";
    /// Сообщение об отсутствии доступных дней.
    private static final String NO_DAYS = "В этой неделе нет доступных дней.";
    /// Число месяцев на одной странице.
    private static final int MONTHS_PER_PAGE = 6;
    /// Максимальное число кнопок времени в строке.
    private static final int TIME_BUTTONS_PER_ROW = 4;
    /// Максимальное число кнопок длительности в строке.
    private static final int DURATIONS_PER_ROW = 6;

    /// Репозиторий баз.
    private final BaseRepository baseRepository;
    /// Репозиторий залов.
    private final RoomRepository roomRepository;
    /// Репозиторий броней.
    private final BookingRepository bookingRepository;
    /// Правила бронирования.
    private final BookingRules rules;
    /// Проверка доступности дат и времени.
    private final BookingAvailabilityService availabilityService;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards;
    /// Форматер дат и времени.
    private final RussianDateFormatter formatter;

    /// Создаёт сервис кнопочного выбора слота.
    ///
    /// @param baseRepository хранилище баз
    /// @param roomRepository хранилище залов
    /// @param bookingRepository хранилище броней
    /// @param rules правила бронирования
    /// @param availabilityService сервис проверки доступности
    /// @param keyboards фабрика клавиатур
    /// @param formatter форматер дат и времени
    public BookingSelectionService(BaseRepository baseRepository, RoomRepository roomRepository,
                                   BookingRepository bookingRepository, BookingRules rules,
                                   BookingAvailabilityService availabilityService,
                                   BookingKeyboardFactory keyboards, RussianDateFormatter formatter) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.rules = rules;
        this.availabilityService = availabilityService;
        this.keyboards = keyboards;
        this.formatter = formatter;
    }

    /// Начинает сценарий бронирования и показывает доступные базы.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    /// @param bot бот для отправки сообщения
    public void beginBooking(long chatId, BookingFlow flow, Bot bot) {
        flow.clearBooking();
        flow.step = BookingFlow.Step.BOOK_BASES;
        List<Base> bases = baseRepository.findAll();
        if (bases.isEmpty()) {
            send(bot, chatId, "Пока нет доступных баз для бронирования.",
                    keyboards.choices(keyboards.button(BACK, MENU_HOME)));
            return;
        }
        List<List<Button>> rows = new ArrayList<>();
        for (Base base : bases) {
            keyboards.addButton(rows, keyboards.button(base.name(), BOOK_BASE + base.id()), 2);
        }
        rows.add(keyboards.row(keyboards.button(BACK, MENU_HOME)));
        send(bot, chatId, SELECT_BASE, keyboards.markup(rows));
    }

    /// Показывает залы выбранной базы.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    /// @param bot бот для отправки сообщения
    public void showRoomChoices(long chatId, BookingFlow flow, Bot bot) {
        flow.step = BookingFlow.Step.BOOK_ROOMS;
        List<Room> rooms = roomRepository.findByBaseId(flow.baseId);
        List<List<Button>> rows = new ArrayList<>();
        for (Room room : rooms) {
            keyboards.addButton(rows, keyboards.button(room.name(), "book:room:" + room.id()), 2);
        }
        rows.add(keyboards.row(keyboards.button(BACK, MENU_BOOK)));
        String text = rooms.isEmpty() ? "В этой базе пока нет доступных залов." : "Выберите зал:";
        send(bot, chatId, text, keyboards.markup(rows));
    }

    /// Показывает месяцы, в которых есть доступное время для бронирования.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    /// @param bot бот для отправки сообщения
    public void showMonthChoices(long chatId, BookingFlow flow, Bot bot) {
        flow.step = BookingFlow.Step.BOOK_MONTH;
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        if (base == null) {
            beginBooking(chatId, flow, bot);
            return;
        }
        YearMonth current = YearMonth.from(rules.today(base));
        List<List<Button>> rows = new ArrayList<>();
        int count = 0;
        for (int offset = 0; offset < MONTHS_PER_PAGE; offset++) {
            YearMonth month = current.plusMonths(flow.monthOffset + offset);
            if (availabilityService.monthHasAvailability(base, flow.roomId, month)) {
                keyboards.addButton(rows, keyboards.button(formatter.monthYear(month), BOOK_MONTH + month), 2);
                count++;
            }
        }
        List<Button> navigation = new ArrayList<>();
        if (flow.monthOffset > 0) {
            navigation.add(keyboards.button("Предыдущие", BOOK_MONTHS
                    + Math.max(0, flow.monthOffset - MONTHS_PER_PAGE)));
        }
        navigation.add(keyboards.button("Следующие", BOOK_MONTHS + (flow.monthOffset + MONTHS_PER_PAGE)));
        rows.add(navigation);
        rows.add(keyboards.row(keyboards.button(BACK, BOOK_BASE + base.id())));
        String text = count == 0 ? "В этих месяцах нет доступных дат." : "Выберите месяц:";
        send(bot, chatId, text, keyboards.markup(rows));
    }

    /// Показывает недели выбранного месяца с доступными днями.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    /// @param month выбранный месяц
    /// @param bot бот для отправки сообщения
    public void showWeekChoices(long chatId, BookingFlow flow, YearMonth month, Bot bot) {
        flow.step = BookingFlow.Step.BOOK_WEEK;
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        if (base == null) {
            beginBooking(chatId, flow, bot);
            return;
        }
        LocalDate cursor = month.atDay(1);
        int count = 0;
        List<List<Button>> rows = new ArrayList<>();
        while (cursor.getMonthValue() == month.getMonthValue()) {
            LocalDate end = cursor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            if (end.isAfter(month.atEndOfMonth())) {
                end = month.atEndOfMonth();
            }
            if (availabilityService.rangeHasAvailability(base, flow.roomId, cursor, end)) {
                String label = formatter.weekRange(cursor, end);
                keyboards.addButton(rows, keyboards.button(label, BOOK_WEEK + cursor), 2);
                count++;
            }
            cursor = end.plusDays(1);
        }
        rows.add(keyboards.row(keyboards.button(BACK, BOOK_MONTHS + flow.monthOffset)));
        String text = count == 0 ? NO_DAYS : "Выберите неделю:";
        send(bot, chatId, text, keyboards.markup(rows));
    }

    /// Показывает доступные для бронирования дни выбранной недели.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    /// @param bot бот для отправки сообщения
    public void showDayChoices(long chatId, BookingFlow flow, Bot bot) {
        flow.step = BookingFlow.Step.BOOK_DAY;
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        if (base == null) {
            beginBooking(chatId, flow, bot);
            return;
        }
        LocalDate start = flow.selectedWeek;
        LocalDate end = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        if (end.getMonthValue() != start.getMonthValue()) {
            end = start.withDayOfMonth(start.lengthOfMonth());
        }
        List<List<Button>> rows = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (availabilityService.dateHasAvailability(base, flow.roomId, date)) {
                String label = formatter.dayAndDate(date);
                keyboards.addButton(rows, keyboards.button(label, BOOK_DAY + date), 2);
            }
        }
        rows.add(keyboards.row(keyboards.button(BACK, BOOK_MONTH + flow.selectedMonth)));
        String text = rows.size() == 1 ? NO_DAYS : "Выберите день:";
        send(bot, chatId, text, keyboards.markup(rows));
    }

    /// Показывает свободное время для выбранного дня.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    /// @param bot бот для отправки сообщения
    public void showTimeChoices(long chatId, BookingFlow flow, Bot bot) {
        flow.step = BookingFlow.Step.BOOK_TIME;
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        Room room = roomRepository.findById(flow.roomId).orElse(null);
        List<List<Button>> rows = new ArrayList<>();
        if (base != null && room != null) {
            for (int hour : availabilityService.availableStarts(base, room, flow.bookingDate)) {
                keyboards.addButton(rows,
                        keyboards.button(formatter.hour(hour), "book:time:" + flow.bookingDate + ":" + hour),
                        TIME_BUTTONS_PER_ROW);
            }
        }
        rows.add(keyboards.row(keyboards.button(BACK, BOOK_WEEK + flow.selectedWeek)));
        String text = rows.size() == 1 ? "На этот день свободного времени нет." : "Выберите время начала:";
        send(bot, chatId, text, keyboards.markup(rows));
    }

    /// Показывает доступную длительность бронирования для выбранного времени начала.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    /// @param bot бот для отправки сообщения
    public void showDurationChoices(long chatId, BookingFlow flow, Bot bot) {
        flow.step = BookingFlow.Step.BOOK_DURATION;
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        OpeningHours hours = base == null ? null : base.hoursOn(flow.bookingDate.getDayOfWeek()).orElse(null);
        List<List<Button>> rows = new ArrayList<>();
        if (hours != null) {
            LocalDateTime start = flow.bookingDate.atTime(flow.startHour, 0);
            for (int duration = 1; duration <= hours.closeHour() - flow.startHour; duration++) {
                if (!bookingRepository.hasConflict(flow.roomId, start, start.plusHours(duration))) {
                    keyboards.addButton(rows,
                            keyboards.button(String.valueOf(duration), "book:hours:" + duration), DURATIONS_PER_ROW);
                }
            }
        }
        rows.add(keyboards.row(keyboards.button(BACK, BOOK_DAY + flow.bookingDate)));
        String text = rows.size() == 1
                ? "До закрытия или свободного времени зала осталось меньше часа."
                : "Сколько часов?";
        send(bot, chatId, text, keyboards.markup(rows));
    }

    /// Отправляет текстовое сообщение с клавиатурой через переданного бота.
    ///
    /// @param bot транспорт для отправки сообщения
    /// @param chatId идентификатор чата получателя
    /// @param text текст сообщения
    /// @param keyboard строки кнопок или null без клавиатуры
    private void send(Bot bot, long chatId, String text, List<List<Button>> keyboard) {
        List<List<Button>> safeKeyboard = keyboard == null ? List.of() : keyboard;
        bot.sendMessage(new Message(text, null, safeKeyboard), chatId);
    }
}

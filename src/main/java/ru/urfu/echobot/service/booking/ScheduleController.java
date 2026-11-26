package ru.urfu.echobot.service.booking;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.service.Button;
import ru.urfu.echobot.service.Message;
import ru.urfu.echobot.service.StaffAccess;
import ru.urfu.echobot.service.UserDirectory;

/// Показывает администраторам и владельцам расписание записей и управляет отменой.
public final class ScheduleController {
    /// Сообщение о недоступном действии.
    private static final String UNAVAILABLE = "Действие недоступно.";
    /// Сообщение о недоступной базе.
    private static final String BASE_UNAVAILABLE = "База больше недоступна.";
    /// Сообщение об отсутствующей записи.
    private static final String NOT_FOUND = "Запись не найдена или уже отменена.";
    /// Сообщение о попытке отменить начавшуюся запись.
    private static final String ALREADY_STARTED = "Запись уже началась, отменить её нельзя.";
    /// Сообщение об успешной отмене.
    private static final String CANCELLED = "Запись отменена.";
    /// Приглашение выбрать базу.
    private static final String SELECT_BASE = "Выберите базу:";
    /// Приглашение выбрать месяц.
    private static final String SELECT_MONTH = "Выберите месяц:";
    /// Приглашение выбрать неделю.
    private static final String SELECT_WEEK = "Выберите неделю:";
    /// Приглашение выбрать день.
    private static final String SELECT_DAY = "Выберите день:";
    /// Сообщение об отсутствии записей.
    private static final String NO_BOOKINGS = "На этот день записей нет.";
    /// Вопрос подтверждения отмены.
    private static final String CANCEL_QUESTION = "Отменить запись?";
    /// Подпись кнопки возврата.
    private static final String BACK = "Назад";
    /// Подпись кнопки подтверждения отмены.
    private static final String CANCEL_YES = "Да, отменить";
    /// Подпись кнопки отказа от отмены.
    private static final String CANCEL_NO = "Оставить запись";
    /// Подпись кнопки отмены единственной записи.
    private static final String CANCEL_ONE = "Отменить запись";
    /// Начало подписи кнопки отмены одной из нескольких записей.
    private static final String CANCEL_PREFIX = "Отменить ";
    /// Callback главного меню.
    private static final String MENU_HOME = "menu:home";
    /// Callback-префикс выбора базы.
    private static final String SCHEDULE_BASE = "schedule:base:";
    /// Callback-префикс выбора месяца.
    private static final String SCHEDULE_MONTH = "schedule:month:";
    /// Callback-префикс выбора недели.
    private static final String SCHEDULE_WEEK = "schedule:week:";
    /// Callback-префикс выбора дня.
    private static final String SCHEDULE_DAY = "schedule:day:";
    /// Callback-префикс запроса отмены.
    private static final String SCHEDULE_CANCEL = "schedule:cancel:";
    /// Callback-префикс подтверждения отмены.
    private static final String SCHEDULE_CANCEL_YES = "schedule:cancelyes:";
    /// Действие выбора базы.
    private static final String ACTION_BASE = "base";
    /// Действие выбора месяца.
    private static final String ACTION_MONTH = "month";
    /// Действие выбора недели.
    private static final String ACTION_WEEK = "week";
    /// Действие выбора дня.
    private static final String ACTION_DAY = "day";
    /// Действие запроса отмены.
    private static final String ACTION_CANCEL = "cancel";
    /// Действие подтверждения отмены.
    private static final String ACTION_CANCEL_YES = "cancelyes";
    /// Формат времени начала в callback-кнопках.
    private static final String START_PATTERN = "uuuuMMddHHmm";
    /// Позиция идентификатора базы в callback-команде.
    private static final int BASE_PART = 2;
    /// Позиция идентификатора зала или даты в callback-команде.
    private static final int DATA_PART = 3;
    /// Позиция ключа времени начала в callback-команде.
    private static final int START_PART = 4;
    /// Количество кнопок баз в строке.
    private static final int BUTTONS_PER_ROW = 2;
    /// Смещение последнего дня недели от понедельника.
    private static final int LAST_DAY_OFFSET = 6;
    /// Разделитель частей callback-команды.
    private static final String COLON = ":";
    /// Разделитель деталей записи.
    private static final String COMMA = ", ";
    /// Разделитель строк сообщения.
    private static final String NEWLINE = "\n";
    /// Открывающая кавычка названия базы.
    private static final String BASE_OPEN = "База «";
    /// Закрывающая кавычка названия базы.
    private static final String BASE_CLOSE = "»";
    /// Начало заголовка расписания дня.
    private static final String DAY_HEADER = "Расписание базы «";
    /// Продолжение заголовка расписания дня.
    private static final String DAY_HEADER_CONTINUATION = "» на ";
    /// Начало части строки записи с комментарием.
    private static final String COMMENT_PREFIX = ", комм.: ";
    /// Завершение предложения.
    private static final String FULL_STOP = ".";

    /// Данные расписания.
    private final ScheduleService scheduleService;
    /// Отмена записей персоналом.
    private final ScheduleCancellationService cancellationService;
    /// Права владельцев и администраторов.
    private final StaffAccess staffAccess;
    /// Справочник известных пользователей и администраторов.
    private final UserDirectory userDirectory;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards = new BookingKeyboardFactory();
    /// Форматер дат и времени.
    private final RussianDateFormatter formatter = new RussianDateFormatter();
    /// Формат времени начала в callback-кнопках.
    private final DateTimeFormatter startFormat = DateTimeFormatter.ofPattern(START_PATTERN);

    /// Создаёт контроллер расписания.
    ///
    /// @param scheduleService     данные расписания
    /// @param cancellationService отмена записей персоналом
    /// @param staffAccess         права владельцев и администраторов
    /// @param userDirectory       справочник известных пользователей и администраторов
    public ScheduleController(ScheduleService scheduleService, ScheduleCancellationService cancellationService,
                              StaffAccess staffAccess, UserDirectory userDirectory) {
        this.scheduleService = scheduleService;
        this.cancellationService = cancellationService;
        this.staffAccess = staffAccess;
        this.userDirectory = userDirectory;
    }

    /// Показывает список баз. Вызывается из главного меню.
    ///
    /// @param userId Telegram ID администратора или владельца
    /// @param bot    бот для отправки сообщений
    public void showBases(long userId, Bot bot) {
        if (!staffAccess.isStaff(userId)) {
            sendUnavailable(userId, bot);
            return;
        }
        List<List<Button>> rows = new ArrayList<>();
        for (Base base : scheduleService.bases()) {
            keyboards.addButton(rows, keyboards.button(base.name(), SCHEDULE_BASE + base.id()),
                    BUTTONS_PER_ROW);
        }
        rows.add(keyboards.row(keyboards.button(BACK, MENU_HOME)));
        send(userId, SELECT_BASE, keyboards.markup(rows), bot);
    }

    /// Обрабатывает callback-команды пространства имён `schedule`.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void handle(BookingCallback context, Bot bot) {
        long userId = context.userId();
        if (!staffAccess.isStaff(userId)) {
            sendUnavailable(userId, bot);
            return;
        }
        try {
            switch (context.action()) {
                case ACTION_BASE -> showMonths(userId, Long.parseLong(context.part(BASE_PART)), bot);
                case ACTION_MONTH -> showWeeks(userId, Long.parseLong(context.part(BASE_PART)),
                        YearMonth.parse(context.part(DATA_PART)), bot);
                case ACTION_WEEK -> showDays(userId, Long.parseLong(context.part(BASE_PART)),
                        LocalDate.parse(context.part(DATA_PART)), bot);
                case ACTION_DAY -> showDay(userId, Long.parseLong(context.part(BASE_PART)),
                        LocalDate.parse(context.part(DATA_PART)), bot);
                case ACTION_CANCEL -> requestCancellation(userId, Long.parseLong(context.part(BASE_PART)),
                        Long.parseLong(context.part(DATA_PART)), context.part(START_PART), bot);
                case ACTION_CANCEL_YES -> confirmCancellation(userId,
                        Long.parseLong(context.part(BASE_PART)), Long.parseLong(context.part(DATA_PART)),
                        context.part(START_PART), bot);
                default -> sendUnavailable(userId, bot);
            }
        } catch (NumberFormatException | DateTimeParseException | IndexOutOfBoundsException exception) {
            sendUnavailable(userId, bot);
        }
    }

    /// Показывает месяцы выбранной базы.
    ///
    /// @param userId Telegram ID администратора или владельца
    /// @param baseId идентификатор базы
    /// @param bot    бот для отправки сообщений
    private void showMonths(long userId, long baseId, Bot bot) {
        Base base = scheduleService.findBase(baseId).orElse(null);
        if (base == null) {
            sendBaseUnavailable(userId, bot);
            return;
        }
        List<List<Button>> rows = new ArrayList<>();
        for (YearMonth month : scheduleService.months(base)) {
            keyboards.addButton(rows, keyboards.button(formatter.monthYear(month),
                    SCHEDULE_MONTH + baseId + COLON + month), BUTTONS_PER_ROW);
        }
        rows.add(keyboards.row(keyboards.button(BACK, MENU_HOME)));
        send(userId, SELECT_MONTH, keyboards.markup(rows), bot);
    }

    /// Показывает недели выбранного месяца.
    ///
    /// @param userId Telegram ID администратора или владельца
    /// @param baseId идентификатор базы
    /// @param month  выбранный месяц
    /// @param bot    бот для отправки сообщений
    private void showWeeks(long userId, long baseId, YearMonth month, Bot bot) {
        Base base = scheduleService.findBase(baseId).orElse(null);
        if (base == null) {
            sendBaseUnavailable(userId, bot);
            return;
        }
        LocalDate monthEnd = month.atEndOfMonth();
        List<List<Button>> rows = new ArrayList<>();
        for (LocalDate weekStart : scheduleService.weekStarts(base, month)) {
            LocalDate weekEnd = weekStart.plusDays(LAST_DAY_OFFSET);
            if (weekEnd.isAfter(monthEnd)) {
                weekEnd = monthEnd;
            }
            keyboards.addButton(rows, keyboards.button(formatter.weekRange(weekStart, weekEnd),
                    SCHEDULE_WEEK + baseId + COLON + weekStart), BUTTONS_PER_ROW);
        }
        rows.add(keyboards.row(keyboards.button(BACK, SCHEDULE_BASE + baseId)));
        send(userId, SELECT_WEEK, keyboards.markup(rows), bot);
    }

    /// Показывает дни выбранной недели.
    ///
    /// @param userId    Telegram ID администратора или владельца
    /// @param baseId    идентификатор базы
    /// @param weekStart первый день недели
    /// @param bot       бот для отправки сообщений
    private void showDays(long userId, long baseId, LocalDate weekStart, Bot bot) {
        Base base = scheduleService.findBase(baseId).orElse(null);
        if (base == null) {
            sendBaseUnavailable(userId, bot);
            return;
        }
        List<List<Button>> rows = new ArrayList<>();
        for (LocalDate date : scheduleService.days(weekStart)) {
            keyboards.addButton(rows, keyboards.button(formatter.dayAndDate(date),
                    SCHEDULE_DAY + baseId + COLON + date), BUTTONS_PER_ROW);
        }
        rows.add(keyboards.row(keyboards.button(BACK,
                SCHEDULE_MONTH + baseId + COLON + YearMonth.from(weekStart))));
        send(userId, SELECT_DAY, keyboards.markup(rows), bot);
    }

    /// Показывает расписание выбранного дня.
    ///
    /// @param userId Telegram ID администратора или владельца
    /// @param baseId идентификатор базы
    /// @param date   выбранный день
    /// @param bot    бот для отправки сообщений
    private void showDay(long userId, long baseId, LocalDate date, Bot bot) {
        Base base = scheduleService.findBase(baseId).orElse(null);
        if (base == null) {
            sendBaseUnavailable(userId, bot);
            return;
        }
        ScheduleDay day = scheduleService.day(base, date);
        String backCallback = SCHEDULE_WEEK + baseId + COLON + mondayOf(date);
        if (day.isEmpty()) {
            send(userId, NO_BOOKINGS, keyboards.choices(keyboards.button(BACK, backCallback)), bot);
        } else {
            List<List<Button>> rows = cancelRows(day);
            rows.add(keyboards.row(keyboards.button(BACK, backCallback)));
            send(userId, dayText(day), keyboards.markup(rows), bot);
        }
    }

    /// Запрашивает у персонала подтверждение отмены записи.
    ///
    /// @param userId   Telegram ID администратора или владельца
    /// @param baseId   идентификатор базы
    /// @param roomId   идентификатор зала
    /// @param startKey ключ времени начала из callback-кнопки
    /// @param bot      бот для отправки сообщений
    private void requestCancellation(long userId, long baseId, long roomId, String startKey, Bot bot) {
        Base base = scheduleService.findBase(baseId).orElse(null);
        Room room = scheduleService.findRoom(roomId).orElse(null);
        LocalDateTime start = LocalDateTime.parse(startKey, startFormat);
        Booking booking = scheduleService.findBooking(roomId, start).orElse(null);
        if (base == null || room == null || booking == null || room.baseId() != base.id()) {
            send(userId, NOT_FOUND, keyboards.choices(keyboards.button(BACK, MENU_HOME)), bot);
            return;
        }
        String backCallback = SCHEDULE_DAY + baseId + COLON + start.toLocalDate();
        if (!scheduleService.isUpcoming(base, booking)) {
            send(userId, ALREADY_STARTED, keyboards.choices(keyboards.button(BACK, backCallback)), bot);
        } else {
            String confirmCallback = SCHEDULE_CANCEL_YES + baseId + COLON + roomId + COLON + startKey;
            send(userId, confirmationText(base, room, booking),
                    keyboards.choices(keyboards.button(CANCEL_YES, confirmCallback),
                            keyboards.button(CANCEL_NO, backCallback)), bot);
        }
    }

    /// Подтверждает отмену записи и возвращает персонал в расписание дня.
    ///
    /// @param userId   Telegram ID администратора или владельца
    /// @param baseId   идентификатор базы
    /// @param roomId   идентификатор зала
    /// @param startKey ключ времени начала из callback-кнопки
    /// @param bot      бот для отправки сообщений
    private void confirmCancellation(long userId, long baseId, long roomId, String startKey, Bot bot) {
        LocalDateTime start = LocalDateTime.parse(startKey, startFormat);
        if (cancellationService.cancel(userId, baseId, roomId, startKey, bot)) {
            send(userId, CANCELLED, List.of(), bot);
            showDay(userId, baseId, start.toLocalDate(), bot);
        }
    }

    /// Создаёт кнопки отмены для будущих записей дня.
    ///
    /// @param day расписание дня
    /// @return строки кнопок отмены
    private List<List<Button>> cancelRows(ScheduleDay day) {
        List<List<Button>> rows = new ArrayList<>();
        int cancellable = cancellableCount(day);
        for (RoomSchedule roomSchedule : day.rooms()) {
            for (Booking booking : roomSchedule.bookings()) {
                if (scheduleService.isUpcoming(day.base(), booking)) {
                    rows.add(keyboards.row(keyboards.button(
                            cancelLabel(roomSchedule.room(), booking, cancellable),
                            cancelCallback(day.base().id(), roomSchedule.room().id(), booking))));
                }
            }
        }
        return rows;
    }

    /// Считает будущие записи дня.
    ///
    /// @param day расписание дня
    /// @return количество записей, которые можно отменить
    private int cancellableCount(ScheduleDay day) {
        int count = 0;
        for (RoomSchedule roomSchedule : day.rooms()) {
            for (Booking booking : roomSchedule.bookings()) {
                if (scheduleService.isUpcoming(day.base(), booking)) {
                    count++;
                }
            }
        }
        return count;
    }

    /// Формирует подпись кнопки отмены записи.
    ///
    /// @param room        зал записи
    /// @param booking     запись
    /// @param cancellable общее число отменяемых записей дня
    /// @return подпись кнопки
    private String cancelLabel(Room room, Booking booking, int cancellable) {
        if (cancellable == 1) {
            return CANCEL_ONE;
        }
        return CANCEL_PREFIX + formatter.timeRange(booking.start(), booking.end()) + COMMA + room.name();
    }

    /// Формирует callback-команду отмены записи.
    ///
    /// @param baseId  идентификатор базы
    /// @param roomId  идентификатор зала
    /// @param booking запись
    /// @return callback-команда отмены
    private String cancelCallback(long baseId, long roomId, Booking booking) {
        return SCHEDULE_CANCEL + baseId + COLON + roomId + COLON + booking.start().format(startFormat);
    }

    /// Формирует текст расписания дня.
    ///
    /// @param day расписание дня
    /// @return текст для сообщения
    private String dayText(ScheduleDay day) {
        StringBuilder text = new StringBuilder(DAY_HEADER).append(day.base().name())
                .append(DAY_HEADER_CONTINUATION).append(formatter.dayAndDate(day.date()))
                .append(COLON).append(NEWLINE);
        for (RoomSchedule roomSchedule : day.rooms()) {
            text.append(roomSchedule.room().name()).append(COLON).append(NEWLINE);
            for (Booking booking : roomSchedule.bookings()) {
                text.append(bookingLine(booking)).append(NEWLINE);
            }
        }
        return text.toString();
    }

    /// Формирует строку записи для расписания дня.
    ///
    /// @param booking запись
    /// @return строка с временем, именем, телефоном и комментарием
    private String bookingLine(Booking booking) {
        StringBuilder line = new StringBuilder()
                .append(formatter.timeRange(booking.start(), booking.end()))
                .append(COMMA).append(booking.customerName())
                .append(COMMA).append(booking.phone());
        if (!booking.comment().isEmpty()) {
            line.append(COMMENT_PREFIX).append(booking.comment());
        }
        return line.toString();
    }

    /// Формирует текст подтверждения отмены.
    ///
    /// @param base    база записи
    /// @param room    зал записи
    /// @param booking отменяемая запись
    /// @return текст подтверждения
    private String confirmationText(Base base, Room room, Booking booking) {
        return CANCEL_QUESTION + NEWLINE + BASE_OPEN + base.name() + BASE_CLOSE + COMMA + room.name()
                + COMMA + formatter.dateTime(booking.start()) + "-"
                + formatter.endTime(booking.start(), booking.end()) + FULL_STOP;
    }

    /// Возвращает понедельник недели указанной даты.
    ///
    /// @param date дата
    /// @return понедельник этой недели
    private LocalDate mondayOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /// Сообщает, что выбранная база больше недоступна.
    ///
    /// @param userId Telegram ID администратора или владельца
    /// @param bot    бот для отправки сообщений
    private void sendBaseUnavailable(long userId, Bot bot) {
        send(userId, BASE_UNAVAILABLE, keyboards.choices(keyboards.button(BACK, MENU_HOME)), bot);
    }

    /// Показывает сообщение о недоступном действии.
    ///
    /// @param userId Telegram ID администратора или владельца
    /// @param bot    бот для отправки сообщений
    private void sendUnavailable(long userId, Bot bot) {
        send(userId, UNAVAILABLE, List.of(), bot);
    }

    /// Отправляет текст и клавиатуру через бота.
    ///
    /// @param chatId   идентификатор чата
    /// @param text     текст сообщения
    /// @param keyboard клавиатура сообщения
    /// @param bot      бот для отправки сообщений
    private void send(long chatId, String text, List<List<Button>> keyboard, Bot bot) {
        bot.sendMessage(new Message(text, null, keyboard), chatId);
    }
}

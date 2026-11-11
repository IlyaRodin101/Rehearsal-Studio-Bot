package ru.urfu.echobot.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.repository.BaseRepository;

/// Показывает кнопки выбора месяца, недели и дня бронирования.
public final class BookingDateSelectionService {
    /// Callback-префикс выбора месяца.
    private static final String BOOK_MONTH = "book:month:";
    /// Callback-префикс переключения страниц месяцев.
    private static final String BOOK_MONTHS = "book:months:";
    /// Callback-префикс выбора недели.
    private static final String BOOK_WEEK = "book:week:";
    /// Callback-префикс выбора дня.
    private static final String BOOK_DAY = "book:day:";
    /// Подпись кнопки возврата.
    private static final String BACK = "Назад";
    /// Сообщение об отсутствии доступных дней.
    private static final String NO_DAYS = "В этой неделе нет доступных дней.";
    /// Число месяцев на одной странице.
    private static final int MONTHS_PER_PAGE = 6;

    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Проверка доступности дат.
    private final BookingAvailabilityService availabilityService;
    /// Правила бронирования.
    private final BookingRules rules;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards;
    /// Форматер дат.
    private final RussianDateFormatter formatter;

    /// Создаёт сервис выбора даты.
    ///
    /// @param baseRepository      хранилище баз
    /// @param availabilityService проверка доступности
    /// @param rules               правила бронирования
    /// @param keyboards           фабрика клавиатур
    /// @param formatter           форматер дат
    public BookingDateSelectionService(BaseRepository baseRepository,
                                       BookingAvailabilityService availabilityService,
                                       BookingRules rules, BookingKeyboardFactory keyboards,
                                       RussianDateFormatter formatter) {
        this.baseRepository = baseRepository;
        this.availabilityService = availabilityService;
        this.rules = rules;
        this.keyboards = keyboards;
        this.formatter = formatter;
    }

    /// Показывает месяцы, в которых есть доступное время.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщения
    public void showMonthChoices(long chatId, BookingFlow flow, Bot bot) {
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        if (base == null) {
            sendUnavailable(chatId, bot);
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
        addMonthNavigation(rows, flow);
        rows.add(keyboards.row(keyboards.button(BACK, "book:base:" + base.id())));
        String text = count == 0 ? "В этих месяцах нет доступных дат." : "Выберите месяц:";
        send(bot, chatId, text, keyboards.markup(rows));
    }

    /// Показывает недели выбранного месяца.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param month  выбранный месяц
    /// @param bot    бот для отправки сообщения
    public void showWeekChoices(long chatId, BookingFlow flow, YearMonth month, Bot bot) {
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        if (base == null) {
            sendUnavailable(chatId, bot);
            return;
        }
        LocalDate cursor = month.atDay(1);
        int count = 0;
        List<List<Button>> rows = new ArrayList<>();
        while (cursor.getMonthValue() == month.getMonthValue()) {
            LocalDate end = weekEnd(cursor, month.atEndOfMonth());
            if (availabilityService.rangeHasAvailability(base, flow.roomId, cursor, end)) {
                keyboards.addButton(rows, keyboards.button(formatter.weekRange(cursor, end), BOOK_WEEK + cursor), 2);
                count++;
            }
            cursor = end.plusDays(1);
        }
        rows.add(keyboards.row(keyboards.button(BACK, BOOK_MONTHS + flow.monthOffset)));
        String text = count == 0 ? NO_DAYS : "Выберите неделю:";
        send(bot, chatId, text, keyboards.markup(rows));
    }

    /// Показывает доступные дни выбранной недели.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщения
    public void showDayChoices(long chatId, BookingFlow flow, Bot bot) {
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        if (base == null) {
            sendUnavailable(chatId, bot);
            return;
        }
        LocalDate start = flow.selectedWeek;
        LocalDate end = weekEnd(start, start.withDayOfMonth(start.lengthOfMonth()));
        List<List<Button>> rows = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (availabilityService.dateHasAvailability(base, flow.roomId, date)) {
                keyboards.addButton(rows, keyboards.button(formatter.dayAndDate(date), BOOK_DAY + date), 2);
            }
        }
        rows.add(keyboards.row(keyboards.button(BACK, BOOK_MONTH + flow.selectedMonth)));
        String text = rows.size() == 1 ? NO_DAYS : "Выберите день:";
        send(bot, chatId, text, keyboards.markup(rows));
    }

    /// Добавляет кнопки переключения страниц месяцев.
    ///
    /// @param rows строки клавиатуры
    /// @param flow состояние диалога
    private void addMonthNavigation(List<List<Button>> rows, BookingFlow flow) {
        List<Button> navigation = new ArrayList<>();
        if (flow.monthOffset > 0) {
            navigation.add(keyboards.button("Предыдущие", BOOK_MONTHS
                    + Math.max(0, flow.monthOffset - MONTHS_PER_PAGE)));
        }
        navigation.add(keyboards.button("Следующие", BOOK_MONTHS + (flow.monthOffset + MONTHS_PER_PAGE)));
        rows.add(navigation);
    }

    /// Возвращает последний день недели, не выходя за границу месяца.
    ///
    /// @param start      первый день недели
    /// @param monthLimit последний день месяца
    /// @return последний день недели
    private LocalDate weekEnd(LocalDate start, LocalDate monthLimit) {
        LocalDate sunday = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return sunday.isAfter(monthLimit) ? monthLimit : sunday;
    }

    /// Сообщает, что выбранная база больше недоступна.
    ///
    /// @param chatId идентификатор чата
    /// @param bot    бот для отправки сообщения
    private void sendUnavailable(long chatId, Bot bot) {
        send(bot, chatId, "База больше недоступна. Начните бронирование заново.",
                keyboards.choices(keyboards.button(BACK, "menu:home")));
    }

    /// Отправляет сообщение с клавиатурой.
    ///
    /// @param bot      бот для отправки
    /// @param chatId   идентификатор чата
    /// @param text     текст сообщения
    /// @param keyboard строки кнопок
    private void send(Bot bot, long chatId, String text, List<List<Button>> keyboard) {
        bot.sendMessage(new Message(text, null, keyboard), chatId);
    }
}

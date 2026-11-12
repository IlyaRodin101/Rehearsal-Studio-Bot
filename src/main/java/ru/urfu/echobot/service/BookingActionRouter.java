package ru.urfu.echobot.service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import ru.urfu.echobot.bot.Bot;

/// Направляет callback-команды кнопочного бронирования и раздела «О базе».
public final class BookingActionRouter {
    /// Позиция аргумента в callback-данных.
    private static final int ARGUMENT_PART = 2;
    /// Минимальное число частей callback без аргумента.
    private static final int MINIMAL_PARTS = 2;
    /// Минимальное число частей callback с одним аргументом.
    private static final int ARGUMENT_PARTS = 3;
    /// Минимальное число частей callback выбора времени.
    private static final int TIME_PARTS = 4;
    /// Действие выбора базы.
    private static final String BASE = "base";
    /// Действие списка.
    private static final String LIST = "list";
    /// Действие выбора зала.
    private static final String ROOM = "room";
    /// Действие переключения страницы месяцев.
    private static final String MONTHS = "months";
    /// Действие выбора месяца.
    private static final String MONTH = "month";
    /// Действие выбора недели.
    private static final String WEEK = "week";
    /// Действие выбора дня.
    private static final String DAY = "day";
    /// Действие выбора времени.
    private static final String TIME = "time";
    /// Действие выбора длительности.
    private static final String HOURS = "hours";
    /// Действие пропуска комментария.
    private static final String SKIP = "skip";
    /// Действие подтверждения брони.
    private static final String CONFIRM = "confirm";
    /// Действие отмены.
    private static final String CANCEL = "cancel";

    /// Контроллер кнопочных переходов бронирования.
    private final BookingNavigationController navigation;
    /// Контроллер выбора даты и времени.
    private final BookingSelectionController selection;
    /// Справочная информация о базах.
    private final BaseInformationService baseInformation;
    /// Обработчики действий бронирования.
    private final Map<String, BiConsumer<BookingCallback, Bot>> bookingActions;
    /// Минимальная длина callback для действий бронирования.
    private final Map<String, Integer> bookingPartCounts;
    /// Обработчики действий раздела «О базе».
    private final Map<String, BiConsumer<BookingCallback, Bot>> aboutActions;
    /// Минимальная длина callback для действий раздела «О базе».
    private final Map<String, Integer> aboutPartCounts;

    /// Создаёт маршрутизатор действий бронирования и справки.
    ///
    /// @param navigation      переходы бронирования
    /// @param selection       выбор даты и времени
    /// @param baseInformation справочная информация
    public BookingActionRouter(BookingNavigationController navigation, BookingSelectionController selection,
                               BaseInformationService baseInformation) {
        this.navigation = navigation;
        this.selection = selection;
        this.baseInformation = baseInformation;
        bookingActions = new HashMap<>();
        bookingPartCounts = new HashMap<>();
        aboutActions = new HashMap<>();
        aboutPartCounts = new HashMap<>();
        registerBookingActions();
        aboutActions.put(BASE, (context, bot) -> baseInformation.showBaseInformation(context.chatId(),
                Long.parseLong(context.part(ARGUMENT_PART)), bot));
        aboutActions.put(LIST, (context, bot) ->
                baseInformation.showAboutBases(context.chatId(), bot));
        aboutPartCounts.put(BASE, ARGUMENT_PARTS);
        aboutPartCounts.put(LIST, MINIMAL_PARTS);
    }

    /// Регистрирует действия кнопочного бронирования.
    private void registerBookingActions() {
        bookingActions.put(BASE, navigation::selectBase);
        bookingActions.put(ROOM, navigation::selectRoom);
        bookingActions.put(MONTHS, navigation::selectMonths);
        bookingActions.put(MONTH, navigation::selectMonth);
        bookingActions.put(WEEK, selection::selectWeek);
        bookingActions.put(DAY, selection::selectDay);
        bookingActions.put(TIME, selection::selectTime);
        bookingActions.put(HOURS, selection::selectHours);
        bookingActions.put(SKIP, navigation::skipComment);
        bookingActions.put(CONFIRM, navigation::confirm);
        bookingActions.put(CANCEL, navigation::cancel);
        bookingPartCounts.put(TIME, TIME_PARTS);
        for (String action : bookingActions.keySet()) {
            bookingPartCounts.putIfAbsent(action, MINIMAL_PARTS);
        }
        bookingPartCounts.put(BASE, ARGUMENT_PARTS);
        bookingPartCounts.put(ROOM, ARGUMENT_PARTS);
        bookingPartCounts.put(MONTHS, ARGUMENT_PARTS);
        bookingPartCounts.put(MONTH, ARGUMENT_PARTS);
        bookingPartCounts.put(WEEK, ARGUMENT_PARTS);
        bookingPartCounts.put(DAY, ARGUMENT_PARTS);
        bookingPartCounts.put(HOURS, ARGUMENT_PARTS);
    }

    /// Выбирает действие кнопочного бронирования.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void handleBooking(BookingCallback context, Bot bot) {
        execute(context, bot, bookingPartCounts, bookingActions);
    }

    /// Выбирает действие раздела «О базе».
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void handleAbout(BookingCallback context, Bot bot) {
        execute(context, bot, aboutPartCounts, aboutActions);
    }

    /// Выполняет действие, если длина команды достаточна.
    ///
    /// @param context      callback-команда
    /// @param bot          бот для отправки сообщений
    /// @param partCounts   минимальные длины команд
    /// @param handlers     обработчики действий
    private void execute(BookingCallback context, Bot bot, Map<String, Integer> partCounts,
                         Map<String, BiConsumer<BookingCallback, Bot>> handlers) {
        String action = context.action();
        Integer requiredParts = partCounts.get(action);
        BiConsumer<BookingCallback, Bot> handler = handlers.get(action);
        if (requiredParts != null && context.size() >= requiredParts && handler != null) {
            handler.accept(context, bot);
        }
    }
}

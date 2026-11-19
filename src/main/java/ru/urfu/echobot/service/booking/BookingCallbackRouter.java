package ru.urfu.echobot.service.booking;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.service.owner.OwnerManagementService;

/// Направляет callback-команды подсистемам бронирования, владельцев и справки.
public final class BookingCallbackRouter {
    /// Позиция действия в callback-данных.
    private static final int ACTION_PART = 1;
    /// Позиция аргумента в callback-данных.
    private static final int ARGUMENT_PART = 2;
    /// Минимальное число частей callback без аргумента.
    private static final int MINIMAL_PARTS = 2;
    /// Минимальное число частей callback с одним аргументом.
    private static final int ARGUMENT_PARTS = 3;
    /// Минимальное число частей callback выбора времени.
    private static final int TIME_PARTS = 4;
    /// Пространство имён владельца.
    private static final String OWNER = "owner";
    /// Пространство имён возврата.
    private static final String BACK = "back";
    /// Пространство имён главного меню.
    private static final String MENU = "menu";
    /// Пространство имён и действие бронирования.
    private static final String BOOK = "book";
    /// Пространство имён и действие справки.
    private static final String ABOUT = "about";
    /// Пространство имён записей клиента.
    private static final String MINE = "mine";
    /// Пространство имён «горячих» слотов.
    private static final String HOT = "hot";
    /// Пространство имён подписок.
    private static final String SUB = "sub";
    /// Действие возврата в главное меню.
    private static final String HOME = "home";
    /// Действие перехода к базам владельца.
    private static final String BASES = "bases";
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

    /// Управление базами владельцев.
    private final OwnerManagementService ownerService;
    /// Контроллер кнопочных переходов бронирования.
    private final BookingNavigationController navigation;
    /// Контроллер записей клиента.
    private final BookingClientController clientController;
    /// Справочная информация о базах.
    private final BaseInformationService baseInformation;
    /// Обработчики пространств имён callback-команд.
    private final Map<String, BiConsumer<BookingCallback, Bot>> namespaces;
    /// Обработчики действий главного меню.
    private final Map<String, BiConsumer<BookingCallback, Bot>> menuActions;
    /// Обработчики действий бронирования.
    private final Map<String, BiConsumer<BookingCallback, Bot>> bookingActions;
    /// Минимальная длина callback для действий бронирования.
    private final Map<String, Integer> bookingPartCounts;
    /// Обработчики действий раздела «О базе».
    private final Map<String, BiConsumer<BookingCallback, Bot>> aboutActions;
    /// Минимальная длина callback для действий раздела «О базе».
    private final Map<String, Integer> aboutPartCounts;

    /// Создаёт маршрутизатор callback-команд.
    ///
    /// @param ownerService     управление базами владельцев
    /// @param menuController   главное меню и возврат
    /// @param navigation       переходы бронирования
    /// @param clientController записи клиента
    /// @param baseInformation  справочная информация
    public BookingCallbackRouter(OwnerManagementService ownerService, BookingMenuController menuController,
                                 BookingNavigationController navigation,
                                 BookingClientController clientController,
                                 BaseInformationService baseInformation) {
        this.ownerService = ownerService;
        this.navigation = navigation;
        this.clientController = clientController;
        this.baseInformation = baseInformation;
        namespaces = new HashMap<>();
        menuActions = new HashMap<>();
        bookingActions = new HashMap<>();
        bookingPartCounts = new HashMap<>();
        aboutActions = new HashMap<>();
        aboutPartCounts = new HashMap<>();
        registerNamespaces(menuController);
        registerMenuActions(menuController);
        registerBookingActions();
        registerAboutActions();
    }

    /// Обрабатывает callback-команду нажатой кнопки.
    ///
    /// @param data   данные кнопки
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщений
    public void handle(String data, long chatId, BookingStep flow, Bot bot) {
        String[] parts = data.split(":");
        if (OWNER.equals(parts[0])) {
            ownerService.handleCallback(chatId, parts, bot);
        } else {
            BookingCallback context = new BookingCallback(chatId, chatId, flow, parts);
            BiConsumer<BookingCallback, Bot> handler = namespaces.get(parts[0]);
            if (handler != null) {
                handler.accept(context, bot);
            }
        }
    }

    /// Регистрирует обработчики пространств имён callback-команд.
    ///
    /// @param menuController главное меню и возврат
    private void registerNamespaces(BookingMenuController menuController) {
        namespaces.put(BACK, (context, bot) -> menuController.back(context.chatId(), context.flow(), bot));
        namespaces.put(MENU, this::handleMenuAction);
        namespaces.put(BOOK, this::handleBooking);
        namespaces.put(ABOUT, this::handleAbout);
        namespaces.put(MINE, clientController::handle);
    }

    /// Регистрирует обработчики действий главного меню.
    ///
    /// @param menuController главное меню
    private void registerMenuActions(BookingMenuController menuController) {
        menuActions.put(BOOK, (context, bot) ->
                navigation.beginBooking(context.chatId(), context.flow(), bot));
        menuActions.put(MINE, (context, bot) ->
                clientController.showList(context.chatId(), context.userId(), bot));
        menuActions.put(ABOUT, (context, bot) ->
                baseInformation.showAboutBases(context.chatId(), bot));
        menuActions.put(BASES, (context, bot) ->
                ownerService.showBaseList(context.userId(), bot));
        menuActions.put(HOME, (context, bot) ->
                menuController.returnToMenu(context.chatId(), context.flow(), bot));
    }

    /// Подключает обработчики «горячих» слотов и подписок.
    ///
    /// @param hotController          контроллер «горячих» слотов
    /// @param subscriptionController контроллер подписок
    void registerHotSlots(HotSlotController hotController, SubscriptionController subscriptionController) {
        namespaces.put(HOT, hotController::handle);
        namespaces.put(SUB, subscriptionController::handle);
        menuActions.put(HOT, (context, bot) ->
                hotController.showBaseList(context.chatId(), context.flow(), bot));
        menuActions.put(SUB, (context, bot) ->
                subscriptionController.showList(context.chatId(), context.userId(), bot));
    }

    /// Регистрирует действия кнопочного бронирования.
    private void registerBookingActions() {
        bookingActions.put(BASE, navigation::selectBase);
        bookingActions.put(ROOM, navigation::selectRoom);
        bookingActions.put(MONTHS, navigation::selectMonths);
        bookingActions.put(MONTH, navigation::selectMonth);
        bookingActions.put(WEEK, navigation::selectWeek);
        bookingActions.put(DAY, navigation::selectDay);
        bookingActions.put(TIME, navigation::selectTime);
        bookingActions.put(HOURS, navigation::selectHours);
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

    /// Регистрирует действия раздела «О базе».
    private void registerAboutActions() {
        aboutActions.put(BASE, (context, bot) -> baseInformation.showBaseInformation(context.chatId(),
                Long.parseLong(context.part(ARGUMENT_PART)), bot));
        aboutActions.put(LIST, (context, bot) ->
                baseInformation.showAboutBases(context.chatId(), bot));
        aboutPartCounts.put(BASE, ARGUMENT_PARTS);
        aboutPartCounts.put(LIST, MINIMAL_PARTS);
    }

    /// Выбирает действие главного меню.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    private void handleMenuAction(BookingCallback context, Bot bot) {
        ownerService.cancel(context.userId());
        BiConsumer<BookingCallback, Bot> handler = context.size() < MINIMAL_PARTS
                ? null : menuActions.get(context.part(ACTION_PART));
        if (handler == null) {
            menuActions.get(HOME).accept(context, bot);
        } else {
            handler.accept(context, bot);
        }
    }

    /// Выбирает действие кнопочного бронирования.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    private void handleBooking(BookingCallback context, Bot bot) {
        execute(context, bot, bookingPartCounts, bookingActions);
    }

    /// Выбирает действие раздела «О базе».
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    private void handleAbout(BookingCallback context, Bot bot) {
        execute(context, bot, aboutPartCounts, aboutActions);
    }

    /// Выполняет действие, если длина команды достаточна.
    ///
    /// @param context    callback-команда
    /// @param bot        бот для отправки сообщений
    /// @param partCounts минимальные длины команд
    /// @param handlers   обработчики действий
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

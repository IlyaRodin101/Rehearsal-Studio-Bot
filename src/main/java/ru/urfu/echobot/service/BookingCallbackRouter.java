package ru.urfu.echobot.service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import ru.urfu.echobot.bot.Bot;

/// Направляет callback-команды подсистемам бронирования, владельцев и справки.
public final class BookingCallbackRouter {
    /// Позиция действия в callback-данных.
    private static final int ACTION_PART = 1;
    /// Минимальное число частей callback без аргумента.
    private static final int MINIMAL_PARTS = 2;
    /// Пространство имён владельца.
    private static final String OWNER = "owner";
    /// Пространство имён возврата.
    private static final String BACK = "back";
    /// Пространство имён главного меню.
    private static final String MENU = "menu";
    /// Пространство имён бронирования и начало его callback.
    private static final String BOOK = "book";
    /// Пространство имён справки и начало её callback.
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

    /// Управление базами владельцев.
    private final OwnerManagementService ownerService;
    /// Контроллер кнопочных переходов бронирования.
    private final BookingNavigationController navigation;
    /// Контроллер записей клиента.
    private final BookingClientController clientController;
    /// Контроллер возврата.
    private final BookingBackController backController;
    /// Справочная информация о базах.
    private final BaseInformationService baseInformation;
    /// Маршрутизатор действий бронирования и справки.
    private final BookingActionRouter actionRouter;
    /// Обработчики пространств имён callback-команд.
    private final Map<String, BiConsumer<BookingCallback, Bot>> namespaces;
    /// Обработчики действий главного меню.
    private final Map<String, BiConsumer<BookingCallback, Bot>> menuActions;

    /// Создаёт маршрутизатор callback-команд.
    ///
    /// @param ownerService     управление базами владельцев
    /// @param menuController   главное меню
    /// @param navigation       переходы бронирования
    /// @param selection        выбор даты и времени
    /// @param clientController записи клиента
    /// @param backController   возврат назад
    /// @param baseInformation  справочная информация
    public BookingCallbackRouter(OwnerManagementService ownerService, BookingMenuController menuController,
                                 BookingNavigationController navigation, BookingSelectionController selection,
                                 BookingClientController clientController, BookingBackController backController,
                                 BaseInformationService baseInformation) {
        this.ownerService = ownerService;
        this.navigation = navigation;
        this.clientController = clientController;
        this.backController = backController;
        this.baseInformation = baseInformation;
        actionRouter = new BookingActionRouter(navigation, selection, baseInformation);
        namespaces = new HashMap<>();
        menuActions = new HashMap<>();
        namespaces.put(BACK, (context, bot) ->
                backController.back(context.chatId(), context.flow(), bot));
        namespaces.put(MENU, this::handleMenuAction);
        namespaces.put(BOOK, actionRouter::handleBooking);
        namespaces.put(ABOUT, actionRouter::handleAbout);
        namespaces.put(MINE, clientController::handle);
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

    /// Обрабатывает callback-команду нажатой кнопки.
    ///
    /// @param data   данные кнопки
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщений
    public void handle(String data, long chatId, BookingFlow flow, Bot bot) {
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
}

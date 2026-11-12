package ru.urfu.echobot.service;

import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;

/// Показывает главное меню и выполняет общий сброс диалога бронирования.
public final class BookingMenuController {
    /// Callback начала бронирования.
    private static final String MENU_BOOK = "menu:book";
    /// Callback списка записей клиента.
    private static final String MENU_MINE = "menu:mine";
    /// Callback раздела «О базе».
    private static final String MENU_ABOUT = "menu:about";
    /// Callback раздела «Горячие слоты».
    private static final String MENU_HOT = "menu:hot";
    /// Callback раздела «Подписка».
    private static final String MENU_SUB = "menu:sub";
    /// Callback раздела управления базами владельца.
    private static final String MENU_BASES = "menu:bases";
    /// Текст главного меню.
    private static final String GREETING = "Здравствуйте! Что хотите сделать?";

    /// Управляет доступом владельцев к настройкам баз.
    private final OwnerManagementService ownerService;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards;
    /// Автомат состояний диалога бронирования.
    private final BookingStateMachine stateMachine;

    /// Создаёт контроллер главного меню.
    ///
    /// @param ownerService управление владельцами баз
    /// @param keyboards    фабрика клавиатур
    /// @param stateMachine автомат состояний бронирования
    public BookingMenuController(OwnerManagementService ownerService, BookingKeyboardFactory keyboards,
                                 BookingStateMachine stateMachine) {
        this.ownerService = ownerService;
        this.keyboards = keyboards;
        this.stateMachine = stateMachine;
    }

    /// Показывает главное меню с кнопками, доступными пользователю.
    ///
    /// @param chatId идентификатор чата
    /// @param bot    бот для отправки сообщений
    public void showMainMenu(long chatId, Bot bot) {
        List<List<Button>> rows = new ArrayList<>();
        rows.add(keyboards.row(keyboards.button("Забронировать", MENU_BOOK),
                keyboards.button("Мои записи", MENU_MINE)));
        rows.add(keyboards.row(keyboards.button("О базе", MENU_ABOUT)));
        rows.add(keyboards.row(keyboards.button("Горячие слоты", MENU_HOT),
                keyboards.button("Подписка", MENU_SUB)));
        if (ownerService.isOwner(chatId)) {
            rows.add(keyboards.row(keyboards.button("Базы", MENU_BASES)));
        }
        send(chatId, GREETING, rows, bot);
    }

    /// Сбрасывает диалог и возвращает пользователя в главное меню.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщений
    public void returnToMenu(long chatId, BookingFlow flow, Bot bot) {
        ownerService.cancel(chatId);
        flow.clearBooking();
        stateMachine.reset(flow);
        showMainMenu(chatId, bot);
    }

    /// Отправляет уведомление и вслед за ним открывает главное меню.
    ///
    /// @param chatId идентификатор чата
    /// @param notice текст уведомления
    /// @param bot    бот для отправки сообщений
    public void showNoticeAndMenu(long chatId, String notice, Bot bot) {
        send(chatId, notice, List.of(), bot);
        showMainMenu(chatId, bot);
    }

    /// Отправляет текстовое сообщение с клавиатурой.
    ///
    /// @param chatId   идентификатор чата
    /// @param text     текст сообщения
    /// @param keyboard строки кнопок
    /// @param bot      бот для отправки сообщений
    private void send(long chatId, String text, List<List<Button>> keyboard, Bot bot) {
        bot.sendMessage(new Message(text, null, keyboard), chatId);
    }
}

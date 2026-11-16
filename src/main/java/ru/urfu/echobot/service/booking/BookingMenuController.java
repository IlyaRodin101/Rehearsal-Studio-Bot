package ru.urfu.echobot.service.booking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.service.Button;
import ru.urfu.echobot.service.GenericStateMachine;
import ru.urfu.echobot.service.Message;
import ru.urfu.echobot.service.owner.OwnerManagementService;

/// Показывает главное меню и выполняет возврат диалога на предыдущий шаг.
public final class BookingMenuController {
    /// Callback начала бронирования.
    private static final String MENU_BOOK = "menu:book";
    /// Callback списка записей клиента.
    private static final String MENU_MINE = "menu:mine";
    /// Callback раздела «О базе».
    private static final String MENU_ABOUT = "menu:about";
    /// Callback раздела управления базами владельца.
    private static final String MENU_BASES = "menu:bases";
    /// Текст главного меню.
    private static final String GREETING = "Здравствуйте! Что хотите сделать?";

    /// Управляет доступом владельцев к настройкам баз.
    private final OwnerManagementService ownerService;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards = new BookingKeyboardFactory();
    /// Автомат состояний диалога бронирования.
    private final GenericStateMachine<BookingFlow.Step, BookingFlow.Event> stateMachine;
    /// Сервис выбора базы и зала.
    private final BookingBaseSelectionService baseSelection;
    /// Сервис выбора месяца, недели и дня.
    private final BookingDateSelectionService dateSelection;
    /// Сервис выбора времени и длительности.
    private final BookingTimeSelectionService timeSelection;
    /// Сервис формы клиента.
    private final BookingFormService formService;
    /// Сервис комментария и подтверждения.
    private final BookingConfirmationService confirmation;
    /// Обработчики возврата для каждого шага.
    private final Map<BookingFlow.Step, Consumer<BackContext>> backHandlers;

    /// Создаёт контроллер главного меню и возврата.
    ///
    /// @param ownerService управление владельцами баз
    /// @param stateMachine автомат состояний бронирования
    /// @param baseSelection выбор базы и зала
    /// @param dateSelection выбор месяца, недели и дня
    /// @param timeSelection выбор времени и длительности
    /// @param formService   форма клиента
    /// @param confirmation  комментарий и подтверждение
    public BookingMenuController(OwnerManagementService ownerService,
                                 GenericStateMachine<BookingFlow.Step, BookingFlow.Event> stateMachine,
                                 BookingBaseSelectionService baseSelection,
                                 BookingDateSelectionService dateSelection,
                                 BookingTimeSelectionService timeSelection,
                                 BookingFormService formService,
                                 BookingConfirmationService confirmation) {
        this.ownerService = ownerService;
        this.stateMachine = stateMachine;
        this.baseSelection = baseSelection;
        this.dateSelection = dateSelection;
        this.timeSelection = timeSelection;
        this.formService = formService;
        this.confirmation = confirmation;
        backHandlers = new HashMap<>();
        registerBackHandlers();
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
        stateMachine.reset(flow, BookingFlow.Step.MENU);
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

    /// Выполняет возврат с текущего шага диалога.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщений
    public void back(long chatId, BookingFlow flow, Bot bot) {
        Consumer<BackContext> handler = backHandlers.get(flow.step);
        if (handler == null) {
            returnToMenu(chatId, flow, bot);
        } else {
            handler.accept(new BackContext(chatId, flow, bot));
        }
    }

    /// Заполняет таблицу переходов назад для всех шагов диалога.
    private void registerBackHandlers() {
        backHandlers.put(BookingFlow.Step.MENU, context ->
                returnToMenu(context.chatId(), context.flow(), context.bot()));
        backHandlers.put(BookingFlow.Step.BOOK_BASES, context ->
                returnToMenu(context.chatId(), context.flow(), context.bot()));
        backHandlers.put(BookingFlow.Step.BOOK_ROOMS, context -> previous(context,
                () -> baseSelection.showBaseChoices(context.chatId(), context.flow(), context.bot())));
        backHandlers.put(BookingFlow.Step.BOOK_MONTH, context -> previous(context,
                () -> baseSelection.showRoomChoices(context.chatId(), context.flow(), context.bot())));
        backHandlers.put(BookingFlow.Step.BOOK_WEEK, context -> previous(context,
                () -> dateSelection.showMonthChoices(context.chatId(), context.flow(), context.bot())));
        backHandlers.put(BookingFlow.Step.BOOK_DAY, context -> previous(context,
                () -> dateSelection.showWeekChoices(context.chatId(), context.flow(),
                        context.flow().selectedMonth, context.bot())));
        backHandlers.put(BookingFlow.Step.BOOK_TIME, context -> previous(context,
                () -> dateSelection.showDayChoices(context.chatId(), context.flow(), context.bot())));
        backHandlers.put(BookingFlow.Step.BOOK_DURATION, this::backFromDuration);
        backHandlers.put(BookingFlow.Step.BOOK_NAME, this::backFromName);
        backHandlers.put(BookingFlow.Step.BOOK_PHONE, this::backToName);
        backHandlers.put(BookingFlow.Step.BOOK_COMMENT, this::backToName);
        backHandlers.put(BookingFlow.Step.BOOK_CONFIRM, context -> previous(context,
                () -> confirmation.promptComment(context.chatId(), context.flow(), context.bot())));
    }

    /// Возвращает к выбору времени и сбрасывает длительность.
    ///
    /// @param context контекст возврата
    private void backFromDuration(BackContext context) {
        context.flow().hours = 0;
        previous(context, () -> timeSelection.showTimeChoices(context.chatId(), context.flow(), context.bot()));
    }

    /// Возвращает к выбору длительности и очищает данные клиента.
    ///
    /// @param context контекст возврата
    private void backFromName(BackContext context) {
        formService.clearCustomerDetails(context.flow());
        previous(context, () -> timeSelection.showDurationChoices(context.chatId(), context.flow(), context.bot()));
    }

    /// Возвращает к вводу имени и очищает данные клиента.
    ///
    /// @param context контекст возврата
    private void backToName(BackContext context) {
        formService.clearCustomerDetails(context.flow());
        previous(context, () -> formService.promptName(context.chatId(), context.bot()));
    }

    /// Выполняет переход назад и вызывает отрисовку предыдущего шага.
    ///
    /// @param context контекст возврата
    /// @param render  отрисовка предыдущего шага
    private void previous(BackContext context, Runnable render) {
        if (stateMachine.transition(context.flow(), BookingFlow.Event.BACK)) {
            render.run();
        } else {
            returnToMenu(context.chatId(), context.flow(), context.bot());
        }
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

    /// Данные, необходимые обработчику возврата.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщений
    private record BackContext(long chatId, BookingFlow flow, Bot bot) {
    }
}

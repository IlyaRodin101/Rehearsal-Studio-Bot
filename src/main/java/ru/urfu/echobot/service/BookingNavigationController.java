package ru.urfu.echobot.service;

import java.time.YearMonth;
import java.util.List;
import ru.urfu.echobot.bot.Bot;

/// Выполняет переходы кнопочных шагов бронирования и запускает создание записи.
public final class BookingNavigationController {
    /// Позиция аргумента действия в callback-данных.
    private static final int ARGUMENT_PART = 2;
    /// Callback начала бронирования.
    private static final String MENU_BOOK = "menu:book";
    /// Callback главного меню.
    private static final String MENU_HOME = "menu:home";
    /// Уведомление о созданной записи.
    private static final String CREATED = "Готово, запись создана.";
    /// Уведомление о занятом слоте.
    private static final String UNAVAILABLE = "Это время больше недоступно. Выберите другое время.";

    /// Сервис выбора базы и зала.
    private final BookingBaseSelectionService baseSelection;
    /// Сервис выбора месяца, недели и дня.
    private final BookingDateSelectionService dateSelection;
    /// Сервис формы клиента.
    private final BookingFormService formService;
    /// Контроллер главного меню.
    private final BookingMenuController menuController;
    /// Автомат состояний бронирования.
    private final BookingStateMachine stateMachine;
    /// Сервис комментария и подтверждения.
    private final BookingConfirmationService confirmation;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards;

    /// Создаёт контроллер переходов бронирования.
    ///
    /// @param baseSelection  выбор базы и зала
    /// @param dateSelection  выбор месяца, недели и дня
    /// @param formService    форма клиента
    /// @param menuController главное меню
    /// @param stateMachine   автомат состояний
    /// @param confirmation   комментарий и подтверждение
    /// @param keyboards      фабрика клавиатур
    public BookingNavigationController(BookingBaseSelectionService baseSelection,
                                       BookingDateSelectionService dateSelection,
                                       BookingFormService formService,
                                       BookingMenuController menuController,
                                       BookingStateMachine stateMachine,
                                       BookingConfirmationService confirmation,
                                       BookingKeyboardFactory keyboards) {
        this.baseSelection = baseSelection;
        this.dateSelection = dateSelection;
        this.formService = formService;
        this.menuController = menuController;
        this.stateMachine = stateMachine;
        this.confirmation = confirmation;
        this.keyboards = keyboards;
    }

    /// Начинает новый диалог бронирования из главного меню.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщений
    public void beginBooking(long chatId, BookingFlow flow, Bot bot) {
        stateMachine.reset(flow);
        flow.clearBooking();
        if (stateMachine.transition(flow, BookingStateMachine.Event.BEGIN_BOOKING)) {
            baseSelection.showBaseChoices(chatId, flow, bot);
        }
    }

    /// Выбирает базу и открывает список её залов.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void selectBase(BookingCallback context, Bot bot) {
        BookingFlow flow = context.flow();
        if (stateMachine.transition(flow, BookingStateMachine.Event.SELECT_BASE)) {
            flow.clearBooking();
            flow.baseId = Long.parseLong(context.part(ARGUMENT_PART));
            baseSelection.showRoomChoices(context.chatId(), flow, bot);
        } else {
            menuController.returnToMenu(context.chatId(), flow, bot);
        }
    }

    /// Выбирает зал базы и открывает список месяцев.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void selectRoom(BookingCallback context, Bot bot) {
        BookingFlow flow = context.flow();
        long roomId = Long.parseLong(context.part(ARGUMENT_PART));
        if (!baseSelection.isAvailableRoom(flow.baseId, roomId)) {
            baseSelection.showRoomChoices(context.chatId(), flow, bot);
        } else if (stateMachine.transition(flow, BookingStateMachine.Event.SELECT_ROOM)) {
            flow.roomId = roomId;
            flow.monthOffset = 0;
            dateSelection.showMonthChoices(context.chatId(), flow, bot);
        } else {
            menuController.returnToMenu(context.chatId(), flow, bot);
        }
    }

    /// Переключает страницу списка месяцев.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void selectMonths(BookingCallback context, Bot bot) {
        BookingFlow flow = context.flow();
        if (stateMachine.transition(flow, BookingStateMachine.Event.CHANGE_MONTH_PAGE)) {
            flow.monthOffset = Integer.parseInt(context.part(ARGUMENT_PART));
            dateSelection.showMonthChoices(context.chatId(), flow, bot);
        } else {
            menuController.returnToMenu(context.chatId(), flow, bot);
        }
    }

    /// Выбирает месяц и открывает список недель.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void selectMonth(BookingCallback context, Bot bot) {
        BookingFlow flow = context.flow();
        if (stateMachine.transition(flow, BookingStateMachine.Event.SELECT_MONTH)) {
            YearMonth month = YearMonth.parse(context.part(ARGUMENT_PART));
            flow.selectedMonth = month;
            dateSelection.showWeekChoices(context.chatId(), flow, month, bot);
        } else {
            menuController.returnToMenu(context.chatId(), flow, bot);
        }
    }

    /// Подтверждает и создаёт бронирование.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void confirm(BookingCallback context, Bot bot) {
        BookingFlow flow = context.flow();
        if (stateMachine.transition(flow, BookingStateMachine.Event.CONFIRM_BOOKING)) {
            boolean created = formService.confirmBooking(context.userId(), flow);
            flow.clearBooking();
            showConfirmationResult(context.chatId(), created, bot);
        } else {
            menuController.returnToMenu(context.chatId(), flow, bot);
        }
    }

    /// Пропускает необязательный комментарий и открывает подтверждение.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void skipComment(BookingCallback context, Bot bot) {
        confirmation.skipBookingComment(context.chatId(), context.flow(), bot);
    }

    /// Отменяет бронирование и возвращает в главное меню.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void cancel(BookingCallback context, Bot bot) {
        menuController.returnToMenu(context.chatId(), context.flow(), bot);
    }

    /// Показывает результат создания записи.
    ///
    /// @param chatId  идентификатор чата
    /// @param created true, если запись создана
    /// @param bot     бот для отправки сообщений
    private void showConfirmationResult(long chatId, boolean created, Bot bot) {
        if (created) {
            menuController.showNoticeAndMenu(chatId, CREATED, bot);
        } else {
            List<List<Button>> keyboard = keyboards.choices(
                    keyboards.button("Выбрать время", MENU_BOOK),
                    keyboards.button("Главное меню", MENU_HOME));
            bot.sendMessage(new Message(UNAVAILABLE, null, keyboard), chatId);
        }
    }
}

package ru.urfu.echobot.service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import ru.urfu.echobot.bot.Bot;

/// Возвращает диалог бронирования на предыдущий шаг, выполняя переход через автомат состояний.
public final class BookingBackController {
    /// Автомат состояний бронирования.
    private final BookingStateMachine stateMachine;
    /// Контроллер главного меню.
    private final BookingMenuController menuController;
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
    private final Map<BookingFlow.Step, Consumer<BackContext>> handlers;

    /// Создаёт контроллер возврата.
    ///
    /// @param stateMachine   автомат состояний
    /// @param menuController главное меню
    /// @param baseSelection  выбор базы и зала
    /// @param dateSelection  выбор месяца, недели и дня
    /// @param timeSelection  выбор времени и длительности
    /// @param formService    форма клиента
    /// @param confirmation   комментарий и подтверждение
    public BookingBackController(BookingStateMachine stateMachine, BookingMenuController menuController,
                                 BookingBaseSelectionService baseSelection,
                                 BookingDateSelectionService dateSelection,
                                 BookingTimeSelectionService timeSelection, BookingFormService formService,
                                 BookingConfirmationService confirmation) {
        this.stateMachine = stateMachine;
        this.menuController = menuController;
        this.baseSelection = baseSelection;
        this.dateSelection = dateSelection;
        this.timeSelection = timeSelection;
        this.formService = formService;
        this.confirmation = confirmation;
        handlers = new HashMap<>();
        registerHandlers();
    }

    /// Выполняет возврат с текущего шага диалога.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщений
    public void back(long chatId, BookingFlow flow, Bot bot) {
        Consumer<BackContext> handler = handlers.get(flow.step);
        if (handler == null) {
            menuController.returnToMenu(chatId, flow, bot);
        } else {
            handler.accept(new BackContext(chatId, flow, bot));
        }
    }

    /// Заполняет таблицу переходов назад для всех шагов диалога.
    private void registerHandlers() {
        handlers.put(BookingFlow.Step.MENU, context ->
                menuController.returnToMenu(context.chatId(), context.flow(), context.bot()));
        handlers.put(BookingFlow.Step.BOOK_BASES, context ->
                menuController.returnToMenu(context.chatId(), context.flow(), context.bot()));
        handlers.put(BookingFlow.Step.BOOK_ROOMS, context -> previous(context,
                () -> baseSelection.showBaseChoices(context.chatId(), context.flow(), context.bot())));
        handlers.put(BookingFlow.Step.BOOK_MONTH, context -> previous(context,
                () -> baseSelection.showRoomChoices(context.chatId(), context.flow(), context.bot())));
        handlers.put(BookingFlow.Step.BOOK_WEEK, context -> previous(context,
                () -> dateSelection.showMonthChoices(context.chatId(), context.flow(), context.bot())));
        handlers.put(BookingFlow.Step.BOOK_DAY, context -> previous(context,
                () -> dateSelection.showWeekChoices(context.chatId(), context.flow(),
                        context.flow().selectedMonth, context.bot())));
        handlers.put(BookingFlow.Step.BOOK_TIME, context -> previous(context,
                () -> dateSelection.showDayChoices(context.chatId(), context.flow(), context.bot())));
        handlers.put(BookingFlow.Step.BOOK_DURATION, this::backFromDuration);
        handlers.put(BookingFlow.Step.BOOK_NAME, this::backFromName);
        handlers.put(BookingFlow.Step.BOOK_PHONE, this::backToName);
        handlers.put(BookingFlow.Step.BOOK_COMMENT, this::backToName);
        handlers.put(BookingFlow.Step.BOOK_CONFIRM, context -> previous(context,
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
        if (stateMachine.transition(context.flow(), BookingStateMachine.Event.BACK)) {
            render.run();
        } else {
            menuController.returnToMenu(context.chatId(), context.flow(), context.bot());
        }
    }

    /// Данные, необходимые обработчику возврата.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщений
    private record BackContext(long chatId, BookingFlow flow, Bot bot) {
    }
}

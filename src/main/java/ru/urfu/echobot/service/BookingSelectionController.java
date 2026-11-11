package ru.urfu.echobot.service;

import java.time.LocalDate;
import ru.urfu.echobot.bot.Bot;

/// Выполняет переходы выбора недели, дня, времени и длительности.
public final class BookingSelectionController {
    /// Позиция аргумента действия в callback-данных.
    private static final int ARGUMENT_PART = 2;
    /// Позиция часа начала в callback-данных выбора времени.
    private static final int HOUR_PART = 3;

    /// Сервис выбора месяца, недели и дня.
    private final BookingDateSelectionService dateSelection;
    /// Сервис выбора времени и длительности.
    private final BookingTimeSelectionService timeSelection;
    /// Сервис формы клиента.
    private final BookingFormService formService;
    /// Контроллер главного меню.
    private final BookingMenuController menuController;
    /// Автомат состояний бронирования.
    private final BookingStateMachine stateMachine;

    /// Создаёт контроллер выбора даты и времени.
    ///
    /// @param dateSelection  выбор месяца, недели и дня
    /// @param timeSelection  выбор времени и длительности
    /// @param formService    форма клиента
    /// @param menuController главное меню
    /// @param stateMachine   автомат состояний
    public BookingSelectionController(BookingDateSelectionService dateSelection,
                                      BookingTimeSelectionService timeSelection,
                                      BookingFormService formService, BookingMenuController menuController,
                                      BookingStateMachine stateMachine) {
        this.dateSelection = dateSelection;
        this.timeSelection = timeSelection;
        this.formService = formService;
        this.menuController = menuController;
        this.stateMachine = stateMachine;
    }

    /// Выбирает неделю и открывает список дней.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void selectWeek(BookingCallback context, Bot bot) {
        BookingFlow flow = context.flow();
        if (stateMachine.transition(flow, BookingStateMachine.Event.SELECT_WEEK)) {
            flow.bookingDate = LocalDate.parse(context.part(ARGUMENT_PART));
            flow.selectedWeek = flow.bookingDate;
            dateSelection.showDayChoices(context.chatId(), flow, bot);
        } else {
            menuController.returnToMenu(context.chatId(), flow, bot);
        }
    }

    /// Выбирает дату и открывает свободное время.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void selectDay(BookingCallback context, Bot bot) {
        BookingFlow flow = context.flow();
        if (stateMachine.transition(flow, BookingStateMachine.Event.SELECT_DAY)) {
            flow.bookingDate = LocalDate.parse(context.part(ARGUMENT_PART));
            if (flow.selectedWeek == null) {
                flow.selectedWeek = flow.bookingDate;
            }
            timeSelection.showTimeChoices(context.chatId(), flow, bot);
        } else {
            menuController.returnToMenu(context.chatId(), flow, bot);
        }
    }

    /// Выбирает время начала и открывает доступную длительность.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void selectTime(BookingCallback context, Bot bot) {
        BookingFlow flow = context.flow();
        if (stateMachine.transition(flow, BookingStateMachine.Event.SELECT_TIME)) {
            flow.bookingDate = LocalDate.parse(context.part(ARGUMENT_PART));
            flow.startHour = Integer.parseInt(context.part(HOUR_PART));
            timeSelection.showDurationChoices(context.chatId(), flow, bot);
        } else {
            menuController.returnToMenu(context.chatId(), flow, bot);
        }
    }

    /// Выбирает длительность и просит ввести имя клиента.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void selectHours(BookingCallback context, Bot bot) {
        BookingFlow flow = context.flow();
        if (stateMachine.transition(flow, BookingStateMachine.Event.SELECT_DURATION)) {
            flow.hours = Integer.parseInt(context.part(ARGUMENT_PART));
            formService.promptName(context.chatId(), bot);
        } else {
            menuController.returnToMenu(context.chatId(), flow, bot);
        }
    }
}

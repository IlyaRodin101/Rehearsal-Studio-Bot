package ru.urfu.echobot.service;

import java.util.Map;
import java.util.Objects;

/**
 * Управляет переходами между состояниями бронирования.
 */
final class BookingStateMachine {

    /**
     * События, вызывающие переход между состояниями.
     */
    enum Event {
        BEGIN_BOOKING, SELECT_BASE, SELECT_ROOM, SELECT_MONTH, CHANGE_MONTH_PAGE, SELECT_WEEK, SELECT_DAY,
        SELECT_TIME, SELECT_DURATION, ENTER_NAME, VALID_PHONE,
        ENTER_COMMENT, SKIP_COMMENT, CONFIRM_BOOKING, BACK, CANCEL
    }

    /**
     * Таблица допустимых переходов.
     */
    private final Map<BookingFlow.Step, Map<Event, BookingFlow.Step>> transitions;

    /**
     * Создаёт автомат состояний и инициализирует таблицу переходов.
     */
    BookingStateMachine() {
        transitions = Map.ofEntries(Map.entry(BookingFlow.Step.MENU, Map.of(Event.BEGIN_BOOKING,
                        BookingFlow.Step.BOOK_BASES)),

                Map.entry(BookingFlow.Step.BOOK_BASES, Map.of(Event.SELECT_BASE, BookingFlow.Step.BOOK_ROOMS,
                        Event.BACK, BookingFlow.Step.MENU)),

                Map.entry(BookingFlow.Step.BOOK_ROOMS, Map.of(Event.SELECT_ROOM, BookingFlow.Step.BOOK_MONTH,
                        Event.BACK, BookingFlow.Step.BOOK_BASES)),

                Map.entry(BookingFlow.Step.BOOK_MONTH, Map.of(Event.SELECT_MONTH, BookingFlow.Step.BOOK_WEEK,
                        Event.CHANGE_MONTH_PAGE, BookingFlow.Step.BOOK_MONTH, Event.BACK,
                        BookingFlow.Step.BOOK_ROOMS)),

                Map.entry(BookingFlow.Step.BOOK_WEEK, Map.of(Event.SELECT_WEEK, BookingFlow.Step.BOOK_DAY,
                        Event.BACK, BookingFlow.Step.BOOK_MONTH)),

                Map.entry(BookingFlow.Step.BOOK_DAY, Map.of(Event.SELECT_DAY, BookingFlow.Step.BOOK_TIME,
                        Event.BACK, BookingFlow.Step.BOOK_WEEK)),

                Map.entry(BookingFlow.Step.BOOK_TIME, Map.of(Event.SELECT_TIME, BookingFlow.Step.BOOK_DURATION,
                        Event.BACK, BookingFlow.Step.BOOK_DAY)),

                Map.entry(BookingFlow.Step.BOOK_DURATION, Map.of(Event.SELECT_DURATION, BookingFlow.Step.BOOK_NAME,
                        Event.BACK, BookingFlow.Step.BOOK_TIME)),

                Map.entry(BookingFlow.Step.BOOK_NAME, Map.of(Event.ENTER_NAME, BookingFlow.Step.BOOK_PHONE,
                        Event.BACK, BookingFlow.Step.BOOK_DURATION)),

                Map.entry(BookingFlow.Step.BOOK_PHONE, Map.of(Event.VALID_PHONE, BookingFlow.Step.BOOK_COMMENT,
                        Event.BACK, BookingFlow.Step.BOOK_NAME)),

                Map.entry(BookingFlow.Step.BOOK_COMMENT, Map.of(Event.ENTER_COMMENT, BookingFlow.Step.BOOK_CONFIRM,
                        Event.SKIP_COMMENT, BookingFlow.Step.BOOK_CONFIRM, Event.BACK, BookingFlow.Step.BOOK_PHONE)),

                Map.entry(BookingFlow.Step.BOOK_CONFIRM, Map.of(Event.CONFIRM_BOOKING, BookingFlow.Step.MENU,
                        Event.CANCEL, BookingFlow.Step.MENU, Event.BACK, BookingFlow.Step.BOOK_COMMENT)));
    }

    /**
     * Выполняет переход между состояниями бронирования.
     *
     * @param flow  состояние бронирования пользователя
     * @param event событие, инициирующее переход
     * @return true, если переход выполнен, иначе false
     */
    boolean transition(BookingFlow flow, Event event) {
        Objects.requireNonNull(flow, "flow");
        Objects.requireNonNull(event, "event");

        BookingFlow.Step currentStep = Objects.requireNonNull(flow.step, "flow.step");

        Map<Event, BookingFlow.Step> stateTransitions = transitions.get(currentStep);

        if (stateTransitions == null) {
            return false;
        }

        BookingFlow.Step nextStep = stateTransitions.get(event);

        if (nextStep == null) {
            return false;
        }

        flow.step = nextStep;
        return true;
    }
}

package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class BookingStateMachineTest {
    /**
     * Проверяемый автомат состояний.
     */
    private final BookingStateMachine stateMachine = new BookingStateMachine();

    /**
     * Состояние диалога тестового пользователя.
     */
    private final BookingFlow flow = new BookingFlow();

    /**
     * Given: пользователь находится в главном меню.
     * When: пользователь начинает бронирование.
     * Then: состояние диалога меняется на BOOK_BASES.
     */
    @Test
    void startsBookingFromMenu() {
        boolean result = stateMachine.transition(
                flow, BookingStateMachine.Event.BEGIN_BOOKING);

        assertTrue(result);
        assertEquals(BookingFlow.Step.BOOK_BASES, flow.step);
    }

    /**
     * Given: пользователь находится в главном меню.
     * When: пользователь пытается выбрать базу.
     * Then: переход отклоняется, состояние не меняется.
     */
    @Test
    void rejectsInvalidTransition() {
        boolean result = stateMachine.transition(
                flow, BookingStateMachine.Event.SELECT_BASE);

        assertFalse(result);
        assertEquals(BookingFlow.Step.MENU, flow.step);
    }

    /**
     * Given: пользователь находится на этапе выбора комнаты.
     * When: пользователь возвращается на предыдущий шаг.
     * Then: состояние меняется на BOOK_BASES.
     */
    @Test
    void returnsToPreviousStep() {
        flow.step = BookingFlow.Step.BOOK_ROOMS;

        boolean result = stateMachine.transition(
                flow, BookingStateMachine.Event.BACK);

        assertTrue(result);
        assertEquals(BookingFlow.Step.BOOK_BASES, flow.step);
    }

    /**
     * Given: пользователь находится на этапе выбора месяца.
     * When: пользователь переключает страницу месяцев.
     * Then: состояние остаётся BOOK_MONTH.
     */
    @Test
    void changesMonthPageWithoutLeavingMonthSelection() {
        flow.step = BookingFlow.Step.BOOK_MONTH;

        boolean result = stateMachine.transition(
                flow, BookingStateMachine.Event.CHANGE_MONTH_PAGE);

        assertTrue(result);
        assertEquals(BookingFlow.Step.BOOK_MONTH, flow.step);
    }

    /**
     * Given: пользователь находится на этапе ввода комментария.
     * When: пользователь пропускает ввод необязательного комментария.
     * Then: состояние меняется на BOOK_CONFIRM.
     */
    @Test
    void skipsOptionalComment() {
        flow.step = BookingFlow.Step.BOOK_COMMENT;

        boolean result = stateMachine.transition(
                flow, BookingStateMachine.Event.SKIP_COMMENT);

        assertTrue(result);
        assertEquals(BookingFlow.Step.BOOK_CONFIRM, flow.step);
    }

    /**
     * Given: пользователь находится на этапе подтверждения бронирования.
     * When: пользователь отменяет бронирование.
     * Then: состояние меняется на MENU.
     */
    @Test
    void cancelsBooking() {
        flow.step = BookingFlow.Step.BOOK_CONFIRM;

        boolean result = stateMachine.transition(
                flow, BookingStateMachine.Event.CANCEL);

        assertTrue(result);
        assertEquals(BookingFlow.Step.MENU, flow.step);
    }

    /**
     * Given: созданы два независимых состояния диалога.
     * When: первый пользователь начинает бронирование.
     * Then: состояние второго пользователя не меняется.
     */
    @Test
    void usersHaveIndependentBookingStates() {
        BookingFlow anotherFlow = new BookingFlow();

        stateMachine.transition(flow, BookingStateMachine.Event.BEGIN_BOOKING);

        assertEquals(BookingFlow.Step.BOOK_BASES, flow.step);
        assertEquals(BookingFlow.Step.MENU, anotherFlow.step);
    }

}

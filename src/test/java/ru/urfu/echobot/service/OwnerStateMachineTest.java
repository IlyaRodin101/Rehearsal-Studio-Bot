package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/// Проверяет переходы автомата состояний владельца базы.
final class OwnerStateMachineTest {
    /// Проверяемый автомат состояний.
    private final OwnerStateMachine stateMachine = new OwnerStateMachine();
    /// Сессия тестового владельца.
    private final OwnerSession session = new OwnerSession();

    /// Given: владелец без активной формы. When: начинает создание базы. Then: переходит к вводу названия.
    @Test
    void startsBaseCreationFromIdle() {
        assertTrue(stateMachine.transition(session, OwnerStateMachine.Event.BEGIN_CREATE));

        assertEquals(OwnerStep.CREATE_NAME, session.step);
    }

    /// Given: владелец вводит название. When: переход по событию имени. Then: открывается ввод адреса.
    @Test
    void movesFromNameToAddress() {
        stateMachine.transition(session, OwnerStateMachine.Event.BEGIN_CREATE);

        assertTrue(stateMachine.transition(session, OwnerStateMachine.Event.ENTER_BASE_NAME));

        assertEquals(OwnerStep.CREATE_ADDRESS, session.step);
    }

    /// Given: владелец на шаге адреса. When: пытается сохранить текст базы. Then: переход отклоняется.
    @Test
    void rejectsEventThatIsNotAvailableFromCurrentStep() {
        session.step = OwnerStep.CREATE_ADDRESS;

        assertFalse(stateMachine.transition(session, OwnerStateMachine.Event.SAVE_TEXT));

        assertEquals(OwnerStep.CREATE_ADDRESS, session.step);
    }

    /// Given: владелец заполнил график. When: завершает последний день. Then: переходит к залам.
    @Test
    void finishesWeekWithRoomsStep() {
        session.step = OwnerStep.CREATE_OPENING;

        assertTrue(stateMachine.transition(session, OwnerStateMachine.Event.LAST_DAY));

        assertEquals(OwnerStep.CREATE_ROOM_NAME, session.step);
    }

    /// Given: владелец редактирует название. When: сохраняет текст. Then: возвращается в состояние без формы.
    @Test
    void returnsToIdleAfterEditing() {
        session.step = OwnerStep.EDIT_NAME;

        assertTrue(stateMachine.transition(session, OwnerStateMachine.Event.SAVE_TEXT));

        assertEquals(OwnerStep.IDLE, session.step);
    }

    /// Given: владелец подтвердил удаление зала. When: подтверждает повторно. Then: сессия сбрасывается.
    @Test
    void deletesRoomAndReturnsToIdle() {
        session.step = OwnerStep.DELETE_ROOM;

        assertTrue(stateMachine.transition(session, OwnerStateMachine.Event.DELETE_ROOM_CONFIRMED));

        assertEquals(OwnerStep.IDLE, session.step);
    }

    /// Given: две независимые сессии. When: первая начинает создание базы. Then: вторая остаётся без формы.
    @Test
    void ownersHaveIndependentSessions() {
        OwnerSession another = new OwnerSession();

        stateMachine.transition(session, OwnerStateMachine.Event.BEGIN_CREATE);

        assertEquals(OwnerStep.CREATE_NAME, session.step);
        assertEquals(OwnerStep.IDLE, another.step);
    }

    /// Given: активная форма владельца. When: автомат сбрасывает сессию. Then: шаг становится IDLE.
    @Test
    void resetReturnsSessionToIdle() {
        session.step = OwnerStep.CREATE_EQUIPMENT;

        stateMachine.reset(session);

        assertEquals(OwnerStep.IDLE, session.step);
    }
}

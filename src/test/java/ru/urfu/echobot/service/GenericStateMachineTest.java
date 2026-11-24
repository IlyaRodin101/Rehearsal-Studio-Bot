package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/// Проверяет универсальный автомат состояний с таблицей переходов.
final class GenericStateMachineTest {
    /// Хранитель состояния тестового автомата.
    private final class Holder implements State<TestState> {
        /// Текущее состояние.
        private TestState step;

        /// Создаёт хранитель с заданным состоянием.
        ///
        /// @param step начальное состояние
        Holder(TestState step) {
            this.step = step;
        }

        /// Возвращает текущее состояние.
        ///
        /// @return текущее состояние
        @Override
        public TestState state() {
            return step;
        }

        /// Устанавливает текущее состояние.
        ///
        /// @param value новое состояние
        @Override
        public void state(TestState value) {
            step = value;
        }
    }

    /// Проверяемый автомат: IDLE -> FIRST -> SECOND, из FIRST можно вернуться в IDLE.
    private final GenericStateMachine<TestState, TestEvent> stateMachine = new GenericStateMachine<>(Map.of(
            TestState.IDLE, Map.of(TestEvent.START, TestState.FIRST),
            TestState.FIRST, Map.of(TestEvent.NEXT, TestState.SECOND, TestEvent.BACK, TestState.IDLE)));

    /// Given: начальное состояние. When: приходит допустимое событие. Then: состояние меняется.
    @Test
    void movesToNextStateOnAllowedEvent() {
        Holder holder = new Holder(TestState.IDLE);

        assertTrue(stateMachine.transition(holder, TestEvent.START));
        assertEquals(TestState.FIRST, holder.state());
    }

    /// Given: состояние без обработчика события. When: приходит событие. Then: состояние не меняется.
    @Test
    void ignoresEventUnavailableForCurrentState() {
        Holder holder = new Holder(TestState.SECOND);

        assertFalse(stateMachine.transition(holder, TestEvent.BACK));
        assertEquals(TestState.SECOND, holder.state());
    }

    /// Given: состояние с обработчиком. When: приходит неизвестное событие. Then: состояние не меняется.
    @Test
    void ignoresEventMissingFromTransitionTable() {
        Holder holder = new Holder(TestState.IDLE);

        assertFalse(stateMachine.transition(holder, TestEvent.NEXT));
        assertEquals(TestState.IDLE, holder.state());
    }

    /// Given: автомат с таблицей переходов. When: вызывается reset. Then: состояние заменяется принудительно.
    @Test
    void resetsHolderToRequestedState() {
        Holder holder = new Holder(TestState.SECOND);

        stateMachine.reset(holder, TestState.IDLE);

        assertEquals(TestState.IDLE, holder.state());
    }

    /// Given: два независимых хранителя. When: один меняет состояние. Then: второй остаётся прежним.
    @Test
    void keepsHoldersIndependent() {
        Holder first = new Holder(TestState.IDLE);
        Holder second = new Holder(TestState.IDLE);

        stateMachine.transition(first, TestEvent.START);

        assertEquals(TestState.FIRST, first.state());
        assertEquals(TestState.IDLE, second.state());
    }

    /// Given: автомат. When: передаётся null вместо хранителя или события. Then: выбрасывается исключение.
    @Test
    void rejectsNullArguments() {
        Holder holder = new Holder(TestState.IDLE);

        assertThrows(NullPointerException.class, () -> stateMachine.transition(null, TestEvent.START));
        assertThrows(NullPointerException.class, () -> stateMachine.transition(holder, null));
        assertThrows(NullPointerException.class, () -> stateMachine.reset(null, TestState.IDLE));
    }
}

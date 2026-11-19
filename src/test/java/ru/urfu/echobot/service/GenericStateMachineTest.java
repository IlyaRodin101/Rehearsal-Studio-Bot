package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

/// Проверяет универсальный автомат состояний с таблицей переходов.
final class GenericStateMachineTest {
    /// Состояния тестового автомата.
    private enum Step {
        /// Начальное состояние.
        IDLE,
        /// Первое рабочее состояние.
        FIRST,
        /// Второе рабочее состояние.
        SECOND
    }

    /// События тестового автомата.
    private enum Event {
        /// Начало сценария.
        START,
        /// Переход к следующему шагу.
        NEXT,
        /// Возврат к началу.
        BACK
    }

    /// Хранитель состояния тестового автомата.
    private final class Holder implements GenericStateMachine.State<Step> {
        /// Текущее состояние.
        private Step step;

        /// Создаёт хранитель с заданным состоянием.
        ///
        /// @param step начальное состояние
        Holder(Step step) {
            this.step = step;
        }

        /// Возвращает текущее состояние.
        ///
        /// @return текущее состояние
        @Override
        public Step state() {
            return step;
        }

        /// Устанавливает текущее состояние.
        ///
        /// @param value новое состояние
        @Override
        public void state(Step value) {
            step = value;
        }
    }

    /// Проверяемый автомат: IDLE -> FIRST -> SECOND, из FIRST можно вернуться в IDLE.
    private final GenericStateMachine<Step, Event> stateMachine = new GenericStateMachine<>(Map.of(
            Step.IDLE, Map.of(Event.START, Step.FIRST),
            Step.FIRST, Map.of(Event.NEXT, Step.SECOND, Event.BACK, Step.IDLE)));

    /// Given: начальное состояние. When: приходит допустимое событие. Then: состояние меняется.
    @Test
    void movesToNextStateOnAllowedEvent() {
        Holder holder = new Holder(Step.IDLE);

        assertTrue(stateMachine.transition(holder, Event.START));
        assertEquals(Step.FIRST, holder.state());
    }

    /// Given: состояние без обработчика события. When: приходит событие. Then: состояние не меняется.
    @Test
    void ignoresEventUnavailableForCurrentState() {
        Holder holder = new Holder(Step.SECOND);

        assertFalse(stateMachine.transition(holder, Event.BACK));
        assertEquals(Step.SECOND, holder.state());
    }

    /// Given: состояние с обработчиком. When: приходит неизвестное событие. Then: состояние не меняется.
    @Test
    void ignoresEventMissingFromTransitionTable() {
        Holder holder = new Holder(Step.IDLE);

        assertFalse(stateMachine.transition(holder, Event.NEXT));
        assertEquals(Step.IDLE, holder.state());
    }

    /// Given: автомат с таблицей переходов. When: вызывается reset. Then: состояние заменяется принудительно.
    @Test
    void resetsHolderToRequestedState() {
        Holder holder = new Holder(Step.SECOND);

        stateMachine.reset(holder, Step.IDLE);

        assertEquals(Step.IDLE, holder.state());
    }

    /// Given: два независимых хранителя. When: один меняет состояние. Then: второй остаётся прежним.
    @Test
    void keepsHoldersIndependent() {
        Holder first = new Holder(Step.IDLE);
        Holder second = new Holder(Step.IDLE);

        stateMachine.transition(first, Event.START);

        assertEquals(Step.FIRST, first.state());
        assertEquals(Step.IDLE, second.state());
    }

    /// Given: автомат. When: передаётся null вместо хранителя или события. Then: выбрасывается исключение.
    @Test
    void rejectsNullArguments() {
        Holder holder = new Holder(Step.IDLE);

        assertThrows(NullPointerException.class, () -> stateMachine.transition(null, Event.START));
        assertThrows(NullPointerException.class, () -> stateMachine.transition(holder, null));
        assertThrows(NullPointerException.class, () -> stateMachine.reset(null, Step.IDLE));
    }
}

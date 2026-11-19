package ru.urfu.echobot.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/// Универсальный автомат состояний с таблицей допустимых переходов.
///
/// @param <S> тип состояния
/// @param <E> тип события
public final class GenericStateMachine<S, E> {
    /// Имя параметра хранителя состояния в проверках.
    private static final String HOLDER = "holder";

    /// Таблица допустимых переходов.
    private final Map<S, Map<E, S>> transitions;

    /// Создаёт автомат по таблице переходов.
    ///
    /// @param transitions таблица переходов
    public GenericStateMachine(Map<S, Map<E, S>> transitions) {
        Map<S, Map<E, S>> copy = new HashMap<>();
        transitions.forEach((state, stateTransitions) -> copy.put(state, Map.copyOf(stateTransitions)));
        this.transitions = Map.copyOf(copy);
    }

    /// Выполняет переход, если событие допустимо для текущего состояния.
    ///
    /// @param holder хранитель состояния
    /// @param event  событие перехода
    /// @return true, если состояние изменено
    public boolean transition(State<S> holder, E event) {
        Objects.requireNonNull(holder, HOLDER);
        Objects.requireNonNull(event, "event");
        S current = Objects.requireNonNull(holder.state(), "holder.state");
        Map<E, S> stateTransitions = transitions.get(current);
        if (stateTransitions == null) {
            return false;
        }
        S next = stateTransitions.get(event);
        if (next == null) {
            return false;
        }
        holder.state(next);
        return true;
    }

    /// Принудительно устанавливает начальное состояние.
    ///
    /// @param holder  хранитель состояния
    /// @param initial начальное состояние
    public void reset(State<S> holder, S initial) {
        Objects.requireNonNull(holder, HOLDER);
        holder.state(Objects.requireNonNull(initial, "initial"));
    }

    /// Изменяемое состояние, которым управляет автомат.
    ///
    /// @param <S> тип состояния
    public interface State<S> {
        /// Возвращает текущее состояние.
        ///
        /// @return текущее состояние
        S state();

        /// Устанавливает новое состояние.
        ///
        /// @param value новое состояние
        void state(S value);
    }
}

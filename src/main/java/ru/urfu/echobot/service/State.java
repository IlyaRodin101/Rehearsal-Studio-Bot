package ru.urfu.echobot.service;

/// Изменяемое состояние, которым управляет автомат состояний.
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

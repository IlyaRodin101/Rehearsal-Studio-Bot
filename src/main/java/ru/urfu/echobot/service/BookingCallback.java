package ru.urfu.echobot.service;

/// Разобранные данные callback-команды.
///
/// @param chatId идентификатор чата
/// @param userId идентификатор пользователя
/// @param flow   состояние диалога пользователя
/// @param parts  части callback-команды, разделённые двоеточием
record BookingCallback(long chatId, long userId, BookingFlow flow, String[] parts) {

    /// Возвращает количество частей callback-команды.
    ///
    /// @return количество частей
    int size() {
        return parts.length;
    }

    /// Возвращает часть callback-команды по индексу.
    ///
    /// @param index индекс части
    /// @return часть команды
    String part(int index) {
        return parts[index];
    }

    /// Возвращает действие внутри пространства имён.
    ///
    /// @return вторая часть callback-команды или пустая строка
    String action() {
        return parts.length > 1 ? parts[1] : "";
    }
}

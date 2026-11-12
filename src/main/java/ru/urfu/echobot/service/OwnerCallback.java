package ru.urfu.echobot.service;

/// Разобранные данные callback-команды владельца.
///
/// @param userId идентификатор владельца
/// @param parts  части callback-команды, разделённые двоеточием
record OwnerCallback(long userId, String[] parts) {

    /// Возвращает количество частей команды.
    ///
    /// @return количество частей
    int size() {
        return parts.length;
    }

    /// Возвращает действие команды.
    ///
    /// @return вторая часть команды или пустая строка
    String action() {
        return parts.length > 1 ? parts[1] : "";
    }

    /// Возвращает часть команды по индексу.
    ///
    /// @param index индекс части
    /// @return часть команды
    String part(int index) {
        return parts[index];
    }

    /// Возвращает числовую часть команды.
    ///
    /// @param index индекс части
    /// @return число
    int intPart(int index) {
        return Integer.parseInt(parts[index]);
    }

    /// Возвращает числовой идентификатор.
    ///
    /// @param index индекс части
    /// @return идентификатор
    long longPart(int index) {
        return Long.parseLong(parts[index]);
    }
}

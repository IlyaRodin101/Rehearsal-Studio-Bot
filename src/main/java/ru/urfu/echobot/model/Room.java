package ru.urfu.echobot.model;

import org.jetbrains.annotations.NotNull;

/// Зал репетиционной базы.
///
/// @param id              идентификатор зала
/// @param baseId          идентификатор базы, которой принадлежит зал
/// @param name            название зала
/// @param commentRequired нужно ли клиенту обязательно описать, что он будет делать в зале
/// @param active          доступен ли зал для новых бронирований
public record Room(long id, long baseId, @NotNull String name, boolean commentRequired, boolean active) {
    /// Создаёт доступный зал.
    public Room(long id, long baseId, String name, boolean commentRequired) {
        this(id, baseId, name, commentRequired, true);
    }
}

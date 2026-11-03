package ru.urfu.echobot.model;

import org.jetbrains.annotations.NotNull;

/// Зал репетиционной базы.
///
/// @param id              идентификатор зала
/// @param baseId          идентификатор базы, которой принадлежит зал
/// @param name            название зала
/// @param commentRequired нужно ли клиенту обязательно описать, что он будет делать в зале
public record Room(long id, long baseId, @NotNull String name, boolean commentRequired) {
}

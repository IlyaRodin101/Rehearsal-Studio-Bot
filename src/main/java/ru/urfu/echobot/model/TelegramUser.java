package ru.urfu.echobot.model;

import java.util.Locale;
import org.jetbrains.annotations.NotNull;

/// Пользователь Telegram, который хотя бы раз писал боту.
///
/// @param id       Telegram ID пользователя
/// @param username имя пользователя без символа `@` в нижнем регистре
public record TelegramUser(long id, @NotNull String username) {

    /// Приводит имя пользователя к единому виду.
    public TelegramUser {
        String value = username.trim();
        if (value.startsWith("@")) {
            value = value.substring(1);
        }
        value = value.toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Имя пользователя не может быть пустым");
        }
        username = value;
    }

    /// Возвращает имя пользователя с символом `@`.
    ///
    /// @return упоминание пользователя
    public String mention() {
        return "@" + username;
    }
}

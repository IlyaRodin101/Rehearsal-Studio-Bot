package ru.urfu.echobot.service;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Сообщение, которыми обмениваются боты и сервисы.
///
/// @param text       текст сообщения
/// @param buttonData данные нажатой кнопки (заполняется только во входящих сообщениях)
/// @param keyboard   строки кнопок под сообщением (заполняется только в исходящих)
/// @param username   имя пользователя Telegram без символа `@` (заполняется только во входящих)
public record Message(@Nullable String text, @Nullable String buttonData,
                      @NotNull List<List<Button>> keyboard, @Nullable String username) {

    /// Копирует клавиатуру, чтобы сообщение нельзя было изменить снаружи.
    public Message {
        keyboard = List.copyOf(keyboard);
    }

    /// Создаёт сообщение без имени пользователя Telegram.
    ///
    /// @param text       текст сообщения
    /// @param buttonData данные нажатой кнопки
    /// @param keyboard   строки кнопок под сообщением
    public Message(@Nullable String text, @Nullable String buttonData,
                   @NotNull List<List<Button>> keyboard) {
        this(text, buttonData, keyboard, null);
    }
}

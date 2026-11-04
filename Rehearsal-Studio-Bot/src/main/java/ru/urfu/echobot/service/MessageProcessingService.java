package ru.urfu.echobot.service;

import org.jetbrains.annotations.NotNull;
import ru.urfu.echobot.bot.Bot;

/// Интерфейс логического ядра.
public interface MessageProcessingService {

    /// Обработчик сообщений, возвращающий ответы на них.
    ///
    /// В процессе обработки может вызывать методы ботов, чтобы, например, отправлять сообщения.
    ///
    /// @param msg    сообщение, которое нужно обработать
    /// @param chatId id чата, откуда пришло сообщение
    /// @param userId id Telegram-пользователя, отправившего сообщение
    /// @param bot    бот, который получил сообщение
    void processMessage(@NotNull Message msg, long chatId, long userId, @NotNull Bot bot);

    /// Processes a message in a private chat where the user and chat IDs are the same.
    ///
    /// @param msg    message to process
    /// @param chatId Telegram chat ID and user ID
    /// @param bot    bot that received the message
    default void processMessage(@NotNull Message msg, long chatId, @NotNull Bot bot) {
        processMessage(msg, chatId, chatId, bot);
    }
}

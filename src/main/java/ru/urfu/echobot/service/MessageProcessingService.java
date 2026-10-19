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
    /// @param chatId id чата, от кого пришло сообщение
    /// @param bot    бот, который получил сообщение
    void processMessage(@NotNull Message msg, long chatId, @NotNull Bot bot);
}
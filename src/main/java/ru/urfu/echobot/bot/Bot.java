package ru.urfu.echobot.bot;

import org.jetbrains.annotations.NotNull;
import ru.urfu.echobot.service.Message;

/// Бот, умеющий отправлять сообщения.
public interface Bot {

    /// Отправляет сообщение.
    ///
    /// @param msg сообщение, которое требуется отправить
    /// @param id  идентификатор получателя
    void sendMessage(@NotNull Message msg, long id);
}
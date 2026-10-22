package ru.urfu.echobot.bot;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import ru.urfu.echobot.service.Message;

/// Бот-заглушка для тестов: запоминает все отправленные сообщения.
public final class DummyBot implements Bot {
    /// Все сообщения, отправленные ботом, в порядке отправки.
    private final List<Message> sentMessages = new ArrayList<>();

    @Override
    public void sendMessage(@NotNull Message msg, long id) {
        sentMessages.add(msg);
    }

    /// Возвращает последнее отправленное сообщение.
    ///
    /// @return последнее сообщение бота
    public Message lastMessage() {
        return sentMessages.get(sentMessages.size() - 1);
    }

    /// Возвращает все отправленные сообщения.
    ///
    /// @return копия списка сообщений в порядке отправки
    public List<Message> messages() {
        return List.copyOf(sentMessages);
    }

    /// Возвращает число отправленных сообщений.
    ///
    /// @return количество сообщений
    public int sentCount() {
        return sentMessages.size();
    }
}

package ru.urfu.echobot.bot;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import ru.urfu.echobot.service.Message;

/// Бот-заглушка для тестов: запоминает все отправленные сообщения.
public final class DummyBot implements Bot {
    /// Все сообщения, отправленные ботом, в порядке отправки.
    private final List<Message> sentMessages = new ArrayList<>();
    /// Получатели сообщений в порядке отправки.
    private final List<Long> recipients = new ArrayList<>();

    @Override
    public void sendMessage(@NotNull Message msg, long id) {
        sentMessages.add(msg);
        recipients.add(id);
    }

    /// Возвращает получателя сообщения по его позиции.
    ///
    /// @param index позиция сообщения
    /// @return идентификатор получателя
    public long recipient(int index) {
        return recipients.get(index);
    }

    /// Возвращает сообщения, отправленные указанному получателю.
    ///
    /// @param id идентификатор получателя
    /// @return сообщения получателя в порядке отправки
    public List<Message> messagesTo(long id) {
        List<Message> result = new ArrayList<>();
        for (int index = 0; index < sentMessages.size(); index++) {
            if (recipients.get(index) == id) {
                result.add(sentMessages.get(index));
            }
        }
        return result;
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

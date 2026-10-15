package ru.urfu.echobot.bot;

import org.jetbrains.annotations.NotNull;
import ru.urfu.echobot.service.Message;

public interface Bot {
    void sendMessage(@NotNull Message msg, long id);
}

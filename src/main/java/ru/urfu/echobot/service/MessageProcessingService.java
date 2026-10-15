package ru.urfu.echobot.service;

import org.jetbrains.annotations.NotNull;
import ru.urfu.echobot.bot.Bot;

public interface MessageProcessingService {
    void processUpdate(@NotNull Message msg, long chatId, @NotNull Bot bot);
}

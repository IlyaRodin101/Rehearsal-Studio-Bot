package ru.urfu.echobot.service;

import org.telegram.telegrambots.meta.api.objects.Update;
import ru.urfu.echobot.bot.Bot;

public interface MessageProcessingService {
    void processUpdate(Update update, Bot bot);
}

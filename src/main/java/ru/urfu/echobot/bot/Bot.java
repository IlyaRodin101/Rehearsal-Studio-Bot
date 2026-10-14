package ru.urfu.echobot.bot;

import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.urfu.echobot.service.Message;

public interface Bot {
    void sendMessage(Message message, long chatId) throws TelegramApiException;

    void answerCallbackQuery(AnswerCallbackQuery answer) throws TelegramApiException;
}

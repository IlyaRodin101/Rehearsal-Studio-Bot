package ru.urfu.echobot.bot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.urfu.echobot.service.BookingMessageProcessingService;
import ru.urfu.echobot.service.Message;

public final class TelegramBot extends TelegramLongPollingBot implements Bot {
    private final String token;
    private final BookingMessageProcessingService messageProcessingService;

    public TelegramBot(String token, BookingMessageProcessingService messageProcessingService) {
        this.token = token;
        this.messageProcessingService = messageProcessingService;
    }

    @Override
    public void onUpdateReceived(Update update) {
        messageProcessingService.processUpdate(update, this);
    }

    @Override
    public String getBotUsername() {
        String username = System.getenv("BOT_USERNAME");
        return username == null || username.trim().isEmpty() ? "RehearsalBookingBot" : username.trim();
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public void sendMessage(Message message, long chatId) throws TelegramApiException {
        if (message.text() == null) {
            return;
        }
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(String.valueOf(chatId));
        sendMessage.setText(message.text());
        if (message.keyboard() != null) {
            sendMessage.setReplyMarkup(message.keyboard());
        }
        execute(sendMessage);
    }

    @Override
    public void answerCallbackQuery(AnswerCallbackQuery answer) throws TelegramApiException {
        execute(answer);
    }
}

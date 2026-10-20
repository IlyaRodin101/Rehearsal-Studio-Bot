package ru.urfu.echobot.bot;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.urfu.echobot.service.Button;
import ru.urfu.echobot.service.Message;
import ru.urfu.echobot.service.MessageProcessingService;

import java.util.ArrayList;
import java.util.List;

/// Telegram-бот, который принимает текст и нажатия кнопок и отправляет сообщения с кнопками.
public final class TelegramBot extends DefaultLongPollingUpdateConsumer implements Bot {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramBot.class);
    private final TelegramClient telegramClient;
    private final MessageProcessingService logic;
    private final String token;
    private final TelegramBotsLongPollingApplication application;

    /// Конструктор.
    ///
    /// @param token строка, содержащая токен для бота
    /// @param logic сервис для обработки сообщений
    public TelegramBot(@NotNull String token, @NotNull MessageProcessingService logic) {
        telegramClient = new OkHttpTelegramClient(token);
        this.logic = logic;
        this.token = token;
        application = new TelegramBotsLongPollingApplication();
    }

    /// Запускает бота.
    public void start() {
        try {
            application.registerBot(token, this);
            LOGGER.info("Telegram bot successfully started!");
            Thread.currentThread().join();
        } catch (Exception e) {
            LOGGER.error("Bot didn't get registered and didn't run.", e);
        }
    }

    @Override
    public void sendMessage(@NotNull Message msg, long id) {
        try {
            final String text = msg.text();
            if (text != null) {
                telegramClient.execute(createSendMessage(text, msg.keyboard(), id));
            }
        } catch (TelegramApiException e) {
            LOGGER.error("Couldn't send message", e);
        }
    }

    @Override
    public void consume(Update update) {
        try {
            LOGGER.debug("Consuming update: {}", update);
            if (update.hasCallbackQuery()) {
                consumeButtonPress(update.getCallbackQuery());
            } else if (update.hasMessage()) {
                final long chatId = update.getMessage().getChatId();
                logic.processMessage(convertTelegramMessage(update.getMessage()), chatId, this);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to process update: {}", update, e);
        }
    }

    /// Обрабатывает нажатие на кнопку: убирает «часики» на кнопке и передаёт данные в сервис.
    ///
    /// @param query нажатие на кнопку из Telegram
    /// @throws TelegramApiException если не удалось ответить на нажатие
    private void consumeButtonPress(@NotNull CallbackQuery query) throws TelegramApiException {
        telegramClient.execute(AnswerCallbackQuery.builder().callbackQueryId(query.getId()).build());
        final long chatId = query.getMessage().getChatId();
        final Message msg = Message.builder().buttonData(query.getData()).build();
        logic.processMessage(msg, chatId, this);
    }

    /// Превращает текст и кнопки в [SendMessage].
    ///
    /// @param text     текст сообщения, которое нужно отправить
    /// @param keyboard строки кнопок (может быть пустым)
    /// @param chatId   id чата, куда надо отправить сообщение
    /// @return объект [SendMessage], который можно отправлять
    private SendMessage createSendMessage(@NotNull String text, @NotNull List<List<Button>> keyboard, long chatId) {
        final SendMessage.SendMessageBuilder<?, ?> builder = SendMessage.builder().chatId(chatId).text(text);
        if (!keyboard.isEmpty()) {
            builder.replyMarkup(createKeyboard(keyboard));
        }
        return builder.build();
    }

    /// Превращает наши кнопки в inline-клавиатуру Telegram.
    ///
    /// @param keyboard строки кнопок
    /// @return клавиатура для Telegram
    private InlineKeyboardMarkup createKeyboard(@NotNull List<List<Button>> keyboard) {
        final List<InlineKeyboardRow> rows = new ArrayList<>();
        for (List<Button> line : keyboard) {
            final InlineKeyboardRow row = new InlineKeyboardRow();
            for (Button button : line) {
                row.add(InlineKeyboardButton.builder().text(button.text()).callbackData(button.data()).build());
            }
            rows.add(row);
        }
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    /// Переводит Telegram-сообщения в [Message].
    ///
    /// @param message объект сообщения из Telegram
    /// @return объект нашего универсального сообщения
    private Message convertTelegramMessage(org.telegram.telegrambots.meta.api.objects.message.Message message) {
        final String text = message.hasPhoto() ? message.getCaption() : message.getText();
        return Message.builder().text(text).build();
    }
}

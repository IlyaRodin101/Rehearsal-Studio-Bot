package ru.urfu.echobot.service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import ru.urfu.echobot.bot.Bot;

/// Направляет текст текущему шагу диалога, владельцу или базовым командам.
public final class BookingTextController {
    /// Команда запуска диалога.
    private static final String COMMAND_START = "/start";
    /// Команда возврата в главное меню.
    private static final String COMMAND_MENU = "/menu";
    /// Текст кнопки возврата.
    private static final String BACK_TEXT = "Назад";

    /// Управление базами владельцев.
    private final OwnerManagementService ownerService;
    /// Контроллер главного меню.
    private final BookingMenuController menuController;
    /// Контроллер возврата.
    private final BookingBackController backController;
    /// Форма клиента.
    private final BookingFormService formService;
    /// Сервис комментария и подтверждения.
    private final BookingConfirmationService confirmation;
    /// Обработчики текстовых команд.
    private final Map<String, BiConsumer<BookingText, Bot>> commandHandlers;
    /// Обработчики текста на шагах диалога.
    private final Map<BookingFlow.Step, BiConsumer<BookingText, Bot>> stepHandlers;

    /// Создаёт контроллер текста.
    ///
    /// @param ownerService   управление владельцами
    /// @param menuController главное меню
    /// @param backController возврат на предыдущий шаг
    /// @param formService    форма клиента
    /// @param confirmation   комментарий и подтверждение
    public BookingTextController(OwnerManagementService ownerService, BookingMenuController menuController,
                                 BookingBackController backController, BookingFormService formService,
                                 BookingConfirmationService confirmation) {
        this.ownerService = ownerService;
        this.menuController = menuController;
        this.backController = backController;
        this.formService = formService;
        this.confirmation = confirmation;
        commandHandlers = new HashMap<>();
        commandHandlers.put(COMMAND_START, (text, bot) ->
                menuController.returnToMenu(text.chatId(), text.flow(), bot));
        commandHandlers.put(COMMAND_MENU, (text, bot) ->
                menuController.returnToMenu(text.chatId(), text.flow(), bot));
        commandHandlers.put(BACK_TEXT, (text, bot) ->
                backController.back(text.chatId(), text.flow(), bot));
        stepHandlers = new HashMap<>();
        stepHandlers.put(BookingFlow.Step.BOOK_NAME, (text, bot) ->
                formService.handleNameText(text.chatId(), text.flow(), text.text(), bot));
        stepHandlers.put(BookingFlow.Step.BOOK_PHONE, (text, bot) ->
                formService.handlePhoneText(text.chatId(), text.flow(), text.text(), bot));
        stepHandlers.put(BookingFlow.Step.BOOK_COMMENT, (text, bot) ->
                confirmation.handleCommentText(text.chatId(), text.flow(), text.text(), bot));
    }

    /// Обрабатывает текст пользователя.
    ///
    /// @param rawText исходный текст
    /// @param chatId  идентификатор чата
    /// @param flow    состояние диалога
    /// @param bot     бот для отправки сообщений
    public void handle(String rawText, long chatId, BookingFlow flow, Bot bot) {
        String text = rawText.trim();
        BookingText context = new BookingText(chatId, flow, text);
        BiConsumer<BookingText, Bot> commandHandler = commandHandlers.get(text);
        if (commandHandler != null) {
            commandHandler.accept(context, bot);
        } else if (!ownerService.handleText(chatId, text, bot)) {
            BiConsumer<BookingText, Bot> stepHandler = stepHandlers.get(flow.step);
            if (stepHandler != null) {
                stepHandler.accept(context, bot);
            }
        }
    }
}

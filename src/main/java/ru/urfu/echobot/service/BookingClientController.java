package ru.urfu.echobot.service;

import java.util.List;
import ru.urfu.echobot.bot.Bot;

/// Обрабатывает раздел «Мои записи» и подтверждение отмены брони.
public final class BookingClientController {
    /// Позиция идентификатора зала в callback-данных.
    private static final int ROOM_PART = 2;
    /// Позиция ключа времени начала в callback-данных.
    private static final int START_PART = 3;
    /// Количество частей callback-команды с аргументом.
    private static final int CALLBACK_PARTS = 4;
    /// Действие просмотра списка записей.
    private static final String LIST = "list";
    /// Действие запроса отмены.
    private static final String CANCEL = "cancel";
    /// Действие подтверждения отмены.
    private static final String CANCEL_YES = "cancelyes";
    /// Сообщение о недоступном действии.
    private static final String UNAVAILABLE = "Действие недоступно.";

    /// Сервис сведений о бронированиях.
    private final BookingInformationService informationService;
    /// Сервис отмены записей.
    private final BookingCancellationService cancellationService;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards;

    /// Создаёт контроллер записей клиента.
    ///
    /// @param informationService  сведения о бронированиях
    /// @param cancellationService отмена записей
    /// @param keyboards           фабрика клавиатур
    public BookingClientController(BookingInformationService informationService,
                                   BookingCancellationService cancellationService,
                                   BookingKeyboardFactory keyboards) {
        this.informationService = informationService;
        this.cancellationService = cancellationService;
        this.keyboards = keyboards;
    }

    /// Показывает список будущих записей клиента.
    ///
    /// @param chatId идентификатор чата
    /// @param userId идентификатор клиента
    /// @param bot    бот для отправки сообщений
    public void showList(long chatId, long userId, Bot bot) {
        informationService.showMyBookings(chatId, userId, bot);
    }

    /// Обрабатывает callback-команду раздела «Мои записи».
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void handle(BookingCallback context, Bot bot) {
        if (LIST.equals(context.action())) {
            showList(context.chatId(), context.userId(), bot);
        } else if (context.size() == CALLBACK_PARTS) {
            handleCancellation(context, bot);
        } else {
            showUnavailable(context.chatId(), bot);
        }
    }

    /// Выполняет запрос или подтверждение отмены записи.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    private void handleCancellation(BookingCallback context, Bot bot) {
        try {
            long roomId = Long.parseLong(context.part(ROOM_PART));
            if (CANCEL.equals(context.action())) {
                cancellationService.confirmBookingCancellation(context.chatId(), context.userId(), roomId,
                        context.part(START_PART), bot);
            } else if (CANCEL_YES.equals(context.action())) {
                cancellationService.cancelBooking(context.chatId(), context.userId(), roomId,
                        context.part(START_PART), bot);
            } else {
                showUnavailable(context.chatId(), bot);
            }
        } catch (NumberFormatException exception) {
            showUnavailable(context.chatId(), bot);
        }
    }

    /// Показывает сообщение о недоступном действии.
    ///
    /// @param chatId идентификатор чата
    /// @param bot    бот для отправки сообщений
    private void showUnavailable(long chatId, Bot bot) {
        List<List<Button>> keyboard = keyboards.backKeyboard();
        bot.sendMessage(new Message(UNAVAILABLE, null, keyboard), chatId);
    }
}

package ru.urfu.echobot.service.booking;

import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.service.Button;
import ru.urfu.echobot.service.Message;
import ru.urfu.echobot.service.booking.BookingLookup.UpcomingBooking;

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
    /// Сообщение об отсутствии будущих записей.
    private static final String NO_BOOKINGS = "У вас пока нет будущих записей.";
    /// Callback главного меню.
    private static final String MENU_HOME = "menu:home";
    /// Подпись кнопки возврата.
    private static final String BACK = "Назад";
    /// Приглашение к списку записей.
    private static final String BOOKINGS_HEADER = "Ваши будущие записи:\n";
    /// Начало строки списка записей.
    private static final String ITEM_PREFIX = "• ";
    /// Callback запроса отмены записи.
    private static final String CANCEL_CALLBACK = "mine:cancel:";

    /// Поиск будущих записей.
    private final BookingLookup lookup;
    /// Сервис отмены записей.
    private final BookingCancellationService cancellationService;
    /// Форматер дат и времени.
    private final RussianDateFormatter formatter = new RussianDateFormatter();
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards = new BookingKeyboardFactory();

    /// Создаёт контроллер записей клиента.
    ///
    /// @param lookup              поиск будущих записей
    /// @param cancellationService отмена записей
    public BookingClientController(BookingLookup lookup, BookingCancellationService cancellationService) {
        this.lookup = lookup;
        this.cancellationService = cancellationService;
    }

    /// Показывает список будущих записей клиента с кнопками их отмены.
    ///
    /// @param chatId идентификатор чата
    /// @param userId идентификатор клиента
    /// @param bot    бот для отправки сообщений
    public void showList(long chatId, long userId, Bot bot) {
        List<UpcomingBooking> bookings = lookup.upcoming(userId);
        if (bookings.isEmpty()) {
            send(bot, chatId, NO_BOOKINGS, keyboards.choices(keyboards.button(BACK, MENU_HOME)));
        } else {
            List<List<Button>> rows = new ArrayList<>();
            for (UpcomingBooking item : bookings) {
                Booking booking = item.booking();
                String callback = CANCEL_CALLBACK + booking.roomId() + ":"
                        + lookup.callbackStart(booking.start());
                keyboards.addButton(rows, keyboards.button("Отменить • " + item.base().name()
                        + " • " + item.room().name(), callback), 1);
            }
            rows.add(keyboards.row(keyboards.button(BACK, MENU_HOME)));
            send(bot, chatId, bookingsMessage(bookings), keyboards.markup(rows));
        }
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
                boolean cancelled = cancellationService.cancelBooking(context.chatId(), context.userId(), roomId,
                        context.part(START_PART), bot);
                if (cancelled) {
                    showList(context.chatId(), context.userId(), bot);
                }
            } else {
                showUnavailable(context.chatId(), bot);
            }
        } catch (NumberFormatException exception) {
            showUnavailable(context.chatId(), bot);
        }
    }

    /// Формирует текст списка будущих записей.
    ///
    /// @param bookings будущие записи
    /// @return текст для раздела «Мои записи»
    private String bookingsMessage(List<UpcomingBooking> bookings) {
        StringBuilder text = new StringBuilder(BOOKINGS_HEADER);
        for (UpcomingBooking item : bookings) {
            Booking booking = item.booking();
            text.append(ITEM_PREFIX).append(item.base().name()).append(", ").append(item.room().name())
                    .append(", ").append(formatter.dateTime(booking.start())).append("-")
                    .append(formatter.endTime(booking.start(), booking.end())).append("\n");
        }
        return text.toString();
    }

    /// Показывает сообщение о недоступном действии.
    ///
    /// @param chatId идентификатор чата
    /// @param bot    бот для отправки сообщений
    private void showUnavailable(long chatId, Bot bot) {
        List<List<Button>> keyboard = keyboards.backKeyboard();
        bot.sendMessage(new Message(UNAVAILABLE, null, keyboard), chatId);
    }

    /// Отправляет текст и клавиатуру через бота.
    ///
    /// @param bot      транспорт для отправки
    /// @param chatId   идентификатор чата
    /// @param text     текст сообщения
    /// @param keyboard клавиатура сообщения
    private void send(Bot bot, long chatId, String text, List<List<Button>> keyboard) {
        bot.sendMessage(new Message(text, null, keyboard), chatId);
    }
}

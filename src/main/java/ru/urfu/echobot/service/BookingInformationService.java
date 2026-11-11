package ru.urfu.echobot.service;

import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.service.BookingLookup.UpcomingBooking;

/// Показывает будущие записи клиента.
public final class BookingInformationService {
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
    /// Форматер дат и времени.
    private final RussianDateFormatter formatter;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards = new BookingKeyboardFactory();

    /// Создаёт сервис отображения броней.
    ///
    /// @param lookup    поиск будущих записей
    /// @param formatter форматер дат и времени
    public BookingInformationService(BookingLookup lookup, RussianDateFormatter formatter) {
        this.lookup = lookup;
        this.formatter = formatter;
    }

    /// Возвращает текст со списком будущих записей клиента.
    ///
    /// @param userId идентификатор пользователя
    /// @return текст для раздела «Мои записи»
    public String myBookingsMessage(long userId) {
        return myBookingsMessage(lookup.upcoming(userId));
    }

    /// Показывает записи клиента с кнопками их отмены.
    ///
    /// @param chatId идентификатор чата
    /// @param userId идентификатор клиента
    /// @param bot    бот для отправки сообщений
    public void showMyBookings(long chatId, long userId, Bot bot) {
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
            send(bot, chatId, myBookingsMessage(bookings), keyboards.markup(rows));
        }
    }

    /// Формирует текст списка будущих записей.
    ///
    /// @param bookings будущие записи
    /// @return текст для раздела «Мои записи»
    private String myBookingsMessage(List<UpcomingBooking> bookings) {
        StringBuilder text = new StringBuilder(BOOKINGS_HEADER);
        for (UpcomingBooking item : bookings) {
            Booking booking = item.booking();
            text.append(ITEM_PREFIX).append(item.base().name()).append(", ").append(item.room().name())
                    .append(", ").append(formatter.dateTime(booking.start())).append("-")
                    .append(formatter.endTime(booking.start(), booking.end())).append("\n");
        }
        return bookings.isEmpty() ? NO_BOOKINGS : text.toString();
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

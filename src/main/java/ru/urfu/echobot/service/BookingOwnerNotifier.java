package ru.urfu.echobot.service;

import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.service.BookingLookup.UpcomingBooking;

/// Уведомляет владельца базы об отмене записи клиентом.
final class BookingOwnerNotifier {
    /// Окончание строки сообщения.
    private static final String LINE_END = ".\n";

    /// Форматер дат и времени.
    private final RussianDateFormatter formatter;

    /// Создаёт уведомитель владельца.
    ///
    /// @param formatter форматер дат и времени
    BookingOwnerNotifier(RussianDateFormatter formatter) {
        this.formatter = formatter;
    }

    /// Отправляет владельцу сведения об отменённой записи.
    ///
    /// @param item отменённая запись с базой и залом
    /// @param free true, если отмена бесплатная
    /// @param bot  бот для отправки сообщения
    void notifyCancellation(UpcomingBooking item, boolean free, Bot bot) {
        Booking booking = item.booking();
        String status = free
                ? "бесплатно"
                : "с неустойкой " + item.base().penaltyPercent() + "%";
        String comment = booking.comment().isEmpty() ? "—" : booking.comment();
        String text = "Клиент отменил запись " + status + ":\n"
                + "База «" + item.base().name() + "», " + item.room().name() + ", "
                + formatter.dateTime(booking.start()) + "-"
                + formatter.endTime(booking.start(), booking.end()) + LINE_END
                + "Клиент: " + booking.customerName() + ", " + booking.phone() + LINE_END
                + "Комментарий: " + comment;
        bot.sendMessage(new Message(text, null, List.of()), item.base().ownerId());
    }
}

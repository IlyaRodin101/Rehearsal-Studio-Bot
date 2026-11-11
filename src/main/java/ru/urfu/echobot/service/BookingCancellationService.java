package ru.urfu.echobot.service;

import java.time.LocalDateTime;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.service.BookingLookup.UpcomingBooking;

/// Подтверждает и выполняет отмену будущих записей клиента.
public final class BookingCancellationService {
    /// Сообщение об устаревшей записи.
    private static final String NOT_FOUND = "Запись не найдена или уже неактуальна.";
    /// Callback списка записей клиента.
    private static final String MINE_LIST = "mine:list";
    /// Подпись кнопки возврата.
    private static final String BACK = "Назад";
    /// Callback подтверждения отмены.
    private static final String CANCEL_YES = "mine:cancelyes:";
    /// Сообщение об успешной отмене.
    private static final String CANCELLED = "Запись отменена.";

    /// Поиск будущих записей.
    private final BookingLookup lookup;
    /// Сервис списка записей.
    private final BookingInformationService information;
    /// Хранилище бронирований.
    private final BookingRepository bookingRepository;
    /// Форматер дат и времени.
    private final RussianDateFormatter formatter;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards = new BookingKeyboardFactory();

    /// Создаёт сервис отмены записей.
    ///
    /// @param lookup            поиск будущих записей
    /// @param information       сервис списка записей
    /// @param bookingRepository хранилище броней
    /// @param formatter         форматер дат и времени
    public BookingCancellationService(BookingLookup lookup, BookingInformationService information,
                                      BookingRepository bookingRepository, RussianDateFormatter formatter) {
        this.lookup = lookup;
        this.information = information;
        this.bookingRepository = bookingRepository;
        this.formatter = formatter;
    }

    /// Просит клиента подтвердить отмену его будущей записи.
    ///
    /// @param chatId   идентификатор чата
    /// @param userId   идентификатор клиента
    /// @param roomId   идентификатор зала
    /// @param startKey ключ времени начала из callback-кнопки
    /// @param bot      бот для отправки сообщений
    public void confirmBookingCancellation(long chatId, long userId, long roomId, String startKey, Bot bot) {
        UpcomingBooking booking = lookup.find(userId, roomId, startKey);
        if (booking == null) {
            send(bot, chatId, NOT_FOUND, backToBookingsKeyboard());
        } else {
            LocalDateTime start = booking.booking().start();
            String callback = CANCEL_YES + roomId + ":" + lookup.callbackStart(start);
            send(bot, chatId, "Отменить запись в базе «" + booking.base().name() + "», зал «"
                    + booking.room().name() + "» на " + formatter.dateTime(start) + "?",
                    keyboards.choices(keyboards.button("Подтвердить отмену", callback),
                            keyboards.button(BACK, MINE_LIST)));
        }
    }

    /// Удаляет будущую запись только у клиента, которому она принадлежит.
    ///
    /// @param chatId   идентификатор чата
    /// @param userId   идентификатор клиента
    /// @param roomId   идентификатор зала
    /// @param startKey ключ времени начала из callback-кнопки
    /// @param bot      бот для отправки сообщений
    public void cancelBooking(long chatId, long userId, long roomId, String startKey, Bot bot) {
        UpcomingBooking booking = lookup.find(userId, roomId, startKey);
        if (booking == null || !bookingRepository.deleteByUserAndRoomAndStart(userId, roomId,
                booking.booking().start())) {
            send(bot, chatId, NOT_FOUND, backToBookingsKeyboard());
        } else {
            send(bot, chatId, CANCELLED, List.of());
            information.showMyBookings(chatId, userId, bot);
        }
    }

    /// Возвращает клавиатуру возврата к списку записей.
    ///
    /// @return клавиатура с кнопкой «Назад»
    private List<List<Button>> backToBookingsKeyboard() {
        return keyboards.choices(keyboards.button(BACK, MINE_LIST));
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

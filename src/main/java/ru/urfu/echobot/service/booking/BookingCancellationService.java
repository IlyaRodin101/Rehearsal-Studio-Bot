package ru.urfu.echobot.service.booking;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.service.Button;
import ru.urfu.echobot.service.Message;
import ru.urfu.echobot.service.booking.BookingLookup.UpcomingBooking;

/// Подтверждает и выполняет отмену будущих записей клиента и уведомляет владельца базы.
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
    /// Сообщение о снова доступном времени.
    private static final String TIME_IS_FREE = "Время снова доступно для брони.";
    /// Окончание строки уведомления владельцу.
    private static final String LINE_END = ".\n";
    /// Закрывающая кавычка названия базы с разделителем.
    private static final String BASE_CLOSE = "», ";

    /// Поиск будущих записей.
    private final BookingLookup lookup;
    /// Хранилище бронирований.
    private final BookingRepository bookingRepository;
    /// Форматер дат и времени.
    private final RussianDateFormatter formatter = new RussianDateFormatter();
    /// Правила бесплатной и платной отмены.
    private final BookingRules rules;
    /// Рассылка уведомлений подписчикам.
    private final HotSlotNotifier notifier;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards = new BookingKeyboardFactory();

    /// Создаёт сервис отмены с системными часами.
    ///
    /// @param lookup            поиск будущих записей
    /// @param bookingRepository хранилище броней
    public BookingCancellationService(BookingLookup lookup, BookingRepository bookingRepository) {
        this(lookup, bookingRepository, new BookingRules(Clock.systemUTC()), (base, room, booking, bot) -> {
        });
    }

    /// Создаёт сервис отмены записей без рассылки подписчикам.
    ///
    /// @param lookup            поиск будущих записей
    /// @param bookingRepository хранилище броней
    /// @param rules             правила бесплатной и платной отмены
    public BookingCancellationService(BookingLookup lookup, BookingRepository bookingRepository,
                                      BookingRules rules) {
        this(lookup, bookingRepository, rules, (base, room, booking, bot) -> {
        });
    }

    /// Создаёт сервис отмены записей с рассылкой подписчикам.
    ///
    /// @param lookup            поиск будущих записей
    /// @param bookingRepository хранилище броней
    /// @param rules             правила бесплатной и платной отмены
    /// @param notifier          рассылка уведомлений подписчикам
    public BookingCancellationService(BookingLookup lookup, BookingRepository bookingRepository,
                                      BookingRules rules, HotSlotNotifier notifier) {
        this.lookup = lookup;
        this.bookingRepository = bookingRepository;
        this.rules = rules;
        this.notifier = notifier;
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
            send(bot, chatId, confirmationText(booking),
                    keyboards.choices(keyboards.button("Да, отменить", callback),
                            keyboards.button("Оставить запись", MINE_LIST)));
        }
    }

    /// Удаляет будущую запись только у клиента, которому она принадлежит.
    ///
    /// @param chatId   идентификатор чата
    /// @param userId   идентификатор клиента
    /// @param roomId   идентификатор зала
    /// @param startKey ключ времени начала из callback-кнопки
    /// @param bot      бот для отправки сообщений
    /// @return true, если запись удалена
    public boolean cancelBooking(long chatId, long userId, long roomId, String startKey, Bot bot) {
        UpcomingBooking booking = lookup.find(userId, roomId, startKey);
        if (booking == null || !bookingRepository.deleteByUserAndRoomAndStart(userId, roomId,
                booking.booking().start())) {
            send(bot, chatId, NOT_FOUND, backToBookingsKeyboard());
            return false;
        }
        boolean free = rules.isFreeCancellation(booking.base(), booking.booking().start());
        send(bot, chatId, CANCELLED, List.of());
        if (free) {
            send(bot, chatId, TIME_IS_FREE, List.of());
        }
        notifyOwner(booking, free, userId, bot);
        if (rules.isHotSlot(booking.base(), booking.booking().start())) {
            notifier.notifySubscribers(booking.base(), booking.room(), booking.booking(), bot);
        }
        return true;
    }

    /// Собирает текст подтверждения с предупреждением о неустойке.
    ///
    /// @param item будущая запись клиента
    /// @return текст подтверждения отмены
    private String confirmationText(UpcomingBooking item) {
        Base base = item.base();
        LocalDateTime start = item.booking().start();
        String rule = rules.isFreeCancellation(base, start)
                ? "До начала больше " + base.freeCancellationHours() + " часов, отмена без неустойки."
                : "До начала меньше " + base.freeCancellationHours() + " часов, отмена платная ("
                        + base.penaltyPercent() + "% стоимости).";
        return "Запись: база «" + base.name() + BASE_CLOSE + item.room().name() + ", "
                + formatter.dateTime(start) + "-"
                + formatter.endTime(start, item.booking().end()) + LINE_END
                + rule + "\nОтменить?";
    }

    /// Отправляет владельцу сведения об отменённой записи.
    ///
    /// @param item   отменённая запись с базой и залом
    /// @param free   true, если отмена бесплатная
    /// @param userId Telegram ID клиента, отменившего запись
    /// @param bot    бот для отправки сообщения
    private void notifyOwner(UpcomingBooking item, boolean free, long userId, Bot bot) {
        Booking booking = item.booking();
        String status = free
                ? "бесплатно"
                : "с неустойкой " + item.base().penaltyPercent() + "%";
        String comment = booking.comment().isEmpty() ? "—" : booking.comment();
        String text = "Клиент отменил запись " + status + ":\n"
                + "База «" + item.base().name() + BASE_CLOSE + item.room().name() + ", "
                + formatter.dateTime(booking.start()) + "-"
                + formatter.endTime(booking.start(), booking.end()) + LINE_END
                + "Клиент: " + booking.customerName() + ", " + booking.phone() + LINE_END
                + "Telegram ID: " + userId + LINE_END
                + "Комментарий: " + comment;
        bot.sendMessage(new Message(text, null, List.of()), item.base().ownerId());
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

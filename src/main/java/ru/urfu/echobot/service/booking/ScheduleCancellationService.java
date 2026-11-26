package ru.urfu.echobot.service.booking;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.model.TelegramUser;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.repository.RoomRepository;
import ru.urfu.echobot.service.Button;
import ru.urfu.echobot.service.Message;
import ru.urfu.echobot.service.StaffAccess;
import ru.urfu.echobot.service.UserDirectory;

/// Отменяет записи от имени администратора или владельца и уведомляет участников.
public final class ScheduleCancellationService {
    /// Формат времени начала в callback-кнопках.
    private static final String CALLBACK_PATTERN = "uuuuMMddHHmm";
    /// Сообщение о недоступном действии.
    private static final String UNAVAILABLE = "Действие недоступно.";
    /// Сообщение об отсутствующей записи.
    private static final String NOT_FOUND = "Запись не найдена или уже отменена.";
    /// Сообщение о попытке отменить начавшуюся запись.
    private static final String ALREADY_STARTED = "Запись уже началась, отменить её нельзя.";
    /// Начало уведомления клиенту об отмене администратором.
    private static final String ADMIN_CANCELLED_CLIENT = "Администратор отменил вашу запись:";
    /// Начало уведомления клиенту об отмене владельцем.
    private static final String OWNER_CANCELLED_CLIENT = "Владелец отменил вашу запись:";
    /// Начало уведомления владельцу об отмене администратором.
    private static final String ADMIN_CANCELLED_OWNER = "Администратор ";
    /// Начало уведомления владельцу об отмене владельцем.
    private static final String OWNER_CANCELLED_OWNER = "Владелец отменил запись:";
    /// Продолжение уведомления владельцу об отмене администратором.
    private static final String CANCELLED_OWNER_CONTINUATION = " отменил запись:";
    /// Подпись кнопки «Горячие слоты».
    private static final String HOT_BUTTON = "Горячие слоты";
    /// Подпись кнопки «Забронировать».
    private static final String BOOK_BUTTON = "Забронировать";
    /// Callback раздела «Горячие слоты».
    private static final String MENU_HOT = "menu:hot";
    /// Callback начала бронирования.
    private static final String MENU_BOOK = "menu:book";
    /// Строка с предложением выбрать другое время.
    private static final String CHOOSE_ANOTHER_TIME = "Можете выбрать другое время.";
    /// Разделитель частей уведомления.
    private static final String NEWLINE = "\n";
    /// Открывающая кавычка названия базы.
    private static final String BASE_OPEN = "База «";
    /// Закрывающая кавычка названия базы с разделителем.
    private static final String BASE_CLOSE = "», ";
    /// Начало строки с клиентом.
    private static final String CUSTOMER = "Клиент: ";
    /// Начало строки с комментарием.
    private static final String COMMENT = "Комментарий: ";
    /// Завершение предложения.
    private static final String FULL_STOP = ".";
    /// Разделитель элементов строки.
    private static final String COMMA = ", ";

    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Хранилище залов.
    private final RoomRepository roomRepository;
    /// Хранилище броней.
    private final BookingRepository bookingRepository;
    /// Правила бронирования.
    private final BookingRules rules;
    /// Права владельцев и администраторов.
    private final StaffAccess staffAccess;
    /// Справочник известных пользователей и администраторов.
    private final UserDirectory userDirectory;
    /// Рассылка уведомлений подписчикам.
    private final HotSlotNotifier notifier;
    /// Форматер дат и времени.
    private final RussianDateFormatter formatter = new RussianDateFormatter();
    /// Формат времени начала в callback-кнопках.
    private final DateTimeFormatter callbackFormat = DateTimeFormatter.ofPattern(CALLBACK_PATTERN);

    /// Создаёт сервис отмены записей персоналом.
    ///
    /// @param baseRepository    хранилище баз
    /// @param roomRepository    хранилище залов
    /// @param bookingRepository хранилище броней
    /// @param rules             правила бронирования
    /// @param staffAccess       права владельцев и администраторов
    /// @param userDirectory     справочник известных пользователей и администраторов
    /// @param notifier          рассылка уведомлений подписчикам
    public ScheduleCancellationService(BaseRepository baseRepository, RoomRepository roomRepository,
                                       BookingRepository bookingRepository, BookingRules rules,
                                       StaffAccess staffAccess, UserDirectory userDirectory,
                                       HotSlotNotifier notifier) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.rules = rules;
        this.staffAccess = staffAccess;
        this.userDirectory = userDirectory;
        this.notifier = notifier;
    }

    /// Отменяет запись без проверки неустойки.
    ///
    /// @param cancellerId Telegram ID администратора или владельца
    /// @param baseId      идентификатор базы
    /// @param roomId      идентификатор зала
    /// @param startKey    ключ времени начала из callback-кнопки
    /// @param bot         бот для отправки уведомлений
    /// @return true, если запись удалена
    public boolean cancel(long cancellerId, long baseId, long roomId, @NotNull String startKey, @NotNull Bot bot) {
        if (!staffAccess.isStaff(cancellerId)) {
            send(bot, cancellerId, UNAVAILABLE, List.of());
            return false;
        }
        Cancellation cancellation = findCancellation(baseId, roomId, startKey);
        if (cancellation == null) {
            send(bot, cancellerId, NOT_FOUND, List.of());
            return false;
        }
        return cancelFound(cancellerId, cancellation, bot);
    }

    /// Ищет запись, которую можно отменить.
    ///
    /// @param baseId   идентификатор базы
    /// @param roomId   идентификатор зала
    /// @param startKey ключ времени начала из callback-кнопки
    /// @return найденная запись с базой и залом или null
    private Cancellation findCancellation(long baseId, long roomId, @NotNull String startKey) {
        Base base = baseRepository.findById(baseId).orElse(null);
        Room room = roomRepository.findById(roomId).orElse(null);
        if (base == null || room == null || room.baseId() != base.id()) {
            return null;
        }
        try {
            LocalDateTime start = LocalDateTime.parse(startKey, callbackFormat);
            return bookingRepository.findByRoomAndStart(roomId, start)
                    .map(booking -> new Cancellation(base, room, booking))
                    .orElse(null);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    /// Проверяет и удаляет найденную запись, отправляя уведомления.
    ///
    /// @param cancellerId  Telegram ID отменившего
    /// @param cancellation найденная запись с базой и залом
    /// @param bot          бот для отправки сообщений
    /// @return true, если запись удалена
    private boolean cancelFound(long cancellerId, @NotNull Cancellation cancellation, @NotNull Bot bot) {
        Base base = cancellation.base();
        Room room = cancellation.room();
        Booking booking = cancellation.booking();
        if (!rules.isUpcoming(base, booking)) {
            send(bot, cancellerId, ALREADY_STARTED, List.of());
            return false;
        }
        if (!bookingRepository.deleteByRoomAndStart(room.id(), booking.start())) {
            send(bot, cancellerId, NOT_FOUND, List.of());
            return false;
        }
        notifyClient(cancellerId, base, room, booking, bot);
        notifyOwner(cancellerId, base, room, booking, bot);
        if (rules.isHotSlot(base, booking.start())) {
            notifier.notifySubscribers(base, room, booking, bot);
        }
        return true;
    }

    /// Отправляет клиенту уведомление об отмене его записи.
    ///
    /// @param cancellerId Telegram ID отменившего
    /// @param base        база записи
    /// @param room        зал записи
    /// @param booking     отменённая запись
    /// @param bot         бот для отправки сообщений
    private void notifyClient(long cancellerId, Base base, Room room, Booking booking, Bot bot) {
        if (booking.userId() == cancellerId) {
            return;
        }
        String headline = staffAccess.isOwner(cancellerId) ? OWNER_CANCELLED_CLIENT : ADMIN_CANCELLED_CLIENT;
        List<String> lines = List.of(headline, bookingLine(base, room, booking), CHOOSE_ANOTHER_TIME);
        send(bot, booking.userId(), String.join(NEWLINE, lines), clientKeyboard());
    }

    /// Отправляет владельцу базы сведения об отменённой чужой записи.
    ///
    /// @param cancellerId Telegram ID отменившего
    /// @param base        база записи
    /// @param room        зал записи
    /// @param booking     отменённая запись
    /// @param bot         бот для отправки сообщений
    private void notifyOwner(long cancellerId, Base base, Room room, Booking booking, Bot bot) {
        if (base.ownerId() == cancellerId) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(cancellerHeadline(cancellerId));
        lines.add(bookingLine(base, room, booking));
        lines.add(CUSTOMER + booking.customerName() + COMMA + booking.phone() + FULL_STOP);
        if (!booking.comment().isEmpty()) {
            lines.add(COMMENT + booking.comment() + FULL_STOP);
        }
        send(bot, base.ownerId(), String.join(NEWLINE, lines), List.of());
    }

    /// Возвращает первую строку уведомления владельцу в зависимости от роли отменившего.
    ///
    /// @param cancellerId Telegram ID отменившего
    /// @return первая строка уведомления
    private String cancellerHeadline(long cancellerId) {
        if (staffAccess.isOwner(cancellerId)) {
            return OWNER_CANCELLED_OWNER;
        }
        return userDirectory.administrators().stream()
                .filter(user -> user.id() == cancellerId)
                .findFirst()
                .map(TelegramUser::mention)
                .map(mention -> ADMIN_CANCELLED_OWNER + mention + CANCELLED_OWNER_CONTINUATION)
                .orElse("Администратор отменил запись:");
    }

    /// Формирует строку с базой, залом и временем записи.
    ///
    /// @param base    база записи
    /// @param room    зал записи
    /// @param booking запись
    /// @return строка с деталями записи
    private String bookingLine(Base base, Room room, Booking booking) {
        return BASE_OPEN + base.name() + BASE_CLOSE + room.name() + COMMA
                + formatter.dateTime(booking.start()) + "-"
                + formatter.endTime(booking.start(), booking.end()) + FULL_STOP;
    }

    /// Возвращает клавиатуру уведомления клиенту.
    ///
    /// @return кнопки «Горячие слоты» и «Забронировать»
    private List<List<Button>> clientKeyboard() {
        return List.of(List.of(new Button(HOT_BUTTON, MENU_HOT),
                new Button(BOOK_BUTTON, MENU_BOOK)));
    }

    /// Отправляет текст и клавиатуру через бота.
    ///
    /// @param bot      транспорт для отправки
    /// @param chatId   идентификатор чата
    /// @param text     текст сообщения
    /// @param keyboard клавиатура сообщения
    private void send(@NotNull Bot bot, long chatId, @NotNull String text, @NotNull List<List<Button>> keyboard) {
        bot.sendMessage(new Message(text, null, keyboard), chatId);
    }

    /// Найденная запись с базой и залом.
    ///
    /// @param base    база записи
    /// @param room    зал записи
    /// @param booking отменяемая запись
    private record Cancellation(Base base, Room room, Booking booking) {
    }
}

package ru.urfu.echobot.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Ищет будущие записи клиента и дополняет их сведениями о базе и зале.
final class BookingLookup {
    /// Формат времени начала в callback-кнопках.
    private static final String CALLBACK_PATTERN = "uuuuMMddHHmm";

    /// Формат времени начала в callback-кнопках.
    private final DateTimeFormatter callbackStartFormat = DateTimeFormatter.ofPattern(CALLBACK_PATTERN);
    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Хранилище залов.
    private final RoomRepository roomRepository;
    /// Хранилище бронирований.
    private final BookingRepository bookingRepository;
    /// Правила определения будущих броней.
    private final BookingRules rules;

    /// Создаёт поиск будущих записей.
    ///
    /// @param baseRepository    хранилище баз
    /// @param roomRepository    хранилище залов
    /// @param bookingRepository хранилище броней
    /// @param rules             правила бронирования
    BookingLookup(BaseRepository baseRepository, RoomRepository roomRepository,
                  BookingRepository bookingRepository, BookingRules rules) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.rules = rules;
    }

    /// Возвращает будущие записи клиента.
    ///
    /// @param userId идентификатор клиента
    /// @return будущие записи
    List<UpcomingBooking> upcoming(long userId) {
        List<UpcomingBooking> upcoming = new ArrayList<>();
        for (Booking booking : bookingRepository.findByUser(userId)) {
            UpcomingBooking item = resolve(booking);
            if (item != null && rules.isUpcoming(item.base(), booking)) {
                upcoming.add(item);
            }
        }
        return upcoming;
    }

    /// Ищет будущую запись по залу и ключу начала.
    ///
    /// @param userId   идентификатор клиента
    /// @param roomId   идентификатор зала
    /// @param startKey ключ времени начала
    /// @return найденная запись или null
    UpcomingBooking find(long userId, long roomId, String startKey) {
        return bookingRepository.findByUser(userId).stream()
                .filter(booking -> booking.roomId() == roomId && callbackStart(booking.start()).equals(startKey))
                .map(this::resolve)
                .filter(item -> item != null && rules.isUpcoming(item.base(), item.booking()))
                .findFirst()
                .orElse(null);
    }

    /// Форматирует время начала для callback-кнопки.
    ///
    /// @param start время начала
    /// @return строка вида `uuuuMMddHHmm`
    String callbackStart(LocalDateTime start) {
        return start.format(callbackStartFormat);
    }

    /// Дополняет бронь сведениями о зале и базе.
    ///
    /// @param booking бронь
    /// @return запись с залом и базой или null, если они удалены
    private UpcomingBooking resolve(Booking booking) {
        Room room = roomRepository.findById(booking.roomId()).orElse(null);
        Base base = room == null ? null : baseRepository.findById(room.baseId()).orElse(null);
        return base == null ? null : new UpcomingBooking(booking, room, base);
    }

    /// Будущая запись вместе с залом и базой.
    ///
    /// @param booking бронь
    /// @param room    зал
    /// @param base    база
    record UpcomingBooking(Booking booking, Room room, Base base) {
    }
}

package ru.urfu.echobot.service.booking;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.HotSlot;

/// Форматирует подписи и callback-ключи «горячих» слотов.
public final class HotSlotFormatter {
    /// Формат времени начала в callback-кнопках.
    private static final String CALLBACK_PATTERN = "uuuuMMddHHmm";

    /// Форматер дат и времени.
    private final RussianDateFormatter dateFormatter = new RussianDateFormatter();
    /// Формат времени начала в callback-кнопках.
    private final DateTimeFormatter callbackFormat = DateTimeFormatter.ofPattern(CALLBACK_PATTERN);
    /// Правила бронирования для определения дат базы.
    private final BookingRules rules;

    /// Создаёт форматер «горячих» слотов.
    ///
    /// @param rules правила бронирования
    public HotSlotFormatter(BookingRules rules) {
        this.rules = rules;
    }

    /// Форматирует подпись кнопки слота.
    ///
    /// @param base база, к которой относится слот
    /// @param slot слот
    /// @return строка вида «Зал 1, сегодня, 20:00-22:00»
    public String label(Base base, HotSlot slot) {
        return slot.room().name() + ", "
                + dateFormatter.relativeDate(slot.start().toLocalDate(), rules.today(base)) + ", "
                + dateFormatter.timeRange(slot.start(), slot.end());
    }

    /// Форматирует ключ времени начала для callback-кнопки.
    ///
    /// @param start время начала слота
    /// @return ключ вида «202610202000»
    public String startKey(LocalDateTime start) {
        return start.format(callbackFormat);
    }

    /// Разбирает ключ времени начала из callback-кнопки.
    ///
    /// @param key ключ вида «202610202000»
    /// @return время начала слота
    /// @throws java.time.format.DateTimeParseException если ключ некорректен
    public LocalDateTime parseStart(String key) {
        return LocalDateTime.parse(key, callbackFormat);
    }
}

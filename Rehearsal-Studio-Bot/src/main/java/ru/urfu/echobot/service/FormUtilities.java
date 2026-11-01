package ru.urfu.echobot.service;

/**
 * Объединяет утилиты для обработки формы бронирования.
 */
public final class FormUtilities {
    /** Нормализатор российских телефонных номеров. */
    final PhoneNormalizer phoneNormalizer;

    /** Форматер дат и времени. */
    final RussianDateFormatter formatter;

    /**
     * Создаёт набор утилит формы.
     *
     * @param phoneNormalizer нормализатор телефонных номеров
     * @param formatter форматер дат и времени
     */
    public FormUtilities(PhoneNormalizer phoneNormalizer, RussianDateFormatter formatter) {
        this.phoneNormalizer = phoneNormalizer;
        this.formatter = formatter;
    }
}

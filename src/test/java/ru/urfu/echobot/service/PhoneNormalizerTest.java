package ru.urfu.echobot.service;

import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/// Тесты нормализации российских номеров телефонов.
final class PhoneNormalizerTest {
    private static final String NORMALIZED_PHONE = "+79991234567";

    /// Проверяемый нормализатор.
    private final PhoneNormalizer normalizer = new PhoneNormalizer();

    /// Given: номер с +7, пробелами и дефисами. When: нормализуем. Then: получаем +7 и 10 цифр.
    @Test
    void plusSevenWithSeparatorsIsNormalized() {
        Assertions.assertEquals(Optional.of(NORMALIZED_PHONE), normalizer.normalize("+7 999 123-45-67"));
    }

    /// Given: номер с 8 и скобками. When: нормализуем. Then: 8 заменяется на +7.
    @Test
    void eightWithBracketsIsNormalized() {
        Assertions.assertEquals(Optional.of(NORMALIZED_PHONE), normalizer.normalize("8 (999) 123-45-67"));
    }

    /// Given: слишком короткий номер. When: нормализуем. Then: результат пустой.
    @Test
    void shortNumberIsRejected() {
        Assertions.assertTrue(normalizer.normalize("12345").isEmpty());
    }

    /// Given: номер с буквами. When: нормализуем. Then: результат пустой.
    @Test
    void numberWithLettersIsRejected() {
        Assertions.assertTrue(normalizer.normalize("+7 999 abc-45-67").isEmpty());
    }

    /// Given: пустая строка. When: нормализуем. Then: результат пустой.
    @Test
    void emptyInputIsRejected() {
        Assertions.assertTrue(normalizer.normalize("").isEmpty());
    }
}

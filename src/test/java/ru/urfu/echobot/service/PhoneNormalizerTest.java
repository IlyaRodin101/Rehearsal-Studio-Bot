package ru.urfu.echobot.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Тесты нормализации российских номеров телефонов.
final class PhoneNormalizerTest {
    /// Проверяемый нормализатор.
    private final PhoneNormalizer normalizer = new PhoneNormalizer();

    /// Given: номер с +7, пробелами и дефисами. When: нормализуем. Then: получаем +7 и 10 цифр.
    @Test
    void plusSevenWithSeparatorsIsNormalized() {
        assertEquals(Optional.of("+79991234567"), normalizer.normalize("+7 999 123-45-67"));
    }

    /// Given: номер с 8 и скобками. When: нормализуем. Then: 8 заменяется на +7.
    @Test
    void eightWithBracketsIsNormalized() {
        assertEquals(Optional.of("+79991234567"), normalizer.normalize("8 (999) 123-45-67"));
    }

    /// Given: слишком короткий номер. When: нормализуем. Then: результат пустой.
    @Test
    void shortNumberIsRejected() {
        assertTrue(normalizer.normalize("12345").isEmpty());
    }

    /// Given: номер с буквами. When: нормализуем. Then: результат пустой.
    @Test
    void numberWithLettersIsRejected() {
        assertTrue(normalizer.normalize("+7 999 abc-45-67").isEmpty());
    }

    /// Given: пустая строка. When: нормализуем. Then: результат пустой.
    @Test
    void emptyInputIsRejected() {
        assertTrue(normalizer.normalize("").isEmpty());
    }
}
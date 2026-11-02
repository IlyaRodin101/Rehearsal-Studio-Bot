package ru.urfu.echobot.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/// Приводит российские номера телефонов к виду +7XXXXXXXXXX.
public final class PhoneNormalizer {
    /// Код России, с которого начинается нормализованный номер.
    private static final String COUNTRY_PREFIX = "+7";

    /// Российский номер: +7 или 8, затем 10 цифр. Пробелы, скобки и дефисы между группами допускаются.
    private final Pattern russianPhone = Pattern.compile(
            "^(?:\\+7|8)[\\s()\\-]*(\\d{3})[\\s()\\-]*(\\d{3})[\\s()\\-]*(\\d{2})[\\s()\\-]*(\\d{2})$");

    /// Нормализует номер телефона.
    ///
    /// @param input введённый пользователем текст
    /// @return номер вида +7XXXXXXXXXX или пустой результат, если ввод не похож на российский номер
    public Optional<String> normalize(@NotNull String input) {
        Matcher matcher = russianPhone.matcher(input.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        StringBuilder result = new StringBuilder(COUNTRY_PREFIX);
        for (int group = 1; group <= matcher.groupCount(); group++) {
            result.append(matcher.group(group));
        }
        return Optional.of(result.toString());
    }
}

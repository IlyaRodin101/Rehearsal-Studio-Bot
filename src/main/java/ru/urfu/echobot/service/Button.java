package ru.urfu.echobot.service;

import org.jetbrains.annotations.NotNull;

/// Кнопка под сообщением.
///
/// @param text подпись на кнопке
/// @param data данные, которые вернутся в сервис при нажатии на кнопку
public record Button(@NotNull String text, @NotNull String data) {
}
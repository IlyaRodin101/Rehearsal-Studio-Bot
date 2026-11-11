package ru.urfu.echobot.service;

/// Данные текстового сообщения, необходимые обработчику текущего шага.
///
/// @param chatId идентификатор чата
/// @param flow   состояние диалога пользователя
/// @param text   текст сообщения без внешних пробелов
record BookingText(long chatId, BookingFlow flow, String text) {
}

package ru.urfu.echobot.model;

/// Часы работы базы в один день недели.
///
/// @param openHour  час открытия, от 0 до 23
/// @param closeHour час закрытия, от 1 до 24
public record OpeningHours(int openHour, int closeHour) {
}

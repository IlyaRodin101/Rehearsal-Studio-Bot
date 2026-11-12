package ru.urfu.echobot.service;

import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.Room;

/// Отправляет подписчикам уведомления о новом «горячем» слоте.
public interface HotSlotNotifier {

    /// Сообщает подписчикам базы об освободившемся времени.
    ///
    /// @param base    база, в которой освободилось время
    /// @param room    зал
    /// @param booking освободившаяся бронь
    /// @param bot     бот для отправки сообщений
    void notifySubscribers(Base base, Room room, Booking booking, Bot bot);
}

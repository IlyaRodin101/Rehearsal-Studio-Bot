package ru.urfu.echobot.service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;

/// Хранит незавершённую форму и выбранные объекты одного владельца базы.
final class OwnerSession {
    /// Текущий шаг диалога.
    OwnerStep step = OwnerStep.IDLE;
    /// Идентификатор выбранной базы.
    long selectedBaseId;
    /// Идентификатор выбранного зала.
    long selectedRoomId;
    /// Выбранный день недели при редактировании графика.
    int selectedDay;
    /// День недели при создании графика.
    int day;
    /// Час открытия при создании графика.
    int openHour;
    /// Срок бронирования новой базы.
    int leadHours = 2;
    /// Название новой базы.
    String name;
    /// Адрес новой базы.
    String address;
    /// Часовой пояс новой базы.
    String timezone;
    /// Оборудование новой базы.
    String equipment = "";
    /// Название зала, ожидающего выбора обязательности комментария.
    String pendingRoomName;
    /// График новой базы.
    final Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
    /// Залы, добавленные до сохранения новой базы.
    final List<Room> newRooms = new ArrayList<>();

    /// Полностью сбрасывает выбор и незавершённую форму.
    void reset() {
        selectedBaseId = 0;
        selectedRoomId = 0;
        selectedDay = 0;
        resetDraft();
        step = OwnerStep.IDLE;
    }

    /// Очищает данные создаваемой базы, сохраняя выбранные объекты.
    void resetDraft() {
        day = 0;
        openHour = 0;
        leadHours = 2;
        name = null;
        address = null;
        timezone = null;
        equipment = "";
        pendingRoomName = null;
        schedule.clear();
        newRooms.clear();
    }
}

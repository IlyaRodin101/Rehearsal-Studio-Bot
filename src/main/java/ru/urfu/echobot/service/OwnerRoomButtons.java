package ru.urfu.echobot.service;

import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.model.Room;

/// Создаёт клавиатуры списка залов и меню выбранного зала.
final class OwnerRoomButtons {
    /// Начало названия зала в сообщениях.
    private static final String ROOM_PREFIX = "Зал «";
    /// Callback-префикс выбора зала.
    private static final String ROOM_CALLBACK = "owner:room:";
    /// Callback-префикс переключения комментария.
    private static final String TOGGLE_CALLBACK = "owner:toggle:";
    /// Callback-префикс удаления зала.
    private static final String DELETE_CALLBACK = "owner:delete:";

    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Проверка прав и принадлежности.
    private final OwnerAccess access;
    /// Сессии владельцев.
    private final OwnerSessions sessions;

    /// Создаёт фабрику клавиатур залов.
    ///
    /// @param messaging сообщения и клавиатуры
    /// @param access    проверка прав
    /// @param sessions  сессии владельцев
    OwnerRoomButtons(OwnerMessaging messaging, OwnerAccess access, OwnerSessions sessions) {
        this.messaging = messaging;
        this.access = access;
        this.sessions = sessions;
    }

    /// Возвращает название зала для сообщения.
    ///
    /// @param room зал
    /// @return начало сообщения с названием зала
    String roomTitle(Room room) {
        return ROOM_PREFIX + room.name() + "». Что изменить?";
    }

    /// Создаёт кнопки активных залов выбранной базы.
    ///
    /// @param userId идентификатор владельца
    /// @return строки кнопок
    List<List<Button>> roomsButtons(long userId) {
        List<List<Button>> rows = new ArrayList<>();
        for (Room room : access.roomRepository().findByBaseId(sessions.get(userId).selectedBaseId)) {
            messaging.addButton(rows, messaging.button(room.name(), ROOM_CALLBACK + room.id()), 2);
        }
        rows.add(messaging.row(messaging.button("Добавить зал", "owner:addroom")));
        rows.add(messaging.row(messaging.button(OwnerMessaging.BACK_TEXT, "owner:setting")));
        return rows;
    }

    /// Создаёт кнопки меню выбранного зала.
    ///
    /// @param room выбранный зал
    /// @return строки кнопок
    List<List<Button>> roomMenuButtons(Room room) {
        String requirement = room.commentRequired() ? "да" : "нет";
        List<List<Button>> rows = new ArrayList<>();
        rows.add(messaging.row(messaging.button("Название", "owner:roomname:" + room.id()),
                messaging.button("Комментарий обязателен: " + requirement, TOGGLE_CALLBACK + room.id())));
        rows.add(messaging.row(messaging.button("Удалить зал", DELETE_CALLBACK + room.id())));
        rows.add(messaging.row(messaging.button(OwnerMessaging.BACK_TEXT, "owner:rooms")));
        return rows;
    }
}

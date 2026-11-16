package ru.urfu.echobot.service.owner;

import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.service.Button;

/// Создаёт клавиатуры списка и меню баз и залов владельца.
final class OwnerButtons {
    /// Подпись кнопки удаления базы.
    private static final String DELETE_BASE_TEXT = "Удалить базу";
    /// Начало callback выбора базы.
    private static final String BASE_CALLBACK = "owner:base:";
    /// Начало callback удаления базы.
    private static final String DELETE_BASE_CALLBACK = "owner:deletebase:";
    /// Начало названия зала в сообщениях.
    private static final String ROOM_PREFIX = "Зал «";
    /// Callback-префикс выбора зала.
    private static final String ROOM_CALLBACK = "owner:room:";
    /// Callback-префикс переключения комментария.
    private static final String TOGGLE_CALLBACK = "owner:toggle:";
    /// Callback-префикс удаления зала.
    private static final String DELETE_ROOM_CALLBACK = "owner:delete:";
    /// Подпись кнопки названия.
    private static final String NAME_TEXT = "Название";
    /// Callback списка залов базы.
    private static final String ROOMS_CALLBACK = "owner:rooms";

    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Проверка прав и принадлежности.
    private final OwnerAccess access;
    /// Сессии владельцев.
    private final OwnerSessions sessions;

    /// Создаёт фабрику клавиатур владельца.
    ///
    /// @param messaging      сообщения и клавиатуры
    /// @param baseRepository хранилище баз
    /// @param access         проверка прав
    /// @param sessions       сессии владельцев
    OwnerButtons(OwnerMessaging messaging, BaseRepository baseRepository, OwnerAccess access,
                 OwnerSessions sessions) {
        this.messaging = messaging;
        this.baseRepository = baseRepository;
        this.access = access;
        this.sessions = sessions;
    }

    /// Создаёт кнопки списка баз владельца.
    ///
    /// @param userId идентификатор владельца
    /// @return строки кнопок
    List<List<Button>> baseListButtons(long userId) {
        List<List<Button>> rows = new ArrayList<>();
        for (Base base : baseRepository.findByOwnerId(userId)) {
            messaging.addButton(rows, messaging.button(base.name(), BASE_CALLBACK + base.id()), 2);
        }
        rows.add(messaging.row(messaging.button("Добавить базу", "owner:add")));
        rows.add(messaging.row(messaging.button(OwnerMessaging.BACK_TEXT, "menu:home")));
        return rows;
    }

    /// Создаёт кнопки меню базы.
    ///
    /// @param base выбранная база
    /// @return строки кнопок
    List<List<Button>> baseMenuButtons(Base base) {
        List<List<Button>> rows = new ArrayList<>();
        rows.add(messaging.row(messaging.button(NAME_TEXT, "owner:edit:name"),
                messaging.button("Адрес", "owner:edit:address")));
        rows.add(messaging.row(messaging.button("Часовой пояс", "owner:edit:timezone"),
                messaging.button("График работы", "owner:schedule")));
        rows.add(messaging.row(messaging.button("Залы", ROOMS_CALLBACK),
                messaging.button("Срок бронирования", "owner:edit:lead")));
        rows.add(messaging.row(messaging.button("Оборудование", "owner:edit:equipment"),
                messaging.button("Правила отмены", "owner:cancelrules")));
        rows.add(messaging.row(messaging.button(DELETE_BASE_TEXT, DELETE_BASE_CALLBACK + base.id())));
        rows.add(messaging.row(messaging.button(OwnerMessaging.BACK_TEXT, "owner:list")));
        return rows;
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
        rows.add(messaging.row(messaging.button(NAME_TEXT, "owner:roomname:" + room.id()),
                messaging.button("Комментарий обязателен: " + requirement, TOGGLE_CALLBACK + room.id())));
        rows.add(messaging.row(messaging.button("Удалить зал", DELETE_ROOM_CALLBACK + room.id())));
        rows.add(messaging.row(messaging.button(OwnerMessaging.BACK_TEXT, ROOMS_CALLBACK)));
        return rows;
    }
}

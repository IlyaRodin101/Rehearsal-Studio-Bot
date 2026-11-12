package ru.urfu.echobot.service;

import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Room;

/// Управляет залами существующих баз владельца.
final class OwnerRoomService {
    /// Callback-префикс выбора зала.
    private static final String ROOM_CALLBACK = "owner:room:";
    /// Начало названия зала в сообщениях.
    private static final String ROOM_PREFIX = "Зал «";

    /// Сессии владельцев.
    private final OwnerSessions sessions;
    /// Проверка прав и принадлежности.
    private final OwnerAccess access;
    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Автомат состояний.
    private final OwnerStateMachine stateMachine;
    /// Клавиатуры залов.
    private final OwnerRoomButtons buttons;
    /// Добавление новых залов.
    private final OwnerRoomAddService roomAdd;

    /// Создаёт сервис залов.
    ///
    /// @param sessions     сессии владельцев
    /// @param access       проверка прав
    /// @param messaging    сообщения и клавиатуры
    /// @param stateMachine автомат состояний
    /// @param roomCreation залы новой базы
    OwnerRoomService(OwnerSessions sessions, OwnerAccess access, OwnerMessaging messaging,
                     OwnerStateMachine stateMachine, OwnerRoomCreationService roomCreation) {
        this.sessions = sessions;
        this.access = access;
        this.messaging = messaging;
        this.stateMachine = stateMachine;
        buttons = new OwnerRoomButtons(messaging, access, sessions);
        roomAdd = new OwnerRoomAddService(sessions, access, messaging, stateMachine, roomCreation, buttons);
    }

    /// Показывает активные залы выбранной базы.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void showRooms(long userId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        Base base = access.selectedBase(userId, session, bot);
        if (base != null) {
            stateMachine.reset(session);
            messaging.send(bot, userId, "Залы базы:", buttons.roomsButtons(userId));
        }
    }

    /// Открывает меню выбранного зала.
    ///
    /// @param userId идентификатор владельца
    /// @param roomId идентификатор зала
    /// @param bot    бот для отправки
    void selectRoom(long userId, long roomId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        Room room = access.activeOwnedRoom(userId, session, roomId, bot);
        if (room != null) {
            session.selectedRoomId = room.id();
            stateMachine.reset(session);
            messaging.send(bot, userId, buttons.roomTitle(room), buttons.roomMenuButtons(room));
        }
    }

    /// Начинает добавление зала.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void askRoomName(long userId, Bot bot) {
        roomAdd.askRoomName(userId, bot);
    }

    /// Принимает название нового зала существующей базы.
    ///
    /// @param userId идентификатор владельца
    /// @param text   введённое название
    /// @param bot    бот для отправки
    void handleRoomName(long userId, String text, Bot bot) {
        roomAdd.handleRoomName(userId, text, bot);
    }

    /// Сохраняет новый зал после выбора обязательности комментария.
    ///
    /// @param userId идентификатор владельца
    /// @param value  ответ yes или no
    /// @param bot    бот для отправки
    void setNewRoomRequirement(long userId, String value, Bot bot) {
        roomAdd.setNewRoomRequirement(userId, value, bot);
    }

    /// Начинает редактирование названия зала.
    ///
    /// @param userId идентификатор владельца
    /// @param roomId идентификатор зала
    /// @param bot    бот для отправки
    void beginRoomNameEdit(long userId, long roomId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (access.activeOwnedRoom(userId, session, roomId, bot) != null
                && stateMachine.transition(session, OwnerStateMachine.Event.EDIT_ROOM_NAME)) {
            messaging.send(bot, userId, "Введите новое название зала.", messaging.backKeyboard());
        }
    }

    /// Переключает обязательность комментария у зала.
    ///
    /// @param userId идентификатор владельца
    /// @param roomId идентификатор зала
    /// @param bot    бот для отправки
    void toggleRoom(long userId, long roomId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        Room room = access.activeOwnedRoom(userId, session, roomId, bot);
        if (room != null) {
            access.roomRepository().update(new Room(room.id(), room.baseId(), room.name(),
                    !room.commentRequired()));
            selectRoom(userId, room.id(), bot);
        }
    }

    /// Просит подтвердить удаление зала.
    ///
    /// @param userId идентификатор владельца
    /// @param roomId идентификатор зала
    /// @param bot    бот для отправки
    void confirmRoomDelete(long userId, long roomId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        Room room = access.activeOwnedRoom(userId, session, roomId, bot);
        if (room != null && stateMachine.transition(session, OwnerStateMachine.Event.DELETE_ROOM)) {
            messaging.send(bot, userId, "Удалить зал «" + room.name()
                    + "»? Новые записи в нём будут недоступны, уже созданные сохранятся.",
                    messaging.choices(messaging.button("Удалить", "owner:deleteyes:" + room.id()),
                            messaging.button("Отмена", ROOM_CALLBACK + room.id())));
        }
    }

    /// Удаляет зал после подтверждения.
    ///
    /// @param userId идентификатор владельца
    /// @param roomId идентификатор зала
    /// @param bot    бот для отправки
    void deleteRoom(long userId, long roomId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        Room room = access.activeOwnedRoom(userId, session, roomId, bot);
        if (room != null && session.step == OwnerStep.DELETE_ROOM
                && stateMachine.transition(session, OwnerStateMachine.Event.DELETE_ROOM_CONFIRMED)) {
            access.roomRepository().deactivate(room.id());
            messaging.send(bot, userId, ROOM_PREFIX + room.name() + "» удалён.", buttons.roomsButtons(userId));
        }
    }

    /// Сохраняет новое название зала.
    ///
    /// @param room  зал
    /// @param value новое название
    void renameRoom(Room room, String value) {
        access.roomRepository().update(new Room(room.id(), room.baseId(), value, room.commentRequired()));
    }

    /// Создаёт кнопки активных залов выбранной базы.
    ///
    /// @param userId идентификатор владельца
    /// @return строки кнопок
    List<List<Button>> roomsButtons(long userId) {
        return buttons.roomsButtons(userId);
    }
}

package ru.urfu.echobot.service.owner;

import ru.urfu.echobot.service.Button;
import ru.urfu.echobot.service.GenericStateMachine;
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
    /// Ответ «да» на вопрос об обязательности комментария.
    private static final String YES = "yes";
    /// Ответ «нет» на вопрос об обязательности комментария.
    private static final String NO = "no";
    /// Текст запроса названия зала.
    private static final String ASK_ROOM = "Введите название нового зала.";
    /// Вопрос об обязательности комментария.
    private static final String ASK_REQUIRED = "Комментарий к записи в этом зале обязателен?";
    /// Заголовок списка залов.
    private static final String ROOMS_HEADER = "Залы базы:";

    /// Сессии владельцев.
    private final OwnerSessions sessions;
    /// Проверка прав и принадлежности.
    private final OwnerAccess access;
    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Автомат состояний.
    private final GenericStateMachine<OwnerSession.Step, OwnerSession.Event> stateMachine;
    /// Клавиатуры залов.
    private final OwnerButtons buttons;
    /// Черновик новой базы.
    private final OwnerDraftScheduleService draftSchedule;

    /// Создаёт сервис залов.
    ///
    /// @param sessions      сессии владельцев
    /// @param access        проверка прав
    /// @param messaging     сообщения и клавиатуры
    /// @param stateMachine  автомат состояний
    /// @param draftSchedule черновик новой базы
    /// @param buttons       клавиатуры баз и залов
    OwnerRoomService(OwnerSessions sessions, OwnerAccess access, OwnerMessaging messaging,
                     GenericStateMachine<OwnerSession.Step, OwnerSession.Event> stateMachine,
                     OwnerDraftScheduleService draftSchedule, OwnerButtons buttons) {
        this.sessions = sessions;
        this.access = access;
        this.messaging = messaging;
        this.stateMachine = stateMachine;
        this.draftSchedule = draftSchedule;
        this.buttons = buttons;
    }

    /// Показывает активные залы выбранной базы.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void showRooms(long userId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        Base base = access.selectedBase(userId, session, bot);
        if (base != null) {
            stateMachine.reset(session, OwnerSession.Step.IDLE);
            messaging.send(bot, userId, ROOMS_HEADER, buttons.roomsButtons(userId));
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
            stateMachine.reset(session, OwnerSession.Step.IDLE);
            messaging.send(bot, userId, buttons.roomTitle(room), buttons.roomMenuButtons(room));
        }
    }

    /// Начинает добавление зала.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void askRoomName(long userId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (stateMachine.transition(session, OwnerSession.Event.ADD_ROOM_TO_BASE)) {
            messaging.send(bot, userId, ASK_ROOM, messaging.backKeyboard());
        }
    }

    /// Принимает название нового зала существующей базы.
    ///
    /// @param userId идентификатор владельца
    /// @param text   введённое название
    /// @param bot    бот для отправки
    void handleRoomName(long userId, String text, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (text.isBlank()) {
            messaging.send(bot, userId, ASK_ROOM, messaging.backKeyboard());
        } else if (stateMachine.transition(session, OwnerSession.Event.ENTER_ROOM_NAME)) {
            session.pendingRoomName = text.trim();
            messaging.send(bot, userId, ASK_REQUIRED,
                    messaging.choices(messaging.button("Да", "owner:newrequired:yes"),
                            messaging.button("Нет", "owner:newrequired:no"),
                            messaging.button(OwnerMessaging.BACK_TEXT, OwnerMessaging.BACK_CALLBACK)));
        }
    }

    /// Сохраняет новый зал после выбора обязательности комментария.
    ///
    /// @param userId идентификатор владельца
    /// @param value  ответ yes или no
    /// @param bot    бот для отправки
    void setNewRoomRequirement(long userId, String value, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (session.step == OwnerSession.Step.CREATE_ROOM_REQUIRED) {
            draftSchedule.setRequirement(userId, value, bot);
        } else if (session.step == OwnerSession.Step.ROOM_CREATE_REQUIRED && List.of(YES, NO).contains(value)) {
            saveRoom(userId, access.selectedBase(userId, session, bot), value, bot);
        } else {
            access.requireOwner(userId, bot);
            messaging.send(bot, userId, OwnerMessaging.UNAVAILABLE, messaging.backKeyboard());
        }
    }

    /// Сохраняет зал существующей базы.
    ///
    /// @param userId идентификатор владельца
    /// @param base   выбранная база
    /// @param value  ответ yes или no
    /// @param bot    бот для отправки
    private void saveRoom(long userId, Base base, String value, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (base != null && stateMachine.transition(session, OwnerSession.Event.SET_ROOM_REQUIREMENT)) {
            access.roomRepository().save(new Room(0, base.id(), session.pendingRoomName, YES.equals(value)));
            session.pendingRoomName = null;
            messaging.send(bot, userId, ROOMS_HEADER, buttons.roomsButtons(userId));
        }
    }

    /// Начинает редактирование названия зала.
    ///
    /// @param userId идентификатор владельца
    /// @param roomId идентификатор зала
    /// @param bot    бот для отправки
    void beginRoomNameEdit(long userId, long roomId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (access.activeOwnedRoom(userId, session, roomId, bot) != null
                && stateMachine.transition(session, OwnerSession.Event.EDIT_ROOM_NAME)) {
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
        if (room != null && stateMachine.transition(session, OwnerSession.Event.DELETE_ROOM)) {
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
        if (room != null && session.step == OwnerSession.Step.DELETE_ROOM
                && stateMachine.transition(session, OwnerSession.Event.DELETE_ROOM_CONFIRMED)) {
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

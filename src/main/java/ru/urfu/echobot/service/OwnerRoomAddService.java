package ru.urfu.echobot.service;

import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Room;

/// Добавляет новые залы в существующую базу владельца.
final class OwnerRoomAddService {
    /// Ответ «да» на вопрос об обязательности комментария.
    private static final String YES = "yes";
    /// Ответ «нет» на вопрос об обязательности комментария.
    private static final String NO = "no";
    /// Текст запроса названия зала.
    private static final String ASK_ROOM = "Введите название нового зала.";
    /// Вопрос об обязательности комментария.
    private static final String ASK_REQUIRED = "Комментарий к записи в этом зале обязателен?";

    /// Сессии владельцев.
    private final OwnerSessions sessions;
    /// Проверка прав и принадлежности.
    private final OwnerAccess access;
    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Автомат состояний.
    private final OwnerStateMachine stateMachine;
    /// Залы новой базы.
    private final OwnerRoomCreationService roomCreation;
    /// Клавиатуры залов.
    private final OwnerRoomButtons buttons;

    /// Создаёт сервис добавления залов.
    ///
    /// @param sessions     сессии владельцев
    /// @param access       проверка прав
    /// @param messaging    сообщения и клавиатуры
    /// @param stateMachine автомат состояний
    /// @param roomCreation залы новой базы
    /// @param buttons      клавиатуры залов
    OwnerRoomAddService(OwnerSessions sessions, OwnerAccess access, OwnerMessaging messaging,
                        OwnerStateMachine stateMachine, OwnerRoomCreationService roomCreation,
                        OwnerRoomButtons buttons) {
        this.sessions = sessions;
        this.access = access;
        this.messaging = messaging;
        this.stateMachine = stateMachine;
        this.roomCreation = roomCreation;
        this.buttons = buttons;
    }

    /// Начинает добавление зала.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void askRoomName(long userId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (stateMachine.transition(session, OwnerStateMachine.Event.ADD_ROOM_TO_BASE)) {
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
        } else if (stateMachine.transition(session, OwnerStateMachine.Event.ENTER_ROOM_NAME)) {
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
        if (session.step == OwnerStep.CREATE_ROOM_REQUIRED) {
            roomCreation.setRequirement(userId, value, bot);
        } else if (session.step == OwnerStep.ROOM_CREATE_REQUIRED && List.of(YES, NO).contains(value)) {
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
        if (base != null && stateMachine.transition(session, OwnerStateMachine.Event.SET_ROOM_REQUIREMENT)) {
            access.roomRepository().save(new Room(0, base.id(), session.pendingRoomName, YES.equals(value)));
            session.pendingRoomName = null;
            messaging.send(bot, userId, "Залы базы:", buttons.roomsButtons(userId));
        }
    }
}

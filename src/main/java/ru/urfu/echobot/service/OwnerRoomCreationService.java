package ru.urfu.echobot.service;

import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Room;

/// Создаёт залы новой базы до её сохранения.
final class OwnerRoomCreationService {
    /// Текст запроса названия первого зала.
    private static final String ASK_FIRST_ROOM = "Введите название первого зала.";
    /// Текст запроса названия зала.
    private static final String ASK_ROOM = "Введите название зала.";
    /// Вопрос об обязательности комментария.
    private static final String ASK_REQUIRED = "Комментарий к записи в этом зале обязателен?";
    /// Вопрос о добавлении ещё одного зала.
    private static final String ASK_MORE_ROOMS = "Добавить ещё зал?";
    /// Ответ «да» на вопрос об обязательности комментария.
    private static final String YES = "yes";
    /// Ответ «нет» на вопрос об обязательности комментария.
    private static final String NO = "no";

    /// Сессии владельцев.
    private final OwnerSessions sessions;
    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Автомат состояний.
    private final OwnerStateMachine stateMachine;

    /// Создаёт сервис залов новой базы.
    ///
    /// @param sessions     сессии владельцев
    /// @param messaging    сообщения и клавиатуры
    /// @param stateMachine автомат состояний
    OwnerRoomCreationService(OwnerSessions sessions, OwnerMessaging messaging, OwnerStateMachine stateMachine) {
        this.sessions = sessions;
        this.messaging = messaging;
        this.stateMachine = stateMachine;
    }

    /// Просит ввести название первого зала после заполнения графика.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void promptFirstRoom(long userId, Bot bot) {
        messaging.send(bot, userId, ASK_FIRST_ROOM, messaging.backKeyboard());
    }

    /// Принимает название зала новой базы.
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
            askRequirement(userId, bot);
        }
    }

    /// Сохраняет зал новой базы после выбора обязательности комментария.
    ///
    /// @param userId идентификатор владельца
    /// @param value  ответ yes или no
    /// @param bot    бот для отправки
    void setRequirement(long userId, String value, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (session.step == OwnerStep.CREATE_ROOM_REQUIRED && List.of(YES, NO).contains(value)
                && stateMachine.transition(session, OwnerStateMachine.Event.SET_ROOM_REQUIREMENT)) {
            session.newRooms.add(new Room(0, 0, session.pendingRoomName, YES.equals(value)));
            session.pendingRoomName = null;
            messaging.send(bot, userId, ASK_MORE_ROOMS,
                    messaging.choices(messaging.button("Добавить", "owner:newroom:add"),
                            messaging.button("Достаточно", "owner:newroom:done"),
                            messaging.button(OwnerMessaging.BACK_TEXT, OwnerMessaging.BACK_CALLBACK)));
        }
    }

    /// Добавляет ещё один зал или завершает список залов.
    ///
    /// @param userId идентификатор владельца
    /// @param choice выбранное действие
    /// @param bot    бот для отправки
    void addAnotherRoom(long userId, String choice, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (session.step == OwnerStep.CREATE_MORE_ROOMS && "add".equals(choice)
                && stateMachine.transition(session, OwnerStateMachine.Event.ADD_ROOM)) {
            messaging.send(bot, userId, ASK_ROOM, messaging.backKeyboard());
        } else if (session.step == OwnerStep.CREATE_MORE_ROOMS && "done".equals(choice)
                && stateMachine.transition(session, OwnerStateMachine.Event.FINISH_ROOMS)) {
            messaging.showLeadHours(userId, bot);
        }
    }

    /// Показывает вопрос об обязательности комментария.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    private void askRequirement(long userId, Bot bot) {
        messaging.send(bot, userId, ASK_REQUIRED,
                messaging.choices(messaging.button("Да", "owner:newrequired:yes"),
                        messaging.button("Нет", "owner:newrequired:no"),
                        messaging.button(OwnerMessaging.BACK_TEXT, OwnerMessaging.BACK_CALLBACK)));
    }
}

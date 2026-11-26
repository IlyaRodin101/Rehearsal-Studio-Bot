package ru.urfu.echobot.service.owner;

import ru.urfu.echobot.service.Button;
import ru.urfu.echobot.service.GenericStateMachine;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;

/// Заполняет график и список залов новой базы.
final class OwnerDraftScheduleService {
    /// Последний час суток.
    private static final int LAST_HOUR = 23;
    /// Время закрытия в сутках.
    private static final int DAY_END = 24;
    /// Число дней в неделе.
    private static final int DAYS_IN_WEEK = 7;
    /// Подпись часа.
    private static final String HOUR_SUFFIX = ":00";
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
    private final GenericStateMachine<OwnerState, OwnerEvent> stateMachine;

    /// Создаёт сервис заполнения новой базы.
    ///
    /// @param sessions     сессии владельцев
    /// @param messaging    сообщения и клавиатуры
    /// @param stateMachine автомат состояний
    OwnerDraftScheduleService(OwnerSessions sessions, OwnerMessaging messaging,
                              GenericStateMachine<OwnerState, OwnerEvent> stateMachine) {
        this.sessions = sessions;
        this.messaging = messaging;
        this.stateMachine = stateMachine;
    }

    /// Показывает часы открытия для текущего дня.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void promptOpening(long userId, Bot bot) {
        List<List<Button>> rows = new ArrayList<>();
        for (int hour = 0; hour <= LAST_HOUR; hour++) {
            messaging.addButton(rows, messaging.button(hour + HOUR_SUFFIX, "owner:setopen:" + hour),
                    messaging.buttonsPerRow());
        }
        messaging.addButton(rows, messaging.button("Выходной", "owner:off"), messaging.buttonsPerRow());
        rows.add(messaging.row(messaging.button(OwnerMessaging.BACK_TEXT, OwnerMessaging.BACK_CALLBACK)));
        DayOfWeek day = DayOfWeek.of(sessions.get(userId).day);
        messaging.send(bot, userId, messaging.dayName(day) + ". Час открытия:", rows);
    }

    /// Запоминает час открытия и показывает часы закрытия.
    ///
    /// @param userId идентификатор владельца
    /// @param hour   выбранный час
    /// @param bot    бот для отправки
    void setOpeningHour(long userId, int hour, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (hour < 0 || hour > LAST_HOUR) {
            messaging.send(bot, userId, OwnerMessaging.UNAVAILABLE, messaging.backKeyboard());
        } else if (stateMachine.transition(session, OwnerEvent.ENTER_OPENING)) {
            session.openHour = hour;
            promptClosing(userId, hour, bot);
        }
    }

    /// Запоминает час закрытия и переходит к следующему дню.
    ///
    /// @param userId идентификатор владельца
    /// @param hour   выбранный час
    /// @param bot    бот для отправки
    void setClosingHour(long userId, int hour, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (hour <= session.openHour || hour > DAY_END) {
            messaging.send(bot, userId, OwnerMessaging.UNAVAILABLE, messaging.backKeyboard());
        } else if (stateMachine.transition(session, OwnerEvent.FINISH_DAY)) {
            session.schedule.put(DayOfWeek.of(session.day), new OpeningHours(session.openHour, hour));
            finishDay(userId, bot);
        }
    }

    /// Помечает текущий день выходным.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void closeDay(long userId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (stateMachine.transition(session, OwnerEvent.FINISH_DAY)) {
            session.schedule.remove(DayOfWeek.of(session.day));
            finishDay(userId, bot);
        }
    }

    /// Показывает часы закрытия после выбора часа открытия.
    ///
    /// @param userId  идентификатор владельца
    /// @param openHour час открытия
    /// @param bot     бот для отправки
    private void promptClosing(long userId, int openHour, Bot bot) {
        List<List<Button>> rows = new ArrayList<>();
        for (int hour = openHour + 1; hour <= DAY_END; hour++) {
            messaging.addButton(rows, messaging.button(hour + HOUR_SUFFIX, "owner:setclose:" + hour),
                    messaging.buttonsPerRow());
        }
        rows.add(messaging.row(messaging.button(OwnerMessaging.BACK_TEXT, OwnerMessaging.BACK_CALLBACK)));
        DayOfWeek day = DayOfWeek.of(sessions.get(userId).day);
        messaging.send(bot, userId, messaging.dayName(day) + ". Час закрытия:", rows);
    }

    /// Переходит к следующему дню или к залам новой базы.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    private void finishDay(long userId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        session.day++;
        if (session.day <= DAYS_IN_WEEK) {
            promptOpening(userId, bot);
        } else if (stateMachine.transition(session, OwnerEvent.LAST_DAY)) {
            promptFirstRoom(userId, bot);
        }
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
        } else if (stateMachine.transition(session, OwnerEvent.ENTER_ROOM_NAME)) {
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
        if (session.step == OwnerState.CREATE_ROOM_REQUIRED && List.of(YES, NO).contains(value)
                && stateMachine.transition(session, OwnerEvent.SET_ROOM_REQUIREMENT)) {
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
        if (session.step == OwnerState.CREATE_MORE_ROOMS && "add".equals(choice)
                && stateMachine.transition(session, OwnerEvent.ADD_ROOM)) {
            messaging.send(bot, userId, ASK_ROOM, messaging.backKeyboard());
        } else if (session.step == OwnerState.CREATE_MORE_ROOMS && "done".equals(choice)
                && stateMachine.transition(session, OwnerEvent.FINISH_ROOMS)) {
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

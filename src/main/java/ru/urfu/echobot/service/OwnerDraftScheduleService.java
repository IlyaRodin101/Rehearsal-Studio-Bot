package ru.urfu.echobot.service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.OpeningHours;

/// Заполняет график работы новой базы по дням недели.
final class OwnerDraftScheduleService {
    /// Последний час суток.
    private static final int LAST_HOUR = 23;
    /// Время закрытия в сутках.
    private static final int DAY_END = 24;
    /// Число дней в неделе.
    private static final int DAYS_IN_WEEK = 7;
    /// Подпись часа.
    private static final String HOUR_SUFFIX = ":00";

    /// Сессии владельцев.
    private final OwnerSessions sessions;
    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Автомат состояний.
    private final OwnerStateMachine stateMachine;
    /// Залы новой базы.
    private final OwnerRoomCreationService roomCreation;

    /// Создаёт сервис черновика графика.
    ///
    /// @param sessions     сессии владельцев
    /// @param messaging    сообщения и клавиатуры
    /// @param stateMachine автомат состояний
    /// @param roomCreation залы новой базы
    OwnerDraftScheduleService(OwnerSessions sessions, OwnerMessaging messaging, OwnerStateMachine stateMachine,
                              OwnerRoomCreationService roomCreation) {
        this.sessions = sessions;
        this.messaging = messaging;
        this.stateMachine = stateMachine;
        this.roomCreation = roomCreation;
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
        } else if (stateMachine.transition(session, OwnerStateMachine.Event.ENTER_OPENING)) {
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
        } else if (stateMachine.transition(session, OwnerStateMachine.Event.FINISH_DAY)) {
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
        if (stateMachine.transition(session, OwnerStateMachine.Event.FINISH_DAY)) {
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
        } else if (stateMachine.transition(session, OwnerStateMachine.Event.LAST_DAY)) {
            roomCreation.promptFirstRoom(userId, bot);
        }
    }
}

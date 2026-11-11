package ru.urfu.echobot.service;

import java.time.DayOfWeek;
import java.util.Map;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.OpeningHours;

/// Изменяет график работы базы владельца и черновик графика новой базы.
final class OwnerScheduleEditor {
    /// Время закрытия в сутках.
    private static final int DAY_END = 24;
    /// Час открытия по умолчанию.
    private static final int DEFAULT_OPEN = 9;
    /// Час закрытия по умолчанию.
    private static final int DEFAULT_CLOSE = 24;

    /// Сессии владельцев.
    private final OwnerSessions sessions;
    /// Проверка прав и принадлежности.
    private final OwnerAccess access;
    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Автомат состояний.
    private final OwnerStateMachine stateMachine;
    /// Основные операции с базами.
    private final OwnerBaseService baseService;
    /// Черновик графика новой базы.
    private final OwnerDraftScheduleService draftSchedule;

    /// Создаёт редактор графика.
    ///
    /// @param sessions      сессии владельцев
    /// @param access        проверка прав
    /// @param messaging     сообщения и клавиатуры
    /// @param stateMachine  автомат состояний
    /// @param baseService   основные операции с базами
    /// @param draftSchedule черновик графика новой базы
    OwnerScheduleEditor(OwnerSessions sessions, OwnerAccess access, OwnerMessaging messaging,
                        OwnerStateMachine stateMachine, OwnerBaseService baseService,
                        OwnerDraftScheduleService draftSchedule) {
        this.sessions = sessions;
        this.access = access;
        this.messaging = messaging;
        this.stateMachine = stateMachine;
        this.baseService = baseService;
        this.draftSchedule = draftSchedule;
    }

    /// Помечает день недели выходным.
    ///
    /// @param userId идентификатор владельца
    /// @param day    номер дня от 1 до 7
    /// @param bot    бот для отправки
    /// @return день для отображения или -1, если отображать нечего
    int closeDay(long userId, int day, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (day == 0 && session.step == OwnerStep.CREATE_OPENING) {
            draftSchedule.closeDay(userId, bot);
            return -1;
        }
        Base base = access.selectedBase(userId, session, bot);
        if (base == null || !validDay(day)) {
            return -1;
        }
        Map<DayOfWeek, OpeningHours> schedule = baseService.copySchedule(base);
        schedule.remove(DayOfWeek.of(day));
        updateSchedule(userId, base, schedule);
        return day;
    }

    /// Изменяет час открытия дня недели.
    ///
    /// @param userId идентификатор владельца
    /// @param hour   новый час
    /// @param bot    бот для отправки
    /// @return день для отображения или -1, если отображать нечего
    int setOpeningHour(long userId, int hour, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (session.step == OwnerStep.CREATE_OPENING) {
            draftSchedule.setOpeningHour(userId, hour, bot);
            return -1;
        }
        Base base = access.selectedBase(userId, session, bot);
        int day = session.selectedDay;
        OpeningHours current = base == null || !validDay(day) ? null
                : base.hoursOn(DayOfWeek.of(day)).orElse(new OpeningHours(DEFAULT_OPEN, DEFAULT_CLOSE));
        if (current == null || hour < 0 || hour >= current.closeHour()) {
            messaging.send(bot, userId, OwnerMessaging.UNAVAILABLE, messaging.backKeyboard());
            return -1;
        }
        Map<DayOfWeek, OpeningHours> schedule = baseService.copySchedule(base);
        schedule.put(DayOfWeek.of(day), new OpeningHours(hour, current.closeHour()));
        updateSchedule(userId, base, schedule);
        return day;
    }

    /// Изменяет час закрытия дня недели.
    ///
    /// @param userId идентификатор владельца
    /// @param hour   новый час
    /// @param bot    бот для отправки
    /// @return день для отображения или -1, если отображать нечего
    int setClosingHour(long userId, int hour, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (session.step == OwnerStep.CREATE_CLOSING) {
            draftSchedule.setClosingHour(userId, hour, bot);
            return -1;
        }
        Base base = access.selectedBase(userId, session, bot);
        int day = session.selectedDay;
        OpeningHours current = base == null || !validDay(day) ? null
                : base.hoursOn(DayOfWeek.of(day)).orElse(null);
        if (current == null || hour <= current.openHour() || hour > DAY_END) {
            messaging.send(bot, userId, OwnerMessaging.UNAVAILABLE, messaging.backKeyboard());
            return -1;
        }
        Map<DayOfWeek, OpeningHours> schedule = baseService.copySchedule(base);
        schedule.put(DayOfWeek.of(day), new OpeningHours(current.openHour(), hour));
        updateSchedule(userId, base, schedule);
        return day;
    }

    /// Сохраняет изменённый график базы.
    ///
    /// @param userId   идентификатор владельца
    /// @param base     база
    /// @param schedule новый график
    private void updateSchedule(long userId, Base base, Map<DayOfWeek, OpeningHours> schedule) {
        baseService.updateBase(userId, baseService.copy(base, base.name(), base.address(), base.timezone(),
                base.bookingLeadHours(), base.equipment(), schedule));
    }

    /// Проверяет номер дня недели.
    ///
    /// @param day номер дня
    /// @return true, если номер от 1 до 7
    private boolean validDay(int day) {
        return day >= 1 && day <= 7;
    }
}

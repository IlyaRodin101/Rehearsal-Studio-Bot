package ru.urfu.echobot.service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;

import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.OpeningHours;

/// Показывает и изменяет график работы базы владельца.
final class OwnerScheduleService {
    /// Время закрытия в сутках.
    private static final int DAY_END = 24;
    /// Час открытия по умолчанию.
    private static final int DEFAULT_OPEN = 9;
    /// Час закрытия по умолчанию.
    private static final int DEFAULT_CLOSE = 24;
    /// Час открытия по умолчанию при изменении закрытия.
    private static final int DEFAULT_CLOSE_OPEN = 8;
    /// Подпись часа.
    private static final String HOUR_SUFFIX = ":00";
    /// Кнопки возврата к графику.
    private static final String SCHEDULE = "owner:schedule";
    /// Callback-префикс выбора дня недели.
    private static final String DAY_CALLBACK = "owner:day:";

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
    /// Изменение графика.
    private final OwnerScheduleEditor editor;

    /// Создаёт сервис графика работы.
    ///
    /// @param sessions      сессии владельцев
    /// @param access        проверка прав
    /// @param messaging     сообщения и клавиатуры
    /// @param stateMachine  автомат состояний
    /// @param baseService   основные операции с базами
    /// @param draftSchedule черновик графика новой базы
    OwnerScheduleService(OwnerSessions sessions, OwnerAccess access, OwnerMessaging messaging,
                         OwnerStateMachine stateMachine, OwnerBaseService baseService,
                         OwnerDraftScheduleService draftSchedule) {
        this.sessions = sessions;
        this.access = access;
        this.messaging = messaging;
        this.stateMachine = stateMachine;
        this.baseService = baseService;
        editor = new OwnerScheduleEditor(sessions, access, messaging, stateMachine, baseService, draftSchedule);
    }

    /// Показывает дни недели выбранной базы.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void showSchedule(long userId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        Base base = access.selectedBase(userId, session, bot);
        if (base != null) {
            stateMachine.reset(session);
            List<List<Button>> rows = new ArrayList<>();
            for (DayOfWeek day : DayOfWeek.values()) {
                OpeningHours hours = base.hoursOn(day).orElse(null);
                String label = messaging.dayName(day) + " " + (hours == null ? "выходной" : hours.openHour()
                        + HOUR_SUFFIX + "-" + hours.closeHour() + HOUR_SUFFIX);
                messaging.addButton(rows, messaging.button(label, DAY_CALLBACK + day.getValue()), 2);
            }
            rows.add(messaging.row(messaging.button(OwnerMessaging.BACK_TEXT, "owner:setting")));
            messaging.send(bot, userId, "График работы. Выберите день недели:", rows);
        }
    }

    /// Показывает выбранный день недели.
    ///
    /// @param userId идентификатор владельца
    /// @param day    номер дня от 1 до 7
    /// @param bot    бот для отправки
    void showDay(long userId, int day, Bot bot) {
        OwnerSession session = sessions.get(userId);
        Base base = access.selectedBase(userId, session, bot);
        if (base != null && validDay(day)) {
            session.selectedDay = day;
            stateMachine.reset(session);
            OpeningHours hours = base.hoursOn(DayOfWeek.of(day)).orElse(null);
            List<List<Button>> rows = new ArrayList<>();
            rows.add(messaging.row(messaging.button("Час открытия", "owner:opening:" + day),
                    messaging.button("Час закрытия", "owner:closing:" + day)));
            rows.add(messaging.row(messaging.button("Выходной", "owner:off:" + day)));
            rows.add(messaging.row(messaging.button(OwnerMessaging.BACK_TEXT, SCHEDULE)));
            String text = messaging.dayName(DayOfWeek.of(day)) + (hours == null ? ": выходной. Что изменить?" : ": "
                    + hours.openHour() + HOUR_SUFFIX + "-" + hours.closeHour() + HOUR_SUFFIX + ". Что изменить?");
            messaging.send(bot, userId, text, rows);
        }
    }

    /// Показывает возможные часы открытия.
    ///
    /// @param userId идентификатор владельца
    /// @param day    номер дня от 1 до 7
    /// @param bot    бот для отправки
    void showOpeningHours(long userId, int day, Bot bot) {
        OwnerSession session = sessions.get(userId);
        Base base = access.selectedBase(userId, session, bot);
        if (base != null && validDay(day)) {
            session.selectedDay = day;
            OpeningHours current = base.hoursOn(DayOfWeek.of(day)).orElse(new OpeningHours(DEFAULT_OPEN,
                    DEFAULT_CLOSE));
            List<List<Button>> rows = hourRows(0, current.closeHour() - 1, "owner:setopen:");
            rows.add(messaging.row(messaging.button(OwnerMessaging.BACK_TEXT, DAY_CALLBACK + day)));
            messaging.send(bot, userId, messaging.dayName(DayOfWeek.of(day)) + ". Новый час открытия:", rows);
        }
    }

    /// Показывает возможные часы закрытия.
    ///
    /// @param userId идентификатор владельца
    /// @param day    номер дня от 1 до 7
    /// @param bot    бот для отправки
    void showClosingHours(long userId, int day, Bot bot) {
        OwnerSession session = sessions.get(userId);
        Base base = access.selectedBase(userId, session, bot);
        if (base != null && validDay(day)) {
            session.selectedDay = day;
            OpeningHours current = base.hoursOn(DayOfWeek.of(day)).orElse(new OpeningHours(DEFAULT_CLOSE_OPEN,
                    DEFAULT_CLOSE));
            List<List<Button>> rows = hourRows(current.openHour() + 1, DAY_END, "owner:setclose:");
            rows.add(messaging.row(messaging.button(OwnerMessaging.BACK_TEXT, DAY_CALLBACK + day)));
            messaging.send(bot, userId, messaging.dayName(DayOfWeek.of(day)) + ". Новый час закрытия:", rows);
        }
    }

    /// Помечает день недели выходным.
    ///
    /// @param userId идентификатор владельца
    /// @param day    номер дня от 1 до 7
    /// @param bot    бот для отправки
    void closeDay(long userId, int day, Bot bot) {
        int updatedDay = editor.closeDay(userId, day, bot);
        if (updatedDay > 0) {
            showDay(userId, updatedDay, bot);
        }
    }

    /// Изменяет час открытия дня недели.
    ///
    /// @param userId идентификатор владельца
    /// @param hour   новый час
    /// @param bot    бот для отправки
    void setOpeningHour(long userId, int hour, Bot bot) {
        int updatedDay = editor.setOpeningHour(userId, hour, bot);
        if (updatedDay > 0) {
            showDay(userId, updatedDay, bot);
        }
    }

    /// Изменяет час закрытия дня недели.
    ///
    /// @param userId идентификатор владельца
    /// @param hour   новый час
    /// @param bot    бот для отправки
    void setClosingHour(long userId, int hour, Bot bot) {
        int updatedDay = editor.setClosingHour(userId, hour, bot);
        if (updatedDay > 0) {
            showDay(userId, updatedDay, bot);
        }
    }

    /// Создаёт строки кнопок с часами.
    ///
    /// @param from     первый час
    /// @param to       последний час
    /// @param callback callback-префикс
    /// @return строки кнопок
    private List<List<Button>> hourRows(int from, int to, String callback) {
        List<List<Button>> rows = new ArrayList<>();
        for (int hour = from; hour <= to; hour++) {
            messaging.addButton(rows, messaging.button(hour + HOUR_SUFFIX, callback + hour),
                    messaging.buttonsPerRow());
        }
        return rows;
    }

    /// Проверяет номер дня недели.
    ///
    /// @param day номер дня
    /// @return true, если номер от 1 до 7
    private boolean validDay(int day) {
        return day >= 1 && day <= 7;
    }
}

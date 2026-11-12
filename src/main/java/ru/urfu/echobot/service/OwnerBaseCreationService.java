package ru.urfu.echobot.service;

import java.time.DateTimeException;
import java.time.ZoneId;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Проводит владельца через создание новой базы.
final class OwnerBaseCreationService {
    /// Часовой пояс по умолчанию.
    private static final String DEFAULT_TIMEZONE = "Europe/Moscow";
    /// Текст запроса названия базы.
    private static final String ASK_NAME = "Введите название базы.";
    /// Текст запроса адреса.
    private static final String ASK_ADDRESS = "Введите адрес базы.";
    /// Текст запроса часового пояса.
    private static final String ASK_TIMEZONE = "Введите название часового пояса. Пример: Europe/Moscow.";
    /// Текст запроса оборудования.
    private static final String ASK_EQUIPMENT = "Введите список оборудования (можно оставить пустым).";

    /// Сессии владельцев.
    private final OwnerSessions sessions;
    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Автомат состояний.
    private final OwnerStateMachine stateMachine;
    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Хранилище залов.
    private final RoomRepository roomRepository;
    /// Основные операции с базами.
    private final OwnerBaseService baseService;
    /// Черновик графика новой базы.
    private final OwnerDraftScheduleService draftSchedule;

    /// Создаёт сервис создания баз.
    ///
    /// @param sessions       сессии владельцев
    /// @param messaging      сообщения и клавиатуры
    /// @param stateMachine   автомат состояний
    /// @param baseRepository хранилище баз
    /// @param roomRepository хранилище залов
    /// @param baseService    основные операции с базами
    /// @param draftSchedule  черновик графика
    OwnerBaseCreationService(OwnerSessions sessions, OwnerMessaging messaging, OwnerStateMachine stateMachine,
                             BaseRepository baseRepository, RoomRepository roomRepository,
                             OwnerBaseService baseService, OwnerDraftScheduleService draftSchedule) {
        this.sessions = sessions;
        this.messaging = messaging;
        this.stateMachine = stateMachine;
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.baseService = baseService;
        this.draftSchedule = draftSchedule;
    }

    /// Начинает создание базы.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void beginCreate(long userId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        session.reset();
        if (stateMachine.transition(session, OwnerStateMachine.Event.BEGIN_CREATE)) {
            messaging.send(bot, userId, ASK_NAME, messaging.backKeyboard());
        }
    }

    /// Принимает название новой базы.
    ///
    /// @param userId идентификатор владельца
    /// @param text   введённый текст
    /// @param bot    бот для отправки
    void handleName(long userId, String text, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (text.isBlank()) {
            messaging.send(bot, userId, ASK_NAME, messaging.backKeyboard());
        } else if (stateMachine.transition(session, OwnerStateMachine.Event.ENTER_BASE_NAME)) {
            session.name = text.trim();
            messaging.send(bot, userId, ASK_ADDRESS, messaging.backKeyboard());
        }
    }

    /// Принимает адрес новой базы.
    ///
    /// @param userId идентификатор владельца
    /// @param text   введённый текст
    /// @param bot    бот для отправки
    void handleAddress(long userId, String text, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if (text.isBlank()) {
            messaging.send(bot, userId, ASK_ADDRESS, messaging.backKeyboard());
        } else if (stateMachine.transition(session, OwnerStateMachine.Event.ENTER_BASE_ADDRESS)) {
            session.address = text.trim();
            promptTimezone(userId, bot);
        }
    }

    /// Обрабатывает выбор способа задания часового пояса.
    ///
    /// @param userId идентификатор владельца
    /// @param choice выбранный способ
    /// @param bot    бот для отправки
    void chooseTimezone(long userId, String choice, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if ("default".equals(choice)) {
            session.timezone = DEFAULT_TIMEZONE;
            session.day = 1;
            if (stateMachine.transition(session, OwnerStateMachine.Event.USE_DEFAULT_TIMEZONE)) {
                draftSchedule.promptOpening(userId, bot);
            }
        } else if ("change".equals(choice)
                && stateMachine.transition(session, OwnerStateMachine.Event.ENTER_CUSTOM_TIMEZONE)) {
            messaging.send(bot, userId, ASK_TIMEZONE, messaging.backKeyboard());
        } else {
            baseService.unavailable(userId, bot);
        }
    }

    /// Принимает часовой пояс новой базы.
    ///
    /// @param userId идентификатор владельца
    /// @param text   введённый часовой пояс
    /// @param bot    бот для отправки
    void handleTimezone(long userId, String text, Bot bot) {
        OwnerSession session = sessions.get(userId);
        try {
            String timezone = ZoneId.of(text).getId();
            if (stateMachine.transition(session, OwnerStateMachine.Event.ENTER_TIMEZONE)) {
                session.timezone = timezone;
                session.day = 1;
                draftSchedule.promptOpening(userId, bot);
            }
        } catch (DateTimeException exception) {
            messaging.send(bot, userId, "Неверный формат часового пояса. Пример: Europe/Moscow.",
                    messaging.backKeyboard());
        }
    }

    /// Принимает оборудование и сохраняет новую базу.
    ///
    /// @param userId идентификатор владельца
    /// @param text   введённое оборудование
    /// @param bot    бот для отправки
    void handleEquipment(long userId, String text, Bot bot) {
        OwnerSession session = sessions.get(userId);
        session.equipment = text;
        if (stateMachine.transition(session, OwnerStateMachine.Event.ENTER_EQUIPMENT)) {
            saveNewBase(userId, bot);
        }
    }

    /// Показывает выбор часового пояса после ввода адреса.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    private void promptTimezone(long userId, Bot bot) {
        sessions.get(userId).timezone = DEFAULT_TIMEZONE;
        messaging.send(bot, userId, "Часовой пояс по умолчанию — Europe/Moscow.",
                messaging.choices(messaging.button("Оставить по умолчанию", "owner:timezone:default"),
                        messaging.button("Изменить часовой пояс", "owner:timezone:change")));
    }

    /// Сохраняет базу и её залы.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    private void saveNewBase(long userId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        Base saved = baseRepository.save(new Base(0, userId, session.name, session.address, session.timezone,
                session.leadHours, session.equipment, session.schedule));
        for (Room room : session.newRooms) {
            roomRepository.save(new Room(0, saved.id(), room.name(), room.commentRequired()));
        }
        session.selectedBaseId = saved.id();
        session.resetDraft();
        baseService.showBaseList(userId, bot);
        messaging.send(bot, userId, "База «" + saved.name() + "» создана.",
                baseService.baseListButtons(userId));
    }
}

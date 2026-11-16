package ru.urfu.echobot.service.owner;

import java.util.Map;
import java.util.Set;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.service.GenericStateMachine;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Настраивает базы и залы от имени владельцев.
public final class OwnerManagementService {
    /// Проверка прав владельца.
    private final OwnerAccess access;
    /// Сессии владельцев.
    private final OwnerSessions sessions;
    /// Основные операции с базами.
    private final OwnerBaseService baseService;
    /// Маршрутизатор callback-команд.
    private final OwnerCallbackRouter callbackRouter;
    /// Маршрутизатор текстовых сообщений.
    private final OwnerTextRouter textRouter;

    /// Создаёт сервис управления базами владельцев.
    ///
    /// @param baseRepository хранилище баз
    /// @param roomRepository хранилище залов
    /// @param ownerIds       Telegram ID владельцев
    public OwnerManagementService(BaseRepository baseRepository, RoomRepository roomRepository,
                                  Set<Long> ownerIds) {
        OwnerMessaging messaging = new OwnerMessaging();
        sessions = new OwnerSessions();
        GenericStateMachine<OwnerStep, OwnerSession.Event> stateMachine = createStateMachine();
        access = new OwnerAccess(baseRepository, roomRepository, ownerIds, messaging);
        OwnerRoomCreationService roomCreation = new OwnerRoomCreationService(sessions, messaging, stateMachine);
        OwnerDraftScheduleService draftSchedule = new OwnerDraftScheduleService(sessions, messaging,
                stateMachine, roomCreation);
        OwnerButtons buttons = new OwnerButtons(messaging, baseRepository, access, sessions);
        baseService = new OwnerBaseService(baseRepository, sessions, access, messaging, stateMachine, buttons);
        OwnerRoomService roomService = new OwnerRoomService(sessions, access, messaging, stateMachine,
                roomCreation, buttons);
        OwnerLeadService leadService = new OwnerLeadService(sessions, access, messaging, stateMachine,
                baseService);
        OwnerBaseEditingService editing = new OwnerBaseEditingService(sessions, access, messaging, stateMachine,
                baseService, roomService, leadService);
        OwnerScheduleService schedule = new OwnerScheduleService(sessions, access, messaging, stateMachine,
                baseService, draftSchedule);
        OwnerBaseCreationService creation = new OwnerBaseCreationService(sessions, messaging, stateMachine,
                baseRepository, roomRepository, baseService, draftSchedule);
        OwnerCancellationRulesService cancellationRules = new OwnerCancellationRulesService(sessions, access,
                messaging, baseService);
        callbackRouter = new OwnerCallbackRouter(access, baseService, creation, editing, schedule, roomService,
                roomCreation);
        callbackRouter.addCancellationRules(cancellationRules);
        textRouter = new OwnerTextRouter(access, sessions, stateMachine, creation, editing, roomService,
                roomCreation);
    }

    /// Создаёт автомат переходов настройки баз владельцем.
    ///
    /// @return общий автомат состояний
    private GenericStateMachine<OwnerStep, OwnerSession.Event> createStateMachine() {
        Map<OwnerStep, Map<OwnerSession.Event, OwnerStep>> transitions = Map.ofEntries(
                Map.entry(OwnerStep.IDLE, Map.ofEntries(
                        Map.entry(OwnerSession.Event.BEGIN_CREATE, OwnerStep.CREATE_NAME),
                        Map.entry(OwnerSession.Event.USE_DEFAULT_TIMEZONE, OwnerStep.CREATE_OPENING),
                        Map.entry(OwnerSession.Event.ENTER_CUSTOM_TIMEZONE, OwnerStep.CREATE_TIMEZONE),
                        Map.entry(OwnerSession.Event.EDIT_NAME, OwnerStep.EDIT_NAME),
                        Map.entry(OwnerSession.Event.EDIT_ADDRESS, OwnerStep.EDIT_ADDRESS),
                        Map.entry(OwnerSession.Event.EDIT_TIMEZONE, OwnerStep.EDIT_TIMEZONE),
                        Map.entry(OwnerSession.Event.EDIT_EQUIPMENT, OwnerStep.EDIT_EQUIPMENT),
                        Map.entry(OwnerSession.Event.EDIT_ROOM_NAME, OwnerStep.ROOM_EDIT_NAME),
                        Map.entry(OwnerSession.Event.ADD_ROOM_TO_BASE, OwnerStep.ROOM_CREATE_NAME),
                        Map.entry(OwnerSession.Event.DELETE_ROOM, OwnerStep.DELETE_ROOM),
                        Map.entry(OwnerSession.Event.DELETE_BASE, OwnerStep.DELETE_BASE))),
                Map.entry(OwnerStep.CREATE_NAME, Map.of(
                        OwnerSession.Event.ENTER_BASE_NAME, OwnerStep.CREATE_ADDRESS)),
                Map.entry(OwnerStep.CREATE_ADDRESS, Map.of(
                        OwnerSession.Event.ENTER_BASE_ADDRESS, OwnerStep.IDLE)),
                Map.entry(OwnerStep.CREATE_TIMEZONE, Map.of(
                        OwnerSession.Event.ENTER_TIMEZONE, OwnerStep.CREATE_OPENING)),
                Map.entry(OwnerStep.CREATE_OPENING, Map.of(
                        OwnerSession.Event.ENTER_OPENING, OwnerStep.CREATE_CLOSING,
                        OwnerSession.Event.FINISH_DAY, OwnerStep.CREATE_OPENING,
                        OwnerSession.Event.LAST_DAY, OwnerStep.CREATE_ROOM_NAME)),
                Map.entry(OwnerStep.CREATE_CLOSING, Map.of(
                        OwnerSession.Event.FINISH_DAY, OwnerStep.CREATE_OPENING)),
                Map.entry(OwnerStep.CREATE_ROOM_NAME, Map.of(
                        OwnerSession.Event.ENTER_ROOM_NAME, OwnerStep.CREATE_ROOM_REQUIRED)),
                Map.entry(OwnerStep.CREATE_ROOM_REQUIRED, Map.of(
                        OwnerSession.Event.SET_ROOM_REQUIREMENT, OwnerStep.CREATE_MORE_ROOMS)),
                Map.entry(OwnerStep.CREATE_MORE_ROOMS, Map.of(
                        OwnerSession.Event.ADD_ROOM, OwnerStep.CREATE_ROOM_NAME,
                        OwnerSession.Event.FINISH_ROOMS, OwnerStep.CREATE_LEAD)),
                Map.entry(OwnerStep.CREATE_LEAD, Map.of(
                        OwnerSession.Event.ENTER_LEAD, OwnerStep.CREATE_EQUIPMENT)),
                Map.entry(OwnerStep.CREATE_EQUIPMENT, Map.of(
                        OwnerSession.Event.ENTER_EQUIPMENT, OwnerStep.IDLE)),
                Map.entry(OwnerStep.ROOM_CREATE_NAME, Map.of(
                        OwnerSession.Event.ENTER_ROOM_NAME, OwnerStep.ROOM_CREATE_REQUIRED)),
                Map.entry(OwnerStep.ROOM_CREATE_REQUIRED, Map.of(
                        OwnerSession.Event.SET_ROOM_REQUIREMENT, OwnerStep.IDLE)),
                Map.entry(OwnerStep.EDIT_NAME, Map.of(
                        OwnerSession.Event.SAVE_TEXT, OwnerStep.IDLE)),
                Map.entry(OwnerStep.EDIT_ADDRESS, Map.of(
                        OwnerSession.Event.SAVE_TEXT, OwnerStep.IDLE)),
                Map.entry(OwnerStep.EDIT_TIMEZONE, Map.of(
                        OwnerSession.Event.SAVE_TEXT, OwnerStep.IDLE)),
                Map.entry(OwnerStep.EDIT_EQUIPMENT, Map.of(
                        OwnerSession.Event.SAVE_TEXT, OwnerStep.IDLE)),
                Map.entry(OwnerStep.ROOM_EDIT_NAME, Map.of(
                        OwnerSession.Event.SAVE_TEXT, OwnerStep.IDLE)),
                Map.entry(OwnerStep.DELETE_ROOM, Map.of(
                        OwnerSession.Event.DELETE_ROOM_CONFIRMED, OwnerStep.IDLE)),
                Map.entry(OwnerStep.DELETE_BASE, Map.of(
                        OwnerSession.Event.DELETE_BASE_CONFIRMED, OwnerStep.IDLE)));
        return new GenericStateMachine<>(transitions);
    }

    /// Проверяет, является ли пользователь владельцем базы.
    ///
    /// @param userId идентификатор пользователя
    /// @return true, если пользователь владелец
    public boolean isOwner(long userId) {
        return access.isOwner(userId);
    }

    /// Прерывает незавершённую настройку владельца.
    ///
    /// @param userId идентификатор владельца
    public void cancel(long userId) {
        sessions.cancel(userId);
    }

    /// Показывает список баз владельца.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    public void showBaseList(long userId, Bot bot) {
        baseService.showBaseList(userId, bot);
    }

    /// Обрабатывает callback-команду владельца.
    ///
    /// @param userId идентификатор владельца
    /// @param parts  части callback-команды
    /// @param bot    бот для отправки
    /// @return true, если команда обработана или отклонена
    public boolean handleCallback(long userId, String[] parts, Bot bot) {
        callbackRouter.handle(userId, parts, bot);
        return true;
    }

    /// Обрабатывает текстовое сообщение владельца.
    ///
    /// @param userId идентификатор владельца
    /// @param text   введённый текст
    /// @param bot    бот для отправки
    /// @return true, если текст относился к форме владельца
    public boolean handleText(long userId, String text, Bot bot) {
        return textRouter.handle(userId, text, bot);
    }
}

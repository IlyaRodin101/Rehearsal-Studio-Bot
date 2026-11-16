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
        GenericStateMachine<OwnerSession.Step, OwnerSession.Event> stateMachine = createStateMachine();
        access = new OwnerAccess(baseRepository, roomRepository, ownerIds, messaging);
        OwnerRoomCreationService roomCreation = new OwnerRoomCreationService(sessions, messaging, stateMachine);
        OwnerDraftScheduleService draftSchedule = new OwnerDraftScheduleService(sessions, messaging,
                stateMachine, roomCreation);
        OwnerButtons buttons = new OwnerButtons(messaging, baseRepository, access, sessions);
        baseService = new OwnerBaseService(baseRepository, sessions, access, messaging, stateMachine, buttons);
        OwnerRoomService roomService = new OwnerRoomService(sessions, access, messaging, stateMachine,
                roomCreation, buttons);
        OwnerBaseEditingService editing = new OwnerBaseEditingService(sessions, access, messaging, stateMachine,
                baseService, roomService);
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
    private GenericStateMachine<OwnerSession.Step, OwnerSession.Event> createStateMachine() {
        Map<OwnerSession.Step, Map<OwnerSession.Event, OwnerSession.Step>> transitions = Map.ofEntries(
                Map.entry(OwnerSession.Step.IDLE, Map.ofEntries(
                        Map.entry(OwnerSession.Event.BEGIN_CREATE, OwnerSession.Step.CREATE_NAME),
                        Map.entry(OwnerSession.Event.USE_DEFAULT_TIMEZONE, OwnerSession.Step.CREATE_OPENING),
                        Map.entry(OwnerSession.Event.ENTER_CUSTOM_TIMEZONE, OwnerSession.Step.CREATE_TIMEZONE),
                        Map.entry(OwnerSession.Event.EDIT_NAME, OwnerSession.Step.EDIT_NAME),
                        Map.entry(OwnerSession.Event.EDIT_ADDRESS, OwnerSession.Step.EDIT_ADDRESS),
                        Map.entry(OwnerSession.Event.EDIT_TIMEZONE, OwnerSession.Step.EDIT_TIMEZONE),
                        Map.entry(OwnerSession.Event.EDIT_EQUIPMENT, OwnerSession.Step.EDIT_EQUIPMENT),
                        Map.entry(OwnerSession.Event.EDIT_ROOM_NAME, OwnerSession.Step.ROOM_EDIT_NAME),
                        Map.entry(OwnerSession.Event.ADD_ROOM_TO_BASE, OwnerSession.Step.ROOM_CREATE_NAME),
                        Map.entry(OwnerSession.Event.DELETE_ROOM, OwnerSession.Step.DELETE_ROOM),
                        Map.entry(OwnerSession.Event.DELETE_BASE, OwnerSession.Step.DELETE_BASE))),
                Map.entry(OwnerSession.Step.CREATE_NAME, Map.of(
                        OwnerSession.Event.ENTER_BASE_NAME, OwnerSession.Step.CREATE_ADDRESS)),
                Map.entry(OwnerSession.Step.CREATE_ADDRESS, Map.of(
                        OwnerSession.Event.ENTER_BASE_ADDRESS, OwnerSession.Step.IDLE)),
                Map.entry(OwnerSession.Step.CREATE_TIMEZONE, Map.of(
                        OwnerSession.Event.ENTER_TIMEZONE, OwnerSession.Step.CREATE_OPENING)),
                Map.entry(OwnerSession.Step.CREATE_OPENING, Map.of(
                        OwnerSession.Event.ENTER_OPENING, OwnerSession.Step.CREATE_CLOSING,
                        OwnerSession.Event.FINISH_DAY, OwnerSession.Step.CREATE_OPENING,
                        OwnerSession.Event.LAST_DAY, OwnerSession.Step.CREATE_ROOM_NAME)),
                Map.entry(OwnerSession.Step.CREATE_CLOSING, Map.of(
                        OwnerSession.Event.FINISH_DAY, OwnerSession.Step.CREATE_OPENING)),
                Map.entry(OwnerSession.Step.CREATE_ROOM_NAME, Map.of(
                        OwnerSession.Event.ENTER_ROOM_NAME, OwnerSession.Step.CREATE_ROOM_REQUIRED)),
                Map.entry(OwnerSession.Step.CREATE_ROOM_REQUIRED, Map.of(
                        OwnerSession.Event.SET_ROOM_REQUIREMENT, OwnerSession.Step.CREATE_MORE_ROOMS)),
                Map.entry(OwnerSession.Step.CREATE_MORE_ROOMS, Map.of(
                        OwnerSession.Event.ADD_ROOM, OwnerSession.Step.CREATE_ROOM_NAME,
                        OwnerSession.Event.FINISH_ROOMS, OwnerSession.Step.CREATE_LEAD)),
                Map.entry(OwnerSession.Step.CREATE_LEAD, Map.of(
                        OwnerSession.Event.ENTER_LEAD, OwnerSession.Step.CREATE_EQUIPMENT)),
                Map.entry(OwnerSession.Step.CREATE_EQUIPMENT, Map.of(
                        OwnerSession.Event.ENTER_EQUIPMENT, OwnerSession.Step.IDLE)),
                Map.entry(OwnerSession.Step.ROOM_CREATE_NAME, Map.of(
                        OwnerSession.Event.ENTER_ROOM_NAME, OwnerSession.Step.ROOM_CREATE_REQUIRED)),
                Map.entry(OwnerSession.Step.ROOM_CREATE_REQUIRED, Map.of(
                        OwnerSession.Event.SET_ROOM_REQUIREMENT, OwnerSession.Step.IDLE)),
                Map.entry(OwnerSession.Step.EDIT_NAME, Map.of(
                        OwnerSession.Event.SAVE_TEXT, OwnerSession.Step.IDLE)),
                Map.entry(OwnerSession.Step.EDIT_ADDRESS, Map.of(
                        OwnerSession.Event.SAVE_TEXT, OwnerSession.Step.IDLE)),
                Map.entry(OwnerSession.Step.EDIT_TIMEZONE, Map.of(
                        OwnerSession.Event.SAVE_TEXT, OwnerSession.Step.IDLE)),
                Map.entry(OwnerSession.Step.EDIT_EQUIPMENT, Map.of(
                        OwnerSession.Event.SAVE_TEXT, OwnerSession.Step.IDLE)),
                Map.entry(OwnerSession.Step.ROOM_EDIT_NAME, Map.of(
                        OwnerSession.Event.SAVE_TEXT, OwnerSession.Step.IDLE)),
                Map.entry(OwnerSession.Step.DELETE_ROOM, Map.of(
                        OwnerSession.Event.DELETE_ROOM_CONFIRMED, OwnerSession.Step.IDLE)),
                Map.entry(OwnerSession.Step.DELETE_BASE, Map.of(
                        OwnerSession.Event.DELETE_BASE_CONFIRMED, OwnerSession.Step.IDLE)));
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

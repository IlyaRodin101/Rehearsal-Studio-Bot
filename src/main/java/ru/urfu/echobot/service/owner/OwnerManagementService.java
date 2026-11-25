package ru.urfu.echobot.service.owner;

import java.util.Map;
import java.util.Set;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.service.GenericStateMachine;
import ru.urfu.echobot.service.UserDirectory;
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
    /// Маршрутизатор сообщений владельца.
    private final OwnerRouter router;
    /// Справочник известных пользователей и администраторов.
    private final UserDirectory userDirectory;

    /// Создаёт сервис управления базами владельцев с хранилищем администраторов в памяти.
    ///
    /// @param baseRepository хранилище баз
    /// @param roomRepository хранилище залов
    /// @param ownerIds       Telegram ID владельцев
    public OwnerManagementService(BaseRepository baseRepository, RoomRepository roomRepository,
                                  Set<Long> ownerIds) {
        this(baseRepository, roomRepository, ownerIds, UserDirectory.inMemory());
    }

    /// Создаёт сервис управления базами владельцев.
    ///
    /// @param baseRepository хранилище баз
    /// @param roomRepository хранилище залов
    /// @param ownerIds       Telegram ID владельцев
    /// @param userDirectory  справочник известных пользователей и администраторов
    public OwnerManagementService(BaseRepository baseRepository, RoomRepository roomRepository,
                                  Set<Long> ownerIds, UserDirectory userDirectory) {
        this.userDirectory = userDirectory;
        OwnerMessaging messaging = new OwnerMessaging();
        sessions = new OwnerSessions();
        GenericStateMachine<OwnerState, OwnerEvent> stateMachine = createStateMachine();
        access = new OwnerAccess(baseRepository, roomRepository, ownerIds, messaging);
        OwnerDraftScheduleService draftSchedule = new OwnerDraftScheduleService(sessions, messaging, stateMachine);
        OwnerButtons buttons = new OwnerButtons(messaging, baseRepository, access, sessions);
        baseService = new OwnerBaseService(baseRepository, sessions, access, messaging, stateMachine, buttons);
        OwnerRoomService roomService = new OwnerRoomService(sessions, access, messaging, stateMachine,
                draftSchedule, buttons);
        OwnerBaseEditingService editing = new OwnerBaseEditingService(sessions, access, messaging, stateMachine,
                baseService, roomService);
        OwnerScheduleService schedule = new OwnerScheduleService(sessions, access, messaging, stateMachine,
                baseService, draftSchedule);
        OwnerBaseCreationService creation = new OwnerBaseCreationService(sessions, messaging, stateMachine,
                baseRepository, roomRepository, baseService, draftSchedule);
        OwnerCancellationRulesService cancellationRules = new OwnerCancellationRulesService(sessions, access,
                messaging, baseService);
        OwnerAdministratorService administratorService = new OwnerAdministratorService(userDirectory, sessions,
                access, messaging, stateMachine);
        router = new OwnerRouter(access, baseService, creation, editing, schedule, roomService, draftSchedule);
        router.addAdministrators(administratorService);
        router.addCancellationRules(cancellationRules);
    }

    /// Создаёт автомат переходов настройки баз владельцем.
    ///
    /// @return общий автомат состояний
    private GenericStateMachine<OwnerState, OwnerEvent> createStateMachine() {
        Map<OwnerState, Map<OwnerEvent, OwnerState>> transitions = Map.ofEntries(
                Map.entry(OwnerState.IDLE, Map.ofEntries(
                        Map.entry(OwnerEvent.BEGIN_CREATE, OwnerState.CREATE_NAME),
                        Map.entry(OwnerEvent.USE_DEFAULT_TIMEZONE, OwnerState.CREATE_OPENING),
                        Map.entry(OwnerEvent.ENTER_CUSTOM_TIMEZONE, OwnerState.CREATE_TIMEZONE),
                        Map.entry(OwnerEvent.EDIT_NAME, OwnerState.EDIT_NAME),
                        Map.entry(OwnerEvent.EDIT_ADDRESS, OwnerState.EDIT_ADDRESS),
                        Map.entry(OwnerEvent.EDIT_TIMEZONE, OwnerState.EDIT_TIMEZONE),
                        Map.entry(OwnerEvent.EDIT_EQUIPMENT, OwnerState.EDIT_EQUIPMENT),
                        Map.entry(OwnerEvent.EDIT_ROOM_NAME, OwnerState.ROOM_EDIT_NAME),
                        Map.entry(OwnerEvent.ADD_ROOM_TO_BASE, OwnerState.ROOM_CREATE_NAME),
                        Map.entry(OwnerEvent.DELETE_ROOM, OwnerState.DELETE_ROOM),
                        Map.entry(OwnerEvent.DELETE_BASE, OwnerState.DELETE_BASE),
                        Map.entry(OwnerEvent.ADD_ADMIN, OwnerState.ADMIN_ADD))),
                Map.entry(OwnerState.CREATE_NAME, Map.of(
                        OwnerEvent.ENTER_BASE_NAME, OwnerState.CREATE_ADDRESS)),
                Map.entry(OwnerState.CREATE_ADDRESS, Map.of(
                        OwnerEvent.ENTER_BASE_ADDRESS, OwnerState.IDLE)),
                Map.entry(OwnerState.CREATE_TIMEZONE, Map.of(
                        OwnerEvent.ENTER_TIMEZONE, OwnerState.CREATE_OPENING)),
                Map.entry(OwnerState.CREATE_OPENING, Map.of(
                        OwnerEvent.ENTER_OPENING, OwnerState.CREATE_CLOSING,
                        OwnerEvent.FINISH_DAY, OwnerState.CREATE_OPENING,
                        OwnerEvent.LAST_DAY, OwnerState.CREATE_ROOM_NAME)),
                Map.entry(OwnerState.CREATE_CLOSING, Map.of(
                        OwnerEvent.FINISH_DAY, OwnerState.CREATE_OPENING)),
                Map.entry(OwnerState.CREATE_ROOM_NAME, Map.of(
                        OwnerEvent.ENTER_ROOM_NAME, OwnerState.CREATE_ROOM_REQUIRED)),
                Map.entry(OwnerState.CREATE_ROOM_REQUIRED, Map.of(
                        OwnerEvent.SET_ROOM_REQUIREMENT, OwnerState.CREATE_MORE_ROOMS)),
                Map.entry(OwnerState.CREATE_MORE_ROOMS, Map.of(
                        OwnerEvent.ADD_ROOM, OwnerState.CREATE_ROOM_NAME,
                        OwnerEvent.FINISH_ROOMS, OwnerState.CREATE_LEAD)),
                Map.entry(OwnerState.CREATE_LEAD, Map.of(
                        OwnerEvent.ENTER_LEAD, OwnerState.CREATE_EQUIPMENT)),
                Map.entry(OwnerState.CREATE_EQUIPMENT, Map.of(
                        OwnerEvent.ENTER_EQUIPMENT, OwnerState.IDLE)),
                Map.entry(OwnerState.ROOM_CREATE_NAME, Map.of(
                        OwnerEvent.ENTER_ROOM_NAME, OwnerState.ROOM_CREATE_REQUIRED)),
                Map.entry(OwnerState.ROOM_CREATE_REQUIRED, Map.of(
                        OwnerEvent.SET_ROOM_REQUIREMENT, OwnerState.IDLE)),
                Map.entry(OwnerState.EDIT_NAME, Map.of(
                        OwnerEvent.SAVE_TEXT, OwnerState.IDLE)),
                Map.entry(OwnerState.EDIT_ADDRESS, Map.of(
                        OwnerEvent.SAVE_TEXT, OwnerState.IDLE)),
                Map.entry(OwnerState.EDIT_TIMEZONE, Map.of(
                        OwnerEvent.SAVE_TEXT, OwnerState.IDLE)),
                Map.entry(OwnerState.EDIT_EQUIPMENT, Map.of(
                        OwnerEvent.SAVE_TEXT, OwnerState.IDLE)),
                Map.entry(OwnerState.ROOM_EDIT_NAME, Map.of(
                        OwnerEvent.SAVE_TEXT, OwnerState.IDLE)),
                Map.entry(OwnerState.DELETE_ROOM, Map.of(
                        OwnerEvent.DELETE_ROOM_CONFIRMED, OwnerState.IDLE)),
                Map.entry(OwnerState.DELETE_BASE, Map.of(
                        OwnerEvent.DELETE_BASE_CONFIRMED, OwnerState.IDLE)));
        return new GenericStateMachine<>(transitions);
    }

    /// Проверяет, является ли пользователь владельцем базы.
    ///
    /// @param userId идентификатор пользователя
    /// @return true, если пользователь владелец
    public boolean isOwner(long userId) {
        return access.isOwner(userId);
    }

    /// Проверяет, является ли пользователь администратором.
    ///
    /// @param userId идентификатор пользователя
    /// @return true, если пользователь администратор
    public boolean isAdministrator(long userId) {
        return userDirectory.isAdministrator(userId);
    }

    /// Проверяет, является ли пользователь владельцем или администратором.
    ///
    /// @param userId идентификатор пользователя
    /// @return true, если пользователь владелец или администратор
    public boolean isStaff(long userId) {
        return isOwner(userId) || isAdministrator(userId);
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

    /// Показывает список администраторов.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    public void showAdministrators(long userId, Bot bot) {
        router.showAdministrators(userId, bot);
    }

    /// Обрабатывает callback-команду владельца.
    ///
    /// @param userId идентификатор владельца
    /// @param parts  части callback-команды
    /// @param bot    бот для отправки
    /// @return true, если команда обработана или отклонена
    public boolean handleCallback(long userId, String[] parts, Bot bot) {
        router.handleCallback(userId, parts, bot);
        return true;
    }

    /// Обрабатывает текстовое сообщение владельца.
    ///
    /// @param userId идентификатор владельца
    /// @param text   введённый текст
    /// @param bot    бот для отправки
    /// @return true, если текст относился к форме владельца
    public boolean handleText(long userId, String text, Bot bot) {
        OwnerSession session = sessions.existing(userId);
        return session != null && router.handleText(userId, session, text, bot);
    }
}

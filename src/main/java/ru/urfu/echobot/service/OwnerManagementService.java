package ru.urfu.echobot.service;

import java.util.Set;
import ru.urfu.echobot.bot.Bot;
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
        OwnerStateMachine stateMachine = new OwnerStateMachine();
        access = new OwnerAccess(baseRepository, roomRepository, ownerIds, messaging);
        OwnerRoomCreationService roomCreation = new OwnerRoomCreationService(sessions, messaging, stateMachine);
        OwnerDraftScheduleService draftSchedule = new OwnerDraftScheduleService(sessions, messaging,
                stateMachine, roomCreation);
        baseService = new OwnerBaseService(baseRepository, sessions, access, messaging, stateMachine);
        OwnerRoomService roomService = new OwnerRoomService(sessions, access, messaging, stateMachine,
                roomCreation);
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

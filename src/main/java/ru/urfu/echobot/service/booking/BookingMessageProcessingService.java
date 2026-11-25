package ru.urfu.echobot.service.booking;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import ru.urfu.echobot.service.GenericStateMachine;
import ru.urfu.echobot.service.Message;
import ru.urfu.echobot.service.MessageProcessingService;
import ru.urfu.echobot.service.StaffAccess;
import ru.urfu.echobot.service.UserDirectory;
import ru.urfu.echobot.service.owner.OwnerManagementService;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.repository.InMemorySubscriptionRepository;
import ru.urfu.echobot.repository.RoomRepository;
import ru.urfu.echobot.repository.SubscriptionRepository;

/// Принимает сообщения Telegram, хранит диалоги пользователей и передаёт их контроллерам.
public final class BookingMessageProcessingService implements MessageProcessingService {
    /// Через сколько минут неактивности диалог сбрасывается.
    private static final long SESSION_TIMEOUT_MINUTES = 5;

    /// Состояния диалогов, индексированные по идентификатору пользователя.
    private final Map<Long, BookingStep> flows = new ConcurrentHashMap<>();
    /// Маршрутизатор callback-команд.
    private final BookingCallbackRouter callbackRouter;
    /// Обработчик текстовых сообщений.
    private final BookingTextController textController;
    /// Справочник пользователей, которые писали боту.
    private final UserDirectory userDirectory;

    /// Создаёт обработчик сообщений без владельцев баз.
    ///
    /// @param baseRepository    репозиторий баз
    /// @param roomRepository    репозиторий залов
    /// @param bookingRepository репозиторий бронирований
    /// @param rules             правила бронирования
    public BookingMessageProcessingService(BaseRepository baseRepository, RoomRepository roomRepository,
                                           BookingRepository bookingRepository, BookingRules rules) {
        this(baseRepository, roomRepository, bookingRepository, rules, Set.of(),
                new InMemorySubscriptionRepository(), UserDirectory.inMemory());
    }

    /// Создаёт обработчик сообщений с указанными владельцами баз.
    ///
    /// @param baseRepository    репозиторий баз
    /// @param roomRepository    репозиторий залов
    /// @param bookingRepository репозиторий бронирований
    /// @param rules             правила бронирования
    /// @param ownerIds          Telegram ID владельцев баз
    public BookingMessageProcessingService(BaseRepository baseRepository, RoomRepository roomRepository,
                                           BookingRepository bookingRepository, BookingRules rules,
                                           Set<Long> ownerIds) {
        this(baseRepository, roomRepository, bookingRepository, rules, ownerIds,
                new InMemorySubscriptionRepository(), UserDirectory.inMemory());
    }

    /// Создаёт обработчик сообщений с хранилищем подписок.
    ///
    /// @param baseRepository         репозиторий баз
    /// @param roomRepository         репозиторий залов
    /// @param bookingRepository      репозиторий бронирований
    /// @param rules                  правила бронирования
    /// @param ownerIds               Telegram ID владельцев баз
    /// @param subscriptionRepository хранилище подписок
    public BookingMessageProcessingService(BaseRepository baseRepository, RoomRepository roomRepository,
                                           BookingRepository bookingRepository, BookingRules rules,
                                           Set<Long> ownerIds, SubscriptionRepository subscriptionRepository) {
        this(baseRepository, roomRepository, bookingRepository, rules, ownerIds, subscriptionRepository,
                UserDirectory.inMemory());
    }

    /// Создаёт обработчик сообщений с постоянным справочником пользователей.
    ///
    /// @param baseRepository         репозиторий баз
    /// @param roomRepository         репозиторий залов
    /// @param bookingRepository      репозиторий бронирований
    /// @param rules                  правила бронирования
    /// @param ownerIds               Telegram ID владельцев баз
    /// @param subscriptionRepository хранилище подписок
    /// @param userDirectory          справочник известных пользователей и администраторов
    public BookingMessageProcessingService(BaseRepository baseRepository, RoomRepository roomRepository,
                                           BookingRepository bookingRepository, BookingRules rules,
                                           Set<Long> ownerIds, SubscriptionRepository subscriptionRepository,
                                           UserDirectory userDirectory) {
        this.userDirectory = userDirectory;
        StaffAccess staffAccess = new StaffAccess(ownerIds, userDirectory);
        BookingAvailabilityService availabilityService = new BookingAvailabilityService(roomRepository,
                bookingRepository, rules);
        GenericStateMachine<BookingStep.Step, BookingStep.Event> stateMachine = createStateMachine();
        BookingBaseSelectionService baseSelection = new BookingBaseSelectionService(baseRepository, roomRepository);
        BookingDateSelectionService dateSelection = new BookingDateSelectionService(baseRepository,
                availabilityService, rules, staffAccess);
        BookingTimeSelectionService timeSelection = new BookingTimeSelectionService(baseRepository,
                roomRepository, bookingRepository, availabilityService, staffAccess);
        BookingConfirmationService confirmation = new BookingConfirmationService(baseRepository,
                roomRepository, stateMachine, rules);
        BookingFormService formService = new BookingFormService(baseRepository, roomRepository,
                bookingRepository, rules, stateMachine, confirmation, staffAccess);
        BookingLookup lookup = new BookingLookup(baseRepository, roomRepository, bookingRepository, rules);
        HotSlotService hotSlotService = new HotSlotService(roomRepository, bookingRepository, rules);
        HotSlotNotifier notifier = new SubscriptionHotSlotNotifier(subscriptionRepository, rules);
        BookingCancellationService cancellationService = new BookingCancellationService(lookup,
                bookingRepository, rules, notifier);
        BaseInformationService baseInformation = new BaseInformationService(baseRepository);
        OwnerManagementService ownerService = new OwnerManagementService(baseRepository, roomRepository,
                ownerIds, userDirectory);
        BookingMenuController menuController = new BookingMenuController(ownerService, stateMachine,
                baseSelection, dateSelection, timeSelection, formService, confirmation);
        BookingNavigationController navigation = new BookingNavigationController(baseSelection, dateSelection,
                timeSelection, formService, menuController, stateMachine, confirmation);
        BookingClientController clientController = new BookingClientController(lookup, cancellationService);
        BookingKeyboardFactory keyboards = new BookingKeyboardFactory();
        HotSlotController hotSlotController = new HotSlotController(baseRepository, roomRepository,
                hotSlotService, timeSelection, menuController, keyboards, rules);
        SubscriptionService subscriptionService = new SubscriptionService(subscriptionRepository);
        SubscriptionController subscriptionController = new SubscriptionController(baseRepository,
                subscriptionService, menuController, keyboards);
        callbackRouter = new BookingCallbackRouter(ownerService, menuController, navigation,
                clientController, baseInformation);
        callbackRouter.registerHotSlots(hotSlotController, subscriptionController);
        textController = new BookingTextController(ownerService, menuController, formService, confirmation);
    }

    /// Создаёт автомат переходов диалога бронирования.
    ///
    /// @return общий автомат состояний
    private GenericStateMachine<BookingStep.Step, BookingStep.Event> createStateMachine() {
        Map<BookingStep.Step, Map<BookingStep.Event, BookingStep.Step>> transitions = Map.ofEntries(
                Map.entry(BookingStep.Step.MENU, Map.of(
                        BookingStep.Event.BEGIN_BOOKING, BookingStep.Step.BOOK_BASES)),
                Map.entry(BookingStep.Step.BOOK_BASES, Map.of(
                        BookingStep.Event.SELECT_BASE, BookingStep.Step.BOOK_ROOMS,
                        BookingStep.Event.BACK, BookingStep.Step.MENU)),
                Map.entry(BookingStep.Step.BOOK_ROOMS, Map.of(
                        BookingStep.Event.SELECT_ROOM, BookingStep.Step.BOOK_MONTH,
                        BookingStep.Event.BACK, BookingStep.Step.BOOK_BASES)),
                Map.entry(BookingStep.Step.BOOK_MONTH, Map.of(
                        BookingStep.Event.SELECT_MONTH, BookingStep.Step.BOOK_WEEK,
                        BookingStep.Event.CHANGE_MONTH_PAGE, BookingStep.Step.BOOK_MONTH,
                        BookingStep.Event.SELECT_DAY, BookingStep.Step.BOOK_TIME,
                        BookingStep.Event.BACK, BookingStep.Step.BOOK_ROOMS)),
                Map.entry(BookingStep.Step.BOOK_WEEK, Map.of(
                        BookingStep.Event.SELECT_WEEK, BookingStep.Step.BOOK_DAY,
                        BookingStep.Event.BACK, BookingStep.Step.BOOK_MONTH)),
                Map.entry(BookingStep.Step.BOOK_DAY, Map.of(
                        BookingStep.Event.SELECT_DAY, BookingStep.Step.BOOK_TIME,
                        BookingStep.Event.BACK, BookingStep.Step.BOOK_WEEK)),
                Map.entry(BookingStep.Step.BOOK_TIME, Map.of(
                        BookingStep.Event.SELECT_TIME, BookingStep.Step.BOOK_DURATION,
                        BookingStep.Event.BACK, BookingStep.Step.BOOK_DAY)),
                Map.entry(BookingStep.Step.BOOK_DURATION, Map.of(
                        BookingStep.Event.SELECT_DURATION, BookingStep.Step.BOOK_NAME,
                        BookingStep.Event.BACK, BookingStep.Step.BOOK_TIME)),
                Map.entry(BookingStep.Step.BOOK_NAME, Map.of(
                        BookingStep.Event.ENTER_NAME, BookingStep.Step.BOOK_PHONE,
                        BookingStep.Event.BACK, BookingStep.Step.BOOK_DURATION)),
                Map.entry(BookingStep.Step.BOOK_PHONE, Map.of(
                        BookingStep.Event.VALID_PHONE, BookingStep.Step.BOOK_COMMENT,
                        BookingStep.Event.BACK, BookingStep.Step.BOOK_NAME)),
                Map.entry(BookingStep.Step.BOOK_COMMENT, Map.of(
                        BookingStep.Event.ENTER_COMMENT, BookingStep.Step.BOOK_CONFIRM,
                        BookingStep.Event.SKIP_COMMENT, BookingStep.Step.BOOK_CONFIRM,
                        BookingStep.Event.BACK, BookingStep.Step.BOOK_NAME)),
                Map.entry(BookingStep.Step.BOOK_CONFIRM, Map.of(
                        BookingStep.Event.CONFIRM_BOOKING, BookingStep.Step.MENU,
                        BookingStep.Event.CANCEL, BookingStep.Step.MENU,
                        BookingStep.Event.BACK, BookingStep.Step.BOOK_COMMENT)));
        return new GenericStateMachine<>(transitions);
    }

    /// Обрабатывает входящее сообщение или callback.
    ///
    /// @param msg    входящее сообщение
    /// @param chatId идентификатор чата пользователя
    /// @param bot    бот для отправки ответов
    @Override
    public void processMessage(@NotNull Message msg, long chatId, @NotNull Bot bot) {
        if (msg.username() != null) {
            userDirectory.remember(chatId, msg.username());
        }
        removeExpiredFlows();
        BookingStep flow = flow(chatId);
        if (msg.buttonData() != null) {
            callbackRouter.handle(msg.buttonData(), chatId, flow, bot);
        } else if (msg.text() != null) {
            textController.handle(msg.text(), chatId, flow, bot);
        }
    }

    /// Возвращает диалог пользователя, создавая его при первом обращении.
    ///
    /// @param userId идентификатор пользователя
    /// @return состояние диалога
    private BookingStep flow(long userId) {
        BookingStep flow = flows.computeIfAbsent(userId, ignored -> new BookingStep());
        flow.updateLastActivity();
        return flow;
    }

    /// Удаляет диалоги, в которых не было активности дольше тайм-аута.
    private void removeExpiredFlows() {
        Iterator<Map.Entry<Long, BookingStep>> iterator = flows.entrySet().iterator();
        LocalDateTime currentTime = LocalDateTime.now();
        while (iterator.hasNext()) {
            BookingStep flow = iterator.next().getValue();
            long inactiveMinutes = ChronoUnit.MINUTES.between(flow.getLastActivityAt(), currentTime);
            if (inactiveMinutes >= SESSION_TIMEOUT_MINUTES) {
                iterator.remove();
            }
        }
    }
}

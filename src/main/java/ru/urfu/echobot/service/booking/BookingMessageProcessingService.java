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
        GenericStateMachine<BookingState, BookingEvent> stateMachine = createStateMachine();
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
    private GenericStateMachine<BookingState, BookingEvent> createStateMachine() {
        Map<BookingState, Map<BookingEvent, BookingState>> transitions = Map.ofEntries(
                Map.entry(BookingState.MENU, Map.of(
                        BookingEvent.BEGIN_BOOKING, BookingState.BOOK_BASES)),
                Map.entry(BookingState.BOOK_BASES, Map.of(
                        BookingEvent.SELECT_BASE, BookingState.BOOK_ROOMS,
                        BookingEvent.BACK, BookingState.MENU)),
                Map.entry(BookingState.BOOK_ROOMS, Map.of(
                        BookingEvent.SELECT_ROOM, BookingState.BOOK_MONTH,
                        BookingEvent.BACK, BookingState.BOOK_BASES)),
                Map.entry(BookingState.BOOK_MONTH, Map.of(
                        BookingEvent.SELECT_MONTH, BookingState.BOOK_WEEK,
                        BookingEvent.CHANGE_MONTH_PAGE, BookingState.BOOK_MONTH,
                        BookingEvent.SELECT_DAY, BookingState.BOOK_TIME,
                        BookingEvent.BACK, BookingState.BOOK_ROOMS)),
                Map.entry(BookingState.BOOK_WEEK, Map.of(
                        BookingEvent.SELECT_WEEK, BookingState.BOOK_DAY,
                        BookingEvent.BACK, BookingState.BOOK_MONTH)),
                Map.entry(BookingState.BOOK_DAY, Map.of(
                        BookingEvent.SELECT_DAY, BookingState.BOOK_TIME,
                        BookingEvent.BACK, BookingState.BOOK_WEEK)),
                Map.entry(BookingState.BOOK_TIME, Map.of(
                        BookingEvent.SELECT_TIME, BookingState.BOOK_DURATION,
                        BookingEvent.BACK, BookingState.BOOK_DAY)),
                Map.entry(BookingState.BOOK_DURATION, Map.of(
                        BookingEvent.SELECT_DURATION, BookingState.BOOK_NAME,
                        BookingEvent.BACK, BookingState.BOOK_TIME)),
                Map.entry(BookingState.BOOK_NAME, Map.of(
                        BookingEvent.ENTER_NAME, BookingState.BOOK_PHONE,
                        BookingEvent.BACK, BookingState.BOOK_DURATION)),
                Map.entry(BookingState.BOOK_PHONE, Map.of(
                        BookingEvent.VALID_PHONE, BookingState.BOOK_COMMENT,
                        BookingEvent.BACK, BookingState.BOOK_NAME)),
                Map.entry(BookingState.BOOK_COMMENT, Map.of(
                        BookingEvent.ENTER_COMMENT, BookingState.BOOK_CONFIRM,
                        BookingEvent.SKIP_COMMENT, BookingState.BOOK_CONFIRM,
                        BookingEvent.BACK, BookingState.BOOK_NAME)),
                Map.entry(BookingState.BOOK_CONFIRM, Map.of(
                        BookingEvent.CONFIRM_BOOKING, BookingState.MENU,
                        BookingEvent.CANCEL, BookingState.MENU,
                        BookingEvent.BACK, BookingState.BOOK_COMMENT)));
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

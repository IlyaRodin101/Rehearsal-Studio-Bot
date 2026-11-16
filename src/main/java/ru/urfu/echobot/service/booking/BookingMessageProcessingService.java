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
import ru.urfu.echobot.service.owner.OwnerManagementService;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Принимает сообщения Telegram, хранит диалоги пользователей и передаёт их контроллерам.
public final class BookingMessageProcessingService implements MessageProcessingService {
    /// Через сколько минут неактивности диалог сбрасывается.
    private static final long SESSION_TIMEOUT_MINUTES = 5;

    /// Состояния диалогов, индексированные по идентификатору пользователя.
    private final Map<Long, BookingFlow> flows = new ConcurrentHashMap<>();
    /// Маршрутизатор callback-команд.
    private final BookingCallbackRouter callbackRouter;
    /// Обработчик текстовых сообщений.
    private final BookingTextController textController;

    /// Создаёт обработчик сообщений без владельцев баз.
    ///
    /// @param baseRepository    репозиторий баз
    /// @param roomRepository    репозиторий залов
    /// @param bookingRepository репозиторий бронирований
    /// @param rules             правила бронирования
    public BookingMessageProcessingService(BaseRepository baseRepository, RoomRepository roomRepository,
                                           BookingRepository bookingRepository, BookingRules rules) {
        this(baseRepository, roomRepository, bookingRepository, rules, Set.of());
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
        BookingAvailabilityService availabilityService = new BookingAvailabilityService(roomRepository,
                bookingRepository, rules);
        GenericStateMachine<BookingFlow.Step, BookingFlow.Event> stateMachine = createStateMachine();
        BookingBaseSelectionService baseSelection = new BookingBaseSelectionService(baseRepository, roomRepository);
        BookingDateSelectionService dateSelection = new BookingDateSelectionService(baseRepository,
                availabilityService, rules);
        BookingTimeSelectionService timeSelection = new BookingTimeSelectionService(baseRepository,
                roomRepository, bookingRepository, availabilityService);
        BookingConfirmationService confirmation = new BookingConfirmationService(baseRepository,
                roomRepository, stateMachine, rules);
        BookingFormService formService = new BookingFormService(baseRepository, roomRepository,
                bookingRepository, rules, stateMachine, confirmation);
        BookingLookup lookup = new BookingLookup(baseRepository, roomRepository, bookingRepository, rules);
        BookingInformationService informationService = new BookingInformationService(lookup);
        BookingCancellationService cancellationService = new BookingCancellationService(lookup,
                informationService, bookingRepository, rules);
        BaseInformationService baseInformation = new BaseInformationService(baseRepository);
        OwnerManagementService ownerService = new OwnerManagementService(baseRepository, roomRepository, ownerIds);
        BookingMenuController menuController = new BookingMenuController(ownerService, stateMachine);
        BookingNavigationController navigation = new BookingNavigationController(baseSelection, dateSelection,
                formService, menuController, stateMachine, confirmation);
        BookingSelectionController selection = new BookingSelectionController(dateSelection, timeSelection,
                formService, menuController, stateMachine);
        BookingClientController clientController = new BookingClientController(informationService,
                cancellationService);
        BookingBackController backController = new BookingBackController(stateMachine, menuController,
                baseSelection, dateSelection, timeSelection, formService, confirmation);
        callbackRouter = new BookingCallbackRouter(ownerService, menuController, navigation, selection,
                clientController, backController, baseInformation);
        textController = new BookingTextController(ownerService, menuController, backController, formService,
                confirmation);
    }

    /// Создаёт автомат переходов диалога бронирования.
    ///
    /// @return общий автомат состояний
    private GenericStateMachine<BookingFlow.Step, BookingFlow.Event> createStateMachine() {
        Map<BookingFlow.Step, Map<BookingFlow.Event, BookingFlow.Step>> transitions = Map.ofEntries(
                Map.entry(BookingFlow.Step.MENU, Map.of(
                        BookingFlow.Event.BEGIN_BOOKING, BookingFlow.Step.BOOK_BASES)),
                Map.entry(BookingFlow.Step.BOOK_BASES, Map.of(
                        BookingFlow.Event.SELECT_BASE, BookingFlow.Step.BOOK_ROOMS,
                        BookingFlow.Event.BACK, BookingFlow.Step.MENU)),
                Map.entry(BookingFlow.Step.BOOK_ROOMS, Map.of(
                        BookingFlow.Event.SELECT_ROOM, BookingFlow.Step.BOOK_MONTH,
                        BookingFlow.Event.BACK, BookingFlow.Step.BOOK_BASES)),
                Map.entry(BookingFlow.Step.BOOK_MONTH, Map.of(
                        BookingFlow.Event.SELECT_MONTH, BookingFlow.Step.BOOK_WEEK,
                        BookingFlow.Event.CHANGE_MONTH_PAGE, BookingFlow.Step.BOOK_MONTH,
                        BookingFlow.Event.SELECT_DAY, BookingFlow.Step.BOOK_TIME,
                        BookingFlow.Event.BACK, BookingFlow.Step.BOOK_ROOMS)),
                Map.entry(BookingFlow.Step.BOOK_WEEK, Map.of(
                        BookingFlow.Event.SELECT_WEEK, BookingFlow.Step.BOOK_DAY,
                        BookingFlow.Event.BACK, BookingFlow.Step.BOOK_MONTH)),
                Map.entry(BookingFlow.Step.BOOK_DAY, Map.of(
                        BookingFlow.Event.SELECT_DAY, BookingFlow.Step.BOOK_TIME,
                        BookingFlow.Event.BACK, BookingFlow.Step.BOOK_WEEK)),
                Map.entry(BookingFlow.Step.BOOK_TIME, Map.of(
                        BookingFlow.Event.SELECT_TIME, BookingFlow.Step.BOOK_DURATION,
                        BookingFlow.Event.BACK, BookingFlow.Step.BOOK_DAY)),
                Map.entry(BookingFlow.Step.BOOK_DURATION, Map.of(
                        BookingFlow.Event.SELECT_DURATION, BookingFlow.Step.BOOK_NAME,
                        BookingFlow.Event.BACK, BookingFlow.Step.BOOK_TIME)),
                Map.entry(BookingFlow.Step.BOOK_NAME, Map.of(
                        BookingFlow.Event.ENTER_NAME, BookingFlow.Step.BOOK_PHONE,
                        BookingFlow.Event.BACK, BookingFlow.Step.BOOK_DURATION)),
                Map.entry(BookingFlow.Step.BOOK_PHONE, Map.of(
                        BookingFlow.Event.VALID_PHONE, BookingFlow.Step.BOOK_COMMENT,
                        BookingFlow.Event.BACK, BookingFlow.Step.BOOK_NAME)),
                Map.entry(BookingFlow.Step.BOOK_COMMENT, Map.of(
                        BookingFlow.Event.ENTER_COMMENT, BookingFlow.Step.BOOK_CONFIRM,
                        BookingFlow.Event.SKIP_COMMENT, BookingFlow.Step.BOOK_CONFIRM,
                        BookingFlow.Event.BACK, BookingFlow.Step.BOOK_NAME)),
                Map.entry(BookingFlow.Step.BOOK_CONFIRM, Map.of(
                        BookingFlow.Event.CONFIRM_BOOKING, BookingFlow.Step.MENU,
                        BookingFlow.Event.CANCEL, BookingFlow.Step.MENU,
                        BookingFlow.Event.BACK, BookingFlow.Step.BOOK_COMMENT)));
        return new GenericStateMachine<>(transitions);
    }

    /// Обрабатывает входящее сообщение или callback.
    ///
    /// @param msg    входящее сообщение
    /// @param chatId идентификатор чата пользователя
    /// @param bot    бот для отправки ответов
    @Override
    public void processMessage(@NotNull Message msg, long chatId, @NotNull Bot bot) {
        removeExpiredFlows();
        BookingFlow flow = flow(chatId);
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
    private BookingFlow flow(long userId) {
        BookingFlow flow = flows.computeIfAbsent(userId, ignored -> new BookingFlow());
        flow.updateLastActivity();
        return flow;
    }

    /// Удаляет диалоги, в которых не было активности дольше тайм-аута.
    private void removeExpiredFlows() {
        Iterator<Map.Entry<Long, BookingFlow>> iterator = flows.entrySet().iterator();
        LocalDateTime currentTime = LocalDateTime.now();
        while (iterator.hasNext()) {
            BookingFlow flow = iterator.next().getValue();
            long inactiveMinutes = ChronoUnit.MINUTES.between(flow.getLastActivityAt(), currentTime);
            if (inactiveMinutes >= SESSION_TIMEOUT_MINUTES) {
                iterator.remove();
            }
        }
    }
}

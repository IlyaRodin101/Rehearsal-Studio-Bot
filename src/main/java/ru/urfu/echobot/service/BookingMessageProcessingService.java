package ru.urfu.echobot.service;

import java.util.Set;
import org.jetbrains.annotations.NotNull;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.repository.InMemorySubscriptionRepository;
import ru.urfu.echobot.repository.RoomRepository;
import ru.urfu.echobot.repository.SubscriptionRepository;

/// Принимает сообщения Telegram и передаёт их контроллерам диалогов.
public final class BookingMessageProcessingService implements MessageProcessingService {
    /// Диалоги всех пользователей.
    private final BookingSessionRegistry sessions;
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
        this(baseRepository, roomRepository, bookingRepository, rules, ownerIds,
                new InMemorySubscriptionRepository());
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
        BookingAvailabilityService availabilityService = new BookingAvailabilityService(roomRepository,
                bookingRepository, rules);
        RussianDateFormatter formatter = new RussianDateFormatter();
        BookingKeyboardFactory keyboards = new BookingKeyboardFactory();
        BookingStateMachine stateMachine = new BookingStateMachine();
        BookingBaseSelectionService baseSelection = new BookingBaseSelectionService(baseRepository,
                roomRepository, keyboards);
        BookingDateSelectionService dateSelection = new BookingDateSelectionService(baseRepository,
                availabilityService, rules, keyboards, formatter);
        BookingTimeSelectionService timeSelection = new BookingTimeSelectionService(baseRepository,
                roomRepository, bookingRepository, availabilityService, keyboards, formatter);
        BookingConfirmationService confirmation = new BookingConfirmationService(baseRepository,
                roomRepository, stateMachine);
        BookingFormService formService = new BookingFormService(baseRepository, roomRepository,
                bookingRepository, rules, stateMachine, confirmation);
        BookingLookup lookup = new BookingLookup(baseRepository, roomRepository, bookingRepository, rules);
        BookingInformationService informationService = new BookingInformationService(lookup, formatter);
        HotSlotService hotSlotService = new HotSlotService(roomRepository, bookingRepository, rules);
        SubscriptionHotSlotNotifier notifier = new SubscriptionHotSlotNotifier(subscriptionRepository, rules);
        BookingCancellationService cancellationService = new BookingCancellationService(lookup,
                informationService, bookingRepository, formatter, rules, notifier);
        BaseInformationService baseInformation = new BaseInformationService(baseRepository, formatter, keyboards);
        OwnerManagementService ownerService = new OwnerManagementService(baseRepository, roomRepository, ownerIds);
        BookingMenuController menuController = new BookingMenuController(ownerService, keyboards, stateMachine);
        BookingNavigationController navigation = new BookingNavigationController(baseSelection, dateSelection,
                formService, menuController, stateMachine, confirmation, keyboards);
        BookingSelectionController selection = new BookingSelectionController(dateSelection, timeSelection,
                formService, menuController, stateMachine);
        BookingClientController clientController = new BookingClientController(informationService,
                cancellationService, keyboards);
        BookingBackController backController = new BookingBackController(stateMachine, menuController,
                baseSelection, dateSelection, timeSelection, formService, confirmation);
        HotSlotController hotSlotController = new HotSlotController(baseRepository, roomRepository,
                hotSlotService, timeSelection, menuController, keyboards, rules);
        SubscriptionService subscriptionService = new SubscriptionService(subscriptionRepository);
        SubscriptionController subscriptionController = new SubscriptionController(baseRepository,
                subscriptionService, menuController, keyboards);
        sessions = new BookingSessionRegistry();
        callbackRouter = new BookingCallbackRouter(ownerService, menuController, navigation, selection,
                clientController, backController, baseInformation);
        callbackRouter.registerHotSlots(hotSlotController, subscriptionController);
        textController = new BookingTextController(ownerService, menuController, backController, formService,
                confirmation);
    }

    /// Обрабатывает входящее сообщение или callback.
    ///
    /// @param msg    входящее сообщение
    /// @param chatId идентификатор чата пользователя
    /// @param bot    бот для отправки ответов
    @Override
    public void processMessage(@NotNull Message msg, long chatId, @NotNull Bot bot) {
        sessions.removeExpired();
        BookingFlow flow = sessions.flow(chatId);
        if (msg.buttonData() != null) {
            callbackRouter.handle(msg.buttonData(), chatId, flow, bot);
        } else if (msg.text() != null) {
            textController.handle(msg.text(), chatId, flow, bot);
        }
    }
}

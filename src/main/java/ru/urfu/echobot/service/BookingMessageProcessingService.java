package ru.urfu.echobot.service;

import java.util.Set;
import org.jetbrains.annotations.NotNull;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.repository.RoomRepository;

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
                roomRepository, stateMachine, rules);
        BookingFormService formService = new BookingFormService(baseRepository, roomRepository,
                bookingRepository, rules, stateMachine, confirmation);
        BookingLookup lookup = new BookingLookup(baseRepository, roomRepository, bookingRepository, rules);
        BookingInformationService informationService = new BookingInformationService(lookup, formatter);
        BookingCancellationService cancellationService = new BookingCancellationService(lookup,
                informationService, bookingRepository, formatter, rules);
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
        sessions = new BookingSessionRegistry();
        callbackRouter = new BookingCallbackRouter(ownerService, menuController, navigation, selection,
                clientController, backController, baseInformation);
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

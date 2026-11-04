package ru.urfu.echobot.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Обрабатывает сообщения пользователей и проводит их через сценарии бронирования и просмотра информации.
public final class BookingMessageProcessingService implements MessageProcessingService {
    /// Number of lock stripes used to serialize updates by Telegram user ID.
    private static final int USER_LOCK_COUNT = 64;
    /// Подпись кнопки возврата на предыдущий шаг.
    private static final String BACK = "Назад";
    /// Код действия возврата в callback-данных.
    private static final String CALLBACK_BACK = "back";
    /// Код перехода к бронированию.
    private static final String CALLBACK_BOOK = "book";
    /// Код перехода к информации о базах.
    private static final String CALLBACK_ABOUT = "about";
    /// Код выбора базы в callback-данных.
    private static final String CALLBACK_BASE = "base";
    /// Callback для открытия сценария бронирования из меню.
    private static final String MENU_BOOK = "menu:book";
    /// Callback для возврата в главное меню.
    private static final String MENU_HOME = "menu:home";
    /// Command to return the conversation to the main menu.
    private static final String COMMAND_MENU = "/menu";
    /// Command that starts a conversation.
    private static final String COMMAND_START = "/start";
    /// Индекс часа начала в частях callback-данных.
    private static final int START_HOUR_PART = 3;
    /// строковые константы, чтобы не ругался checkstyle
    private final String room = "room";
    private final String months = "months";
    private final String month = "month";
    private final String week = "week";
    private final String day = "day";
    private final String time = "time";
    private final String hours = "hours";
    private final String skip = "skip";
    private final String confirm = "confirm";
    private final String cancel = "cancel";
    private final String list = "list";
    /// целочисленные константы, чтобы не ругался checkstyle
    /// Минимальное количество частей callback с идентификатором
    private final int callbackPartsWithId = 3;
    /// Минимальное количество частей callback для выбора времени
    private final int callbackPartsForTime = 4;
    /// Минимальное количество частей callback без дополнительного аргумента.
    private final int callbackPartsWithoutArgument = 2;
    /// Собирает кнопки и клавиатуры для сообщений.
    private final BookingKeyboardFactory keyboards = new BookingKeyboardFactory();
    /// Обрабатывает кнопочные этапы выбора слота бронирования.
    private final BookingSelectionService selectionService;
    /// Формирует сведения о бронированиях и репетиционных базах.
    private final BookingInformationService informationService;
    /// Проверяет и обрабатывает данные клиентской формы бронирования.
    private final BookingFormService formService;
    /// Управляет базами и залами от имени владельцев.
    private final OwnerManagementService ownerManagementService;
    /// States for active conversations, indexed by Telegram user ID.
    private final Map<Long, BookingFlow> flows = new ConcurrentHashMap<Long, BookingFlow>();
    /// Locks serialize concurrent updates for one user while allowing other users to proceed.
    private final Object[] userLocks = new Object[USER_LOCK_COUNT];
    /// Обработчики верхнего уровня callback-команд, индексированные по пространству имён.
    private final Map<String, Consumer<CallbackContext>> callbackHandlers;
    /// Обработчики команд главного меню.
    private final Map<String, Consumer<CallbackContext>> menuActionHandlers;
    /// Обработчики подкоманд бронирования.
    private final Map<String, Consumer<CallbackContext>> bookingActionHandlers;
    /// Минимальная длина callback-массивов для команд бронирования.
    private final Map<String, Integer> bookingCallbackPartCounts;
    /// Обработчики действий раздела «О базе».
    private final Map<String, Consumer<CallbackContext>> aboutActionHandlers;
    /// Минимальная длина callback-массивов для действий раздела «О базе».
    private final Map<String, Integer> aboutCallbackPartCounts;
    /// Обработчики текстовых команд, доступных на любом шаге диалога.
    private final Map<String, Consumer<TextContext>> textCommandHandlers;
    /// Обработчики текстового ввода на шагах диалога.
    private final Map<BookingFlow.Step, Consumer<TextContext>> textHandlers;
    /// Обработчики возврата для каждого шага диалога.
    private final Map<BookingFlow.Step, Consumer<CallbackContext>> backHandlers;

    /// Создаёт обработчик сообщений с необходимыми репозиториями и правилами бронирования.
    ///
    /// @param baseRepository    репозиторий баз
    /// @param roomRepository    репозиторий залов
    /// @param bookingRepository репозиторий бронирований
    /// @param rules             правила бронирования
    public BookingMessageProcessingService(BaseRepository baseRepository, RoomRepository roomRepository,
                                           BookingRepository bookingRepository, BookingRules rules) {
        this(baseRepository, roomRepository, bookingRepository, rules, Set.of());
    }

    /// Creates the message processor and configures the authorized owners.
    public BookingMessageProcessingService(BaseRepository baseRepository, RoomRepository roomRepository,
                                           BookingRepository bookingRepository, BookingRules rules,
                                           Set<Long> ownerIds) {
        BookingAvailabilityService availabilityService =
                new BookingAvailabilityService(roomRepository, bookingRepository, rules);
        RussianDateFormatter formatter = new RussianDateFormatter();
        this.selectionService = new BookingSelectionService(baseRepository, roomRepository, bookingRepository, rules,
                availabilityService, keyboards, formatter);
        this.informationService = new BookingInformationService(baseRepository, roomRepository, bookingRepository,
                rules, formatter, keyboards);
        this.formService = new BookingFormService(baseRepository, roomRepository, bookingRepository, rules,
                new FormUtilities(new PhoneNormalizer(), formatter),
                keyboards, selectionService);
        this.ownerManagementService = new OwnerManagementService(baseRepository, roomRepository, ownerIds);
        for (int index = 0; index < userLocks.length; index++) {
            userLocks[index] = new Object();
        }
        callbackHandlers = new HashMap<>();
        callbackHandlers.put(CALLBACK_BACK, context -> back(context));
        callbackHandlers.put("menu", this::handleMenuAction);
        callbackHandlers.put(CALLBACK_BOOK, this::handleBookingCallback);
        callbackHandlers.put(CALLBACK_ABOUT, this::handleAboutCallback);

        menuActionHandlers = new HashMap<>();
        menuActionHandlers.put(CALLBACK_BOOK, context -> selectionService.beginBooking(
                context.chatId(), context.flow(), context.bot()));
        menuActionHandlers.put("mine", context -> sendMainMenu(context.chatId(), context.userId(),
                informationService.myBookingsMessage(context.userId()), context.bot()));
        menuActionHandlers.put(CALLBACK_ABOUT,
                context -> informationService.showAboutBases(context.chatId(), context.bot()));
        menuActionHandlers.put("bases", context ->
                ownerManagementService.showBaseList(context.userId(), context.bot()));
        menuActionHandlers.put("home", context -> returnToMenu(context.chatId(), context.userId(),
                context.flow(), context.bot()));

        bookingActionHandlers = new HashMap<>();
        bookingActionHandlers.put(CALLBACK_BASE, this::selectBase);
        bookingActionHandlers.put(room, this::selectRoom);
        bookingActionHandlers.put(months, this::selectMonths);
        bookingActionHandlers.put(month, this::selectMonth);
        bookingActionHandlers.put(week, this::selectWeek);
        bookingActionHandlers.put(day, this::selectDay);
        bookingActionHandlers.put(time, this::selectTime);
        bookingActionHandlers.put(hours, this::selectHours);
        bookingActionHandlers.put(skip, this::handleSkipComment);
        bookingActionHandlers.put(confirm, this::handleConfirmation);
        bookingActionHandlers.put(cancel, context -> returnToMenu(context.chatId(), context.userId(),
                context.flow(), context.bot()));

        bookingCallbackPartCounts = new HashMap<>();
        bookingCallbackPartCounts.put(CALLBACK_BASE, callbackPartsWithId);
        bookingCallbackPartCounts.put(room, callbackPartsWithId);
        bookingCallbackPartCounts.put(months, callbackPartsWithId);
        bookingCallbackPartCounts.put(month, callbackPartsWithId);
        bookingCallbackPartCounts.put(week, callbackPartsWithId);
        bookingCallbackPartCounts.put(day, callbackPartsWithId);
        bookingCallbackPartCounts.put(time, callbackPartsForTime);
        bookingCallbackPartCounts.put(hours, callbackPartsWithId);
        bookingCallbackPartCounts.put(skip, callbackPartsWithoutArgument);
        bookingCallbackPartCounts.put(confirm, callbackPartsWithoutArgument);
        bookingCallbackPartCounts.put(cancel, callbackPartsWithoutArgument);

        aboutActionHandlers = new HashMap<>();
        aboutActionHandlers.put(CALLBACK_BASE, this::showSelectedBaseInformation);
        aboutActionHandlers.put(list, context -> informationService.showAboutBases(context.chatId(), context.bot()));

        aboutCallbackPartCounts = new HashMap<>();
        aboutCallbackPartCounts.put(CALLBACK_BASE, callbackPartsWithId);
        aboutCallbackPartCounts.put(list, callbackPartsWithoutArgument);

        textCommandHandlers = new HashMap<>();
        textCommandHandlers.put(COMMAND_START, context -> returnToMenu(context.chatId(), context.userId(),
                context.flow(), context.bot()));
        textCommandHandlers.put(COMMAND_MENU, context -> returnToMenu(context.chatId(), context.userId(),
                context.flow(), context.bot()));
        textCommandHandlers.put(BACK, context -> back(new CallbackContext(context.chatId(), context.userId(),
                context.flow(), new String[] {CALLBACK_BACK}, context.bot())));

        textHandlers = new HashMap<>();
        textHandlers.put(BookingFlow.Step.BOOK_NAME, context -> formService.handleNameText(
                context.chatId(), context.flow(), context.text(), context.bot()));
        textHandlers.put(BookingFlow.Step.BOOK_PHONE, context -> formService.handlePhoneText(
                context.chatId(), context.flow(), context.text(), context.bot()));
        textHandlers.put(BookingFlow.Step.BOOK_COMMENT, context -> formService.handleCommentText(
                context.chatId(), context.flow(), context.text(), context.bot()));

        backHandlers = new HashMap<>();
        backHandlers.put(BookingFlow.Step.MENU, context -> returnToMenu(
                context.chatId(), context.userId(), context.flow(), context.bot()));
        backHandlers.put(BookingFlow.Step.BOOK_BASES, context -> returnToMenu(
                context.chatId(), context.userId(), context.flow(), context.bot()));
        backHandlers.put(BookingFlow.Step.BOOK_ROOMS,
                context -> selectionService.beginBooking(context.chatId(), context.flow(), context.bot()));
        backHandlers.put(BookingFlow.Step.BOOK_MONTH,
                context -> selectionService.showRoomChoices(context.chatId(), context.flow(), context.bot()));
        backHandlers.put(BookingFlow.Step.BOOK_WEEK,
                context -> selectionService.showMonthChoices(context.chatId(), context.flow(), context.bot()));
        backHandlers.put(BookingFlow.Step.BOOK_DAY, context -> selectionService.showWeekChoices(
                context.chatId(), context.flow(), context.flow().selectedMonth, context.bot()));
        backHandlers.put(BookingFlow.Step.BOOK_TIME,
                context -> selectionService.showDayChoices(context.chatId(), context.flow(), context.bot()));
        backHandlers.put(BookingFlow.Step.BOOK_DURATION, context -> backFromDuration(
                context.chatId(), context.flow(), context.bot()));
        backHandlers.put(BookingFlow.Step.BOOK_NAME,
                context -> formService.backFromName(context.chatId(), context.flow(), context.bot()));
        backHandlers.put(BookingFlow.Step.BOOK_PHONE,
                context -> formService.backToName(context.chatId(), context.flow(), context.bot()));
        backHandlers.put(BookingFlow.Step.BOOK_COMMENT,
                context -> formService.backToName(context.chatId(), context.flow(), context.bot()));
        backHandlers.put(BookingFlow.Step.BOOK_CONFIRM,
                context -> formService.backToName(context.chatId(), context.flow(), context.bot()));
    }

    /// Обрабатывает входящее сообщение или callback и отправляет ответ через указанного бота.
    ///
    /// @param msg    входящее сообщение
    /// @param chatId идентификатор чата, куда отправлять ответы
    /// @param userId идентификатор Telegram-пользователя, которому принадлежит диалог
    /// @param bot    бот для отправки ответов
    @Override
    public void processMessage(@NotNull Message msg, long chatId, long userId, @NotNull Bot bot) {
        Object userLock = userLocks[Math.floorMod(Long.hashCode(userId), userLocks.length)];
        synchronized (userLock) {
            if (msg.buttonData() != null) {
                handleCallback(msg.buttonData(), chatId, userId, bot);
            } else if (msg.text() != null) {
                handleText(msg.text(), chatId, userId, bot);
            }
        }
    }

    /// Обрабатывает текст пользователя в соответствии с текущим шагом диалога.
    ///
    /// @param rawText исходный текст сообщения
    /// @param chatId  идентификатор чата пользователя
    private void handleText(String rawText, long chatId, long userId, Bot bot) {
        String text = rawText.trim();
        if (!ownerManagementService.handleText(chatId, userId, text, bot)) {
            BookingFlow flow = flows.get(userId);
            Consumer<TextContext> commandHandler = textCommandHandlers.get(text);
            if (flow == null && commandHandler != null) {
                flow = new BookingFlow();
            }
            if (flow != null) {
                dispatchText(flow, commandHandler, chatId, userId, text, bot);
            }
        }
    }

    /// Dispatches a command or text value to the active booking step.
    ///
    /// @param flow current user's booking flow
    /// @param commandHandler global text command handler, if any
    /// @param chatId destination chat ID
    /// @param userId Telegram sender ID
    /// @param text trimmed user text
    /// @param bot bot used to send responses
    private void dispatchText(BookingFlow flow, Consumer<TextContext> commandHandler, long chatId, long userId,
                              String text, Bot bot) {
        TextContext context = new TextContext(chatId, userId, flow, text, bot);
        if (commandHandler != null) {
            commandHandler.accept(context);
        } else {
            Consumer<TextContext> handler = textHandlers.get(flow.step);
            if (handler != null) {
                handler.accept(context);
            }
        }
    }

    /// Ищет обработчик callback-команды по её пространству имён.
    ///
    /// @param data   callback-данные нажатой кнопки
    /// @param chatId идентификатор чата пользователя
    private void handleCallback(String data, long chatId, long userId, Bot bot) {
        String[] parts = data.split(":");
        if ("owner".equals(parts[0])) {
            ownerManagementService.handleCallback(chatId, userId, parts, bot);
            return;
        }
        CallbackContext context = new CallbackContext(chatId, userId, flow(userId), parts, bot);
        Consumer<CallbackContext> handler = callbackHandlers.get(parts[0]);
        if (handler != null) {
            handler.accept(context);
        }
    }

    /// Выбирает обработчик действия главного меню по коду кнопки.
    ///
    /// @param context контекст callback-команды
    private void handleMenuAction(CallbackContext context) {
        ownerManagementService.cancel(context.userId());
        if (context.parts().length < callbackPartsWithoutArgument) {
            return;
        }
        Consumer<CallbackContext> handler = menuActionHandlers.get(context.parts()[1]);
        if (handler != null) {
            handler.accept(context);
        } else {
            returnToMenu(context.chatId(), context.userId(), context.flow(), context.bot());
        }
    }

    /// Выбирает обработчик подкоманды бронирования по хранимой карте обработчиков.
    ///
    /// @param context контекст callback-команды
    private void handleBookingCallback(CallbackContext context) {
        if (context.parts().length < callbackPartsWithoutArgument) {
            return;
        }
        String action = context.parts()[1];
        Integer requiredPartCount = bookingCallbackPartCounts.get(action);
        Consumer<CallbackContext> handler = bookingActionHandlers.get(action);
        if (requiredPartCount != null && context.parts().length >= requiredPartCount && handler != null) {
            handler.accept(context);
        }
    }

    /// Выбирает обработчик команды раздела «О базе».
    ///
    /// @param context контекст callback-команды
    private void handleAboutCallback(CallbackContext context) {
        if (context.parts().length < callbackPartsWithoutArgument) {
            return;
        }
        String action = context.parts()[1];
        Integer requiredParts = aboutCallbackPartCounts.get(action);
        Consumer<CallbackContext> handler = aboutActionHandlers.get(action);
        if (requiredParts != null && context.parts().length >= requiredParts && handler != null) {
            handler.accept(context);
        }
    }

    /// Выбирает базу для нового бронирования.
    ///
    /// @param context контекст callback-команды
    private void selectBase(CallbackContext context) {
        context.flow().clearBooking();
        context.flow().baseId = Long.parseLong(context.parts()[callbackPartsWithoutArgument]);
        selectionService.showRoomChoices(context.chatId(), context.flow(), context.bot());
    }

    /// Выбирает зал и открывает список месяцев.
    ///
    /// @param context контекст callback-команды
    private void selectRoom(CallbackContext context) {
        long roomId = Long.parseLong(context.parts()[callbackPartsWithoutArgument]);
        boolean availableRoom = selectionService.isAvailableRoom(context.flow().baseId, roomId);
        if (!availableRoom) {
            selectionService.showRoomChoices(context.chatId(), context.flow(), context.bot());
            return;
        }
        context.flow().roomId = roomId;
        context.flow().monthOffset = 0;
        selectionService.showMonthChoices(context.chatId(), context.flow(), context.bot());
    }

    /// Переключает страницу месяцев.
    ///
    /// @param context контекст callback-команды
    private void selectMonths(CallbackContext context) {
        context.flow().monthOffset = Integer.parseInt(context.parts()[callbackPartsWithoutArgument]);
        selectionService.showMonthChoices(context.chatId(), context.flow(), context.bot());
    }

    /// Выбирает месяц для бронирования.
    ///
    /// @param context контекст callback-команды
    private void selectMonth(CallbackContext context) {
        YearMonth selectedMonth = YearMonth.parse(context.parts()[callbackPartsWithoutArgument]);
        context.flow().selectedMonth = selectedMonth;
        selectionService.showWeekChoices(context.chatId(), context.flow(), selectedMonth, context.bot());
    }

    /// Выбирает неделю и показывает доступные дни.
    ///
    /// @param context контекст callback-команды
    private void selectWeek(CallbackContext context) {
        context.flow().bookingDate = LocalDate.parse(context.parts()[callbackPartsWithoutArgument]);
        context.flow().selectedWeek = context.flow().bookingDate;
        selectionService.showDayChoices(context.chatId(), context.flow(), context.bot());
    }

    /// Выбирает дату бронирования.
    ///
    /// @param context контекст callback-команды
    private void selectDay(CallbackContext context) {
        context.flow().bookingDate = LocalDate.parse(context.parts()[callbackPartsWithoutArgument]);
        selectionService.showTimeChoices(context.chatId(), context.flow(), context.bot());
    }

    /// Выбирает время начала бронирования.
    ///
    /// @param context контекст callback-команды
    private void selectTime(CallbackContext context) {
        context.flow().bookingDate = LocalDate.parse(context.parts()[callbackPartsWithoutArgument]);
        context.flow().startHour = Integer.parseInt(context.parts()[START_HOUR_PART]);
        selectionService.showDurationChoices(context.chatId(), context.flow(), context.bot());
    }

    /// Выбирает длительность бронирования и просит ввести имя.
    ///
    /// @param context контекст callback-команды
    private void selectHours(CallbackContext context) {
        context.flow().hours = Integer.parseInt(context.parts()[callbackPartsWithoutArgument]);
        formService.beginCustomerDetails(context.chatId(), context.flow(), context.bot());
    }

    /// Обрабатывает нажатие кнопки пропуска комментария.
    ///
    /// @param context контекст callback-команды
    private void handleSkipComment(CallbackContext context) {
        formService.skipBookingComment(context.chatId(), context.flow(), context.bot());
    }

    /// Обрабатывает подтверждение бронирования, только если пользователь находится на нужном шаге.
    ///
    /// @param context контекст callback-команды
    private void handleConfirmation(CallbackContext context) {
        if (context.flow().step == BookingFlow.Step.BOOK_CONFIRM) {
            boolean created = formService.confirmBooking(context.userId(), context.flow());
            context.flow().clearBooking();
            context.flow().step = BookingFlow.Step.MENU;
            if (created) {
                completeToMenu(context.chatId(), context.userId(), context.flow(),
                        "Готово, запись создана.", context.bot());
            } else {
                ownerManagementService.cancel(context.userId());
                flows.remove(context.userId());
                send(context.chatId(), "Это время больше недоступно. Выберите другое время.", keyboards.choices(
                        keyboards.button("Выбрать время", MENU_BOOK),
                        keyboards.button("Главное меню", MENU_HOME)), context.bot());
            }
        }
    }

    /// Показывает информацию о базе, выбранной в разделе «О базе».
    ///
    /// @param context контекст callback-команды
    private void showSelectedBaseInformation(CallbackContext context) {
        informationService.showBaseInformation(context.chatId(),
                Long.parseLong(context.parts()[callbackPartsWithoutArgument]), context.bot());
    }

    /// Возвращает диалог на предыдущий шаг с помощью карты обработчиков.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow   состояние диалога пользователя
    private void back(CallbackContext context) {
        Consumer<CallbackContext> handler = backHandlers.get(context.flow().step);
        if (handler == null) {
            returnToMenu(context.chatId(), context.userId(), context.flow(), context.bot());
            return;
        }
        handler.accept(context);
    }

    /// Сбрасывает форму и возвращает пользователя в главное меню.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow   состояние диалога пользователя
    private void returnToMenu(long chatId, long userId, BookingFlow flow, Bot bot) {
        ownerManagementService.cancel(userId);
        flow.clearBooking();
        flow.step = BookingFlow.Step.MENU;
        flows.remove(userId);
        showMainMenu(chatId, userId, bot);
    }

    /// Возвращает пользователя к выбору времени и сбрасывает выбранную длительность.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow   состояние диалога пользователя
    private void backFromDuration(long chatId, BookingFlow flow, Bot bot) {
        flow.hours = 0;
        selectionService.showTimeChoices(chatId, flow, bot);
    }

    /// Показывает главное меню бота.
    ///
    /// @param chatId идентификатор чата пользователя
    private void showMainMenu(long chatId, long userId, Bot bot) {
        List<List<Button>> rows = new ArrayList<List<Button>>();
        rows.add(keyboards.row(keyboards.button("Забронировать", MENU_BOOK),
                keyboards.button("Мои записи", "menu:mine")));
        rows.add(keyboards.row(keyboards.button("О базе", "menu:about")));
        if (ownerManagementService.isOwner(userId)) {
            rows.add(keyboards.row(keyboards.button("Базы", "menu:bases")));
        }
        send(chatId, "Здравствуйте! Что хотите сделать?", rows, bot);
    }

    /// Отправляет уведомление и вслед за ним открывает главное меню.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param notice текст уведомления
    private void sendMainMenu(long chatId, long userId, String notice, Bot bot) {
        send(chatId, notice, null, bot);
        showMainMenu(chatId, userId, bot);
    }

    private void completeToMenu(long chatId, long userId, BookingFlow flow, String notice, Bot bot) {
        ownerManagementService.cancel(userId);
        flow.clearBooking();
        flow.step = BookingFlow.Step.MENU;
        flows.remove(userId);
        send(chatId, notice, null, bot);
        showMainMenu(chatId, userId, bot);
    }

    /// Возвращает состояние диалога пользователя, создавая его при первом обращении.
    ///
    /// @param userId идентификатор пользователя
    /// @return состояние диалога пользователя
    private BookingFlow flow(long userId) {
        return flows.computeIfAbsent(userId, id -> new BookingFlow());
    }

    /// Отправляет текстовое сообщение с клавиатурой.
    ///
    /// @param chatId   идентификатор чата получателя
    /// @param text     текст сообщения
    /// @param keyboard строки кнопок; null означает сообщение без клавиатуры
    private void send(long chatId, String text, List<List<Button>> keyboard, Bot bot) {
        List<List<Button>> safeKeyboard = keyboard == null ? List.of() : keyboard;
        bot.sendMessage(new Message(text, null, safeKeyboard), chatId);
    }

    /// Данные callback-команды, необходимые обработчику.
    ///
    /// @param chatId идентификатор чата
    /// @param userId идентификатор пользователя
    /// @param flow   состояние пользовательского диалога
    /// @param parts  части callback-команды, разделённые двоеточием
    private record CallbackContext(long chatId, long userId, BookingFlow flow, String[] parts, Bot bot) {
    }

    /// Данные текстового сообщения, необходимые обработчику текущего шага.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние пользовательского диалога
    /// @param text   текст после удаления внешних пробелов
    private record TextContext(long chatId, long userId, BookingFlow flow, String text, Bot bot) {
    }
}

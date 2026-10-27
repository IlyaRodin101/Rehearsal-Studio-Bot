package ru.urfu.echobot.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Обрабатывает сообщения пользователей и проводит их через сценарии бронирования и просмотра информации.
public final class BookingMessageProcessingService implements MessageProcessingService {
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
    /// Транспорт, используемый для отправки ответа текущему пользователю.
    private volatile Bot transport;
    /// Состояния диалогов пользователей, индексированные по идентификатору пользователя.
    private final Map<Long, BookingFlow> flows = new ConcurrentHashMap<Long, BookingFlow>();
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
    private final Map<BookingFlow.Step, BiConsumer<Long, BookingFlow>> backHandlers;

    /// Создаёт обработчик сообщений с необходимыми репозиториями и правилами бронирования.
    ///
    /// @param baseRepository    репозиторий баз
    /// @param roomRepository    репозиторий залов
    /// @param bookingRepository репозиторий бронирований
    /// @param rules             правила бронирования
    public BookingMessageProcessingService(BaseRepository baseRepository, RoomRepository roomRepository,
                                           BookingRepository bookingRepository, BookingRules rules) {
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
        callbackHandlers = new HashMap<>();
        callbackHandlers.put(CALLBACK_BACK, context -> back(context.chatId(), context.flow()));
        callbackHandlers.put("menu", this::handleMenuAction);
        callbackHandlers.put(CALLBACK_BOOK, this::handleBookingCallback);
        callbackHandlers.put(CALLBACK_ABOUT, this::handleAboutCallback);

        menuActionHandlers = new HashMap<>();
        menuActionHandlers.put(CALLBACK_BOOK, context -> selectionService.beginBooking(
                context.chatId(), context.flow(), transport));
        menuActionHandlers.put("mine", context -> sendMainMenu(context.chatId(),
                informationService.myBookingsMessage(context.userId())));
        menuActionHandlers.put(CALLBACK_ABOUT,
                context -> informationService.showAboutBases(context.chatId(), transport));
        menuActionHandlers.put("home", context -> returnToMenu(context.chatId(), context.flow()));

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
        bookingActionHandlers.put(cancel, context -> returnToMenu(context.chatId(), context.flow()));

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
        aboutActionHandlers.put(list, context -> informationService.showAboutBases(context.chatId(), transport));

        aboutCallbackPartCounts = new HashMap<>();
        aboutCallbackPartCounts.put(CALLBACK_BASE, callbackPartsWithId);
        aboutCallbackPartCounts.put(list, callbackPartsWithoutArgument);

        textCommandHandlers = new HashMap<>();
        textCommandHandlers.put("/start", context -> returnToMenu(context.chatId(), context.flow()));
        textCommandHandlers.put("/menu", context -> returnToMenu(context.chatId(), context.flow()));
        textCommandHandlers.put(BACK, context -> back(context.chatId(), context.flow()));

        textHandlers = new HashMap<>();
        textHandlers.put(BookingFlow.Step.BOOK_NAME, context -> formService.handleNameText(
                context.chatId(), context.flow(), context.text(), transport));
        textHandlers.put(BookingFlow.Step.BOOK_PHONE, context -> formService.handlePhoneText(
                context.chatId(), context.flow(), context.text(), transport));
        textHandlers.put(BookingFlow.Step.BOOK_COMMENT, context -> formService.handleCommentText(
                context.chatId(), context.flow(), context.text(), transport));

        backHandlers = new HashMap<>();
        backHandlers.put(BookingFlow.Step.MENU, this::returnToMenu);
        backHandlers.put(BookingFlow.Step.BOOK_BASES, this::returnToMenu);
        backHandlers.put(BookingFlow.Step.BOOK_ROOMS,
                (chatId, flow) -> selectionService.beginBooking(chatId, flow, transport));
        backHandlers.put(BookingFlow.Step.BOOK_MONTH,
                (chatId, flow) -> selectionService.showRoomChoices(chatId, flow, transport));
        backHandlers.put(BookingFlow.Step.BOOK_WEEK,
                (chatId, flow) -> selectionService.showMonthChoices(chatId, flow, transport));
        backHandlers.put(BookingFlow.Step.BOOK_DAY, (chatId, flow) -> selectionService.showWeekChoices(
                chatId, flow, flow.selectedMonth, transport));
        backHandlers.put(BookingFlow.Step.BOOK_TIME,
                (chatId, flow) -> selectionService.showDayChoices(chatId, flow, transport));
        backHandlers.put(BookingFlow.Step.BOOK_DURATION, this::backFromDuration);
        backHandlers.put(BookingFlow.Step.BOOK_NAME,
                (chatId, flow) -> formService.backFromName(chatId, flow, transport));
        backHandlers.put(BookingFlow.Step.BOOK_PHONE,
                (chatId, flow) -> formService.backToName(chatId, flow, transport));
        backHandlers.put(BookingFlow.Step.BOOK_COMMENT,
                (chatId, flow) -> formService.backToName(chatId, flow, transport));
        backHandlers.put(BookingFlow.Step.BOOK_CONFIRM,
                (chatId, flow) -> formService.backToName(chatId, flow, transport));
    }

    /// Обрабатывает входящее сообщение или callback и отправляет ответ через указанного бота.
    ///
    /// @param msg    входящее сообщение
    /// @param chatId идентификатор чата пользователя
    /// @param bot    бот для отправки ответов
    @Override
    public void processMessage(@NotNull Message msg, long chatId, @NotNull Bot bot) {
        transport = bot;
        if (msg.buttonData() != null) {
            handleCallback(msg.buttonData(), chatId);
        } else if (msg.text() != null) {
            handleText(msg.text(), chatId);
        }
    }

    /// Обрабатывает текст пользователя в соответствии с текущим шагом диалога.
    ///
    /// @param rawText исходный текст сообщения
    /// @param chatId  идентификатор чата пользователя
    private void handleText(String rawText, long chatId) {
        BookingFlow flow = flow(chatId);
        String text = rawText.trim();
        TextContext context = new TextContext(chatId, flow, text);
        Consumer<TextContext> commandHandler = textCommandHandlers.get(text);
        if (commandHandler != null) {
            commandHandler.accept(context);
            return;
        }
        Consumer<TextContext> handler = textHandlers.get(flow.step);
        if (handler != null) {
            handler.accept(context);
        }
    }

    /// Ищет обработчик callback-команды по её пространству имён.
    ///
    /// @param data   callback-данные нажатой кнопки
    /// @param chatId идентификатор чата пользователя
    private void handleCallback(String data, long chatId) {
        String[] parts = data.split(":");
        CallbackContext context = new CallbackContext(chatId, chatId, flow(chatId), parts);
        Consumer<CallbackContext> handler = callbackHandlers.get(parts[0]);
        if (handler != null) {
            handler.accept(context);
        }
    }

    /// Выбирает обработчик действия главного меню по коду кнопки.
    ///
    /// @param context контекст callback-команды
    private void handleMenuAction(CallbackContext context) {
        if (context.parts().length < callbackPartsWithoutArgument) {
            return;
        }
        Consumer<CallbackContext> handler = menuActionHandlers.get(context.parts()[1]);
        if (handler != null) {
            handler.accept(context);
        } else {
            returnToMenu(context.chatId(), context.flow());
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
        selectionService.showRoomChoices(context.chatId(), context.flow(), transport);
    }

    /// Выбирает зал и открывает список месяцев.
    ///
    /// @param context контекст callback-команды
    private void selectRoom(CallbackContext context) {
        context.flow().roomId = Long.parseLong(context.parts()[callbackPartsWithoutArgument]);
        context.flow().monthOffset = 0;
        selectionService.showMonthChoices(context.chatId(), context.flow(), transport);
    }

    /// Переключает страницу месяцев.
    ///
    /// @param context контекст callback-команды
    private void selectMonths(CallbackContext context) {
        context.flow().monthOffset = Integer.parseInt(context.parts()[callbackPartsWithoutArgument]);
        selectionService.showMonthChoices(context.chatId(), context.flow(), transport);
    }

    /// Выбирает месяц для бронирования.
    ///
    /// @param context контекст callback-команды
    private void selectMonth(CallbackContext context) {
        YearMonth selectedMonth = YearMonth.parse(context.parts()[callbackPartsWithoutArgument]);
        context.flow().selectedMonth = selectedMonth;
        selectionService.showWeekChoices(context.chatId(), context.flow(), selectedMonth, transport);
    }

    /// Выбирает неделю и показывает доступные дни.
    ///
    /// @param context контекст callback-команды
    private void selectWeek(CallbackContext context) {
        context.flow().bookingDate = LocalDate.parse(context.parts()[callbackPartsWithoutArgument]);
        context.flow().selectedWeek = context.flow().bookingDate;
        selectionService.showDayChoices(context.chatId(), context.flow(), transport);
    }

    /// Выбирает дату бронирования.
    ///
    /// @param context контекст callback-команды
    private void selectDay(CallbackContext context) {
        context.flow().bookingDate = LocalDate.parse(context.parts()[callbackPartsWithoutArgument]);
        selectionService.showTimeChoices(context.chatId(), context.flow(), transport);
    }

    /// Выбирает время начала бронирования.
    ///
    /// @param context контекст callback-команды
    private void selectTime(CallbackContext context) {
        context.flow().bookingDate = LocalDate.parse(context.parts()[callbackPartsWithoutArgument]);
        context.flow().startHour = Integer.parseInt(context.parts()[START_HOUR_PART]);
        selectionService.showDurationChoices(context.chatId(), context.flow(), transport);
    }

    /// Выбирает длительность бронирования и просит ввести имя.
    ///
    /// @param context контекст callback-команды
    private void selectHours(CallbackContext context) {
        context.flow().hours = Integer.parseInt(context.parts()[callbackPartsWithoutArgument]);
        formService.beginCustomerDetails(context.chatId(), context.flow(), transport);
    }

    /// Обрабатывает нажатие кнопки пропуска комментария.
    ///
    /// @param context контекст callback-команды
    private void handleSkipComment(CallbackContext context) {
        formService.skipBookingComment(context.chatId(), context.flow(), transport);
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
                sendMainMenu(context.chatId(), "Готово, запись создана.");
            } else {
                send(context.chatId(), "Это время больше недоступно. Выберите другое время.", keyboards.choices(
                        keyboards.button("Выбрать время", MENU_BOOK),
                        keyboards.button("Главное меню", MENU_HOME)));
            }
        }
    }

    /// Показывает информацию о базе, выбранной в разделе «О базе».
    ///
    /// @param context контекст callback-команды
    private void showSelectedBaseInformation(CallbackContext context) {
        informationService.showBaseInformation(context.chatId(),
                Long.parseLong(context.parts()[callbackPartsWithoutArgument]), transport);
    }

    /// Возвращает диалог на предыдущий шаг с помощью карты обработчиков.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow   состояние диалога пользователя
    private void back(long chatId, BookingFlow flow) {
        BiConsumer<Long, BookingFlow> handler = backHandlers.get(flow.step);
        if (handler == null) {
            returnToMenu(chatId, flow);
            return;
        }
        handler.accept(chatId, flow);
    }

    /// Сбрасывает форму и возвращает пользователя в главное меню.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow   состояние диалога пользователя
    private void returnToMenu(long chatId, BookingFlow flow) {
        flow.clearBooking();
        flow.step = BookingFlow.Step.MENU;
        showMainMenu(chatId);
    }

    /// Возвращает пользователя к выбору времени и сбрасывает выбранную длительность.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow   состояние диалога пользователя
    private void backFromDuration(long chatId, BookingFlow flow) {
        flow.hours = 0;
        selectionService.showTimeChoices(chatId, flow, transport);
    }

    /// Показывает главное меню бота.
    ///
    /// @param chatId идентификатор чата пользователя
    private void showMainMenu(long chatId) {
        List<List<Button>> rows = new ArrayList<List<Button>>();
        rows.add(keyboards.row(keyboards.button("Забронировать", MENU_BOOK),
                keyboards.button("Мои записи", "menu:mine")));
        rows.add(keyboards.row(keyboards.button("О базе", "menu:about")));
        send(chatId, "Здравствуйте! Что хотите сделать?", rows);
    }

    /// Отправляет уведомление и вслед за ним открывает главное меню.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param notice текст уведомления
    private void sendMainMenu(long chatId, String notice) {
        send(chatId, notice, null);
        showMainMenu(chatId);
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
    private void send(long chatId, String text, List<List<Button>> keyboard) {
        List<List<Button>> safeKeyboard = keyboard == null ? List.of() : keyboard;
        transport.sendMessage(new Message(text, null, safeKeyboard), chatId);
    }

    /// Данные callback-команды, необходимые обработчику.
    ///
    /// @param chatId идентификатор чата
    /// @param userId идентификатор пользователя
    /// @param flow   состояние пользовательского диалога
    /// @param parts  части callback-команды, разделённые двоеточием
    private record CallbackContext(long chatId, long userId, BookingFlow flow, String[] parts) {
    }

    /// Данные текстового сообщения, необходимые обработчику текущего шага.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние пользовательского диалога
    /// @param text   текст после удаления внешних пробелов
    private record TextContext(long chatId, BookingFlow flow, String text) {
    }
}

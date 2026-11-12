package ru.urfu.echobot.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.HotSlot;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Показывает «горячие» слоты и начинает бронирование выбранного слота.
public final class HotSlotController {
    /// Позиция аргумента действия в callback-данных.
    private static final int ARGUMENT_PART = 2;
    /// Позиция ключа времени начала в callback-данных.
    private static final int START_PART = 3;
    /// Формат времени начала в callback-кнопках.
    private static final String CALLBACK_PATTERN = "uuuuMMddHHmm";
    /// Действие со списком баз.
    private static final String BASES = "bases";
    /// Действие выбора базы.
    private static final String BASE = "base";
    /// Действие начала бронирования слота.
    private static final String BOOK = "book";
    /// Callback списка баз.
    private static final String BASES_CALLBACK = "hot:bases";
    /// Callback главного меню.
    private static final String MENU_HOME = "menu:home";
    /// Подпись кнопки возврата.
    private static final String BACK = "Назад";
    /// Заголовок списка баз.
    private static final String SELECT_BASE = "Выберите базу:";
    /// Сообщение об отсутствии слотов.
    private static final String NO_SLOTS = "Сейчас «горячих» слотов нет.";
    /// Сообщение о недоступном слоте.
    private static final String UNAVAILABLE = "Этот слот уже недоступен. Выберите другой.";
    /// Количество кнопок баз в строке.
    private static final int BASES_PER_ROW = 2;

    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Хранилище залов.
    private final RoomRepository roomRepository;
    /// Расчёт «горячих» слотов.
    private final HotSlotService hotSlotService;
    /// Сервис выбора времени и длительности.
    private final BookingTimeSelectionService timeSelection;
    /// Контроллер главного меню.
    private final BookingMenuController menuController;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards;
    /// Правила бронирования.
    private final BookingRules rules;
    /// Форматер дат и времени.
    private final RussianDateFormatter formatter = new RussianDateFormatter();
    /// Формат времени начала в callback-кнопках.
    private final DateTimeFormatter callbackFormat = DateTimeFormatter.ofPattern(CALLBACK_PATTERN);

    /// Создаёт контроллер «горячих» слотов.
    ///
    /// @param baseRepository  хранилище баз
    /// @param roomRepository  хранилище залов
    /// @param hotSlotService  расчёт слотов
    /// @param timeSelection   выбор времени и длительности
    /// @param menuController  главное меню
    /// @param keyboards       фабрика клавиатур
    /// @param rules           правила бронирования
    public HotSlotController(BaseRepository baseRepository, RoomRepository roomRepository,
                             HotSlotService hotSlotService, BookingTimeSelectionService timeSelection,
                             BookingMenuController menuController, BookingKeyboardFactory keyboards,
                             BookingRules rules) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.hotSlotService = hotSlotService;
        this.timeSelection = timeSelection;
        this.menuController = menuController;
        this.keyboards = keyboards;
        this.rules = rules;
    }

    /// Показывает список баз для выбора «горячих» слотов.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщений
    public void showBaseList(long chatId, BookingFlow flow, Bot bot) {
        flow.clearBooking();
        flow.step = BookingFlow.Step.MENU;
        List<Base> bases = baseRepository.findAll();
        if (bases.isEmpty()) {
            send(bot, chatId, "Пока нет доступных баз.", keyboards.choices(keyboards.button(BACK, MENU_HOME)));
            return;
        }
        List<List<Button>> rows = new ArrayList<>();
        for (Base base : bases) {
            keyboards.addButton(rows, keyboards.button(base.name(), "hot:base:" + base.id()), BASES_PER_ROW);
        }
        rows.add(keyboards.row(keyboards.button(BACK, MENU_HOME)));
        send(bot, chatId, SELECT_BASE, keyboards.markup(rows));
    }

    /// Обрабатывает callback-команду раздела «Горячие слоты».
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void handle(BookingCallback context, Bot bot) {
        switch (context.action()) {
            case BASES:
                showBaseList(context.chatId(), context.flow(), bot);
                break;
            case BASE:
                selectBase(context, bot);
                break;
            case BOOK:
                book(context, bot);
                break;
            default:
                menuController.returnToMenu(context.chatId(), context.flow(), bot);
                break;
        }
    }

    /// Показывает «горячие» слоты выбранной базы.
    ///
    /// @param chatId идентификатор чата
    /// @param baseId идентификатор базы
    /// @param bot    бот для отправки сообщений
    public void showSlots(long chatId, long baseId, Bot bot) {
        Base base = baseRepository.findById(baseId).orElse(null);
        if (base == null) {
            send(bot, chatId, "База больше недоступна.", keyboards.choices(keyboards.button(BACK, BASES_CALLBACK)));
            return;
        }
        List<HotSlot> slots = hotSlotService.findHotSlots(base);
        List<List<Button>> rows = new ArrayList<>();
        for (HotSlot slot : slots) {
            String text = slot.room().name() + ", " + formatter.relativeDate(slot.start().toLocalDate(),
                    rules.today(base)) + ", " + formatter.timeRange(slot.start(), slot.end());
            keyboards.addButton(rows, keyboards.button(text, "hot:book:" + slot.room().id() + ":"
                    + slot.start().format(callbackFormat)), 1);
        }
        rows.add(keyboards.row(keyboards.button(BACK, BASES_CALLBACK)));
        String text = slots.isEmpty() ? NO_SLOTS : "«Горячие» слоты базы «" + base.name() + "»:";
        send(bot, chatId, text, keyboards.markup(rows));
    }

    /// Начинает обычный диалог бронирования выбранного слота.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    private void book(BookingCallback context, Bot bot) {
        Room room = findRoom(context);
        Base base = room == null ? null : baseRepository.findById(room.baseId()).orElse(null);
        LocalDateTime start = parseStart(context);
        if (base != null && start != null && hotSlotService.isAvailable(base, room, start)) {
            context.flow().startHotSlot(base.id(), room.id(), start);
            timeSelection.showDurationChoices(context.chatId(), context.flow(), bot);
        } else {
            send(bot, context.chatId(), UNAVAILABLE,
                    keyboards.choices(keyboards.button(BACK, BASES_CALLBACK)));
        }
    }

    /// Выбирает базу из callback-команды.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    private void selectBase(BookingCallback context, Bot bot) {
        try {
            showSlots(context.chatId(), Long.parseLong(context.part(ARGUMENT_PART)), bot);
        } catch (NumberFormatException exception) {
            showBaseList(context.chatId(), context.flow(), bot);
        }
    }

    /// Возвращает зал из callback-команды или null, если идентификатор некорректен.
    ///
    /// @param context callback-команда
    /// @return зал или null
    private Room findRoom(BookingCallback context) {
        try {
            return roomRepository.findById(Long.parseLong(context.part(ARGUMENT_PART))).orElse(null);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /// Возвращает время начала из callback-команды или null, если оно некорректно.
    ///
    /// @param context callback-команда
    /// @return время начала или null
    private LocalDateTime parseStart(BookingCallback context) {
        try {
            return LocalDateTime.parse(context.part(START_PART), callbackFormat);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /// Отправляет текст и клавиатуру через бота.
    ///
    /// @param bot      транспорт для отправки
    /// @param chatId   идентификатор чата
    /// @param text     текст сообщения
    /// @param keyboard клавиатура сообщения
    private void send(Bot bot, long chatId, String text, List<List<Button>> keyboard) {
        bot.sendMessage(new Message(text, null, keyboard), chatId);
    }
}

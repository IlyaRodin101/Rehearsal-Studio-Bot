package ru.urfu.echobot.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
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
    /// Префикс callback для выбора базы бронирования.
    private static final String BOOK_BASE = "book:base:";
    /// Префикс callback для выбора месяца.
    private static final String BOOK_MONTH = "book:month:";
    /// Префикс callback для переключения страницы месяцев.
    private static final String BOOK_MONTHS = "book:months:";
    /// Префикс callback для выбора недели.
    private static final String BOOK_WEEK = "book:week:";
    /// Префикс callback для выбора дня.
    private static final String BOOK_DAY = "book:day:";
    /// Текст запроса имени клиента.
    private static final String ENTER_NAME = "Введите имя для связи.";
    /// Текст запроса номера телефона клиента.
    private static final String ENTER_PHONE = "Введите номер телефона для связи.";
    /// Текст приглашения выбрать базу.
    private static final String SELECT_BASE = "Выберите базу:";
    /// Сообщение об отсутствии доступных дней.
    private static final String NO_DAYS = "В этой неделе нет доступных дней.";
    /// Индекс часа начала в частях callback-данных.
    private static final int START_HOUR_PART = 3;
    /// Число месяцев, показываемых на одной странице.
    private static final int MONTHS_PER_PAGE = 6;
    /// Число дней в неделе.
    private static final int DAYS_IN_WEEK = 7;
    /// Число кнопок выбора времени в одной строке.
    private static final int TIME_BUTTONS_PER_ROW = 4;
    /// Журнал для ошибок и событий обработки сообщений.
    private static final Logger LOGGER = LoggerFactory.getLogger(BookingMessageProcessingService.class);

    /// Репозиторий баз.
    private final BaseRepository baseRepository;
    /// Репозиторий залов.
    private final RoomRepository roomRepository;
    /// Репозиторий бронирований.
    private final BookingRepository bookingRepository;
    /// Правила доступности и сроков бронирования.
    private final BookingRules rules;
    /// Транспорт, используемый для отправки ответа текущему пользователю.
    private volatile Bot transport;
    /// Состояния диалогов пользователей, индексированные по идентификатору пользователя.
    private final Map<Long, Flow> flows = new ConcurrentHashMap<Long, Flow>();
    /// Нормализатор российских номеров телефонов.
    private final PhoneNormalizer phoneNormalizer = new PhoneNormalizer();
    /// Форматер дат и времени для сообщений.
    private final RussianDateFormatter formatter = new RussianDateFormatter();

    /// Создаёт обработчик сообщений с необходимыми репозиториями и правилами бронирования.
    ///
    /// @param baseRepository репозиторий баз
    /// @param roomRepository репозиторий залов
    /// @param bookingRepository репозиторий бронирований
    /// @param rules правила бронирования
    public BookingMessageProcessingService(BaseRepository baseRepository, RoomRepository roomRepository,
                                           BookingRepository bookingRepository, BookingRules rules) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.rules = rules;
    }

    /// Обрабатывает входящее сообщение или callback и отправляет ответ через указанного бота.
    ///
    /// @param msg входящее сообщение
    /// @param chatId идентификатор чата пользователя
    /// @param bot бот для отправки ответов
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
    /// @param chatId идентификатор чата пользователя
    private void handleText(String rawText, long chatId) {
        long userId = chatId;
        String text = rawText.trim();
        Flow flow = flow(userId);

        if (text.equals("/start") || text.equals("/menu")) {
            flow.step = Step.MENU;
            flow.clearBooking();
            showMainMenu(chatId);
        } else if (flow.step != Step.MENU) {
            if (text.equals(BACK)) {
                back(chatId, flow);
            } else {
                switch (flow.step) {
                    case BOOK_NAME:
                        if (text.isEmpty()) {
                            send(chatId, ENTER_NAME, backKeyboard());
                        } else {
                            flow.name = text;
                            flow.step = Step.BOOK_PHONE;
                            send(chatId, ENTER_PHONE, backKeyboard());
                        }
                        break;
                    case BOOK_PHONE:
                        Optional<String> normalizedPhone = phoneNormalizer.normalize(text);
                        if (normalizedPhone.isEmpty()) {
                            send(chatId, "Не удалось распознать номер. Введите российский номер, например\n"
                                    + "+7 999 123-45-67 или 8 999 123-45-67.", backKeyboard());
                        } else {
                            flow.phone = normalizedPhone.get();
                            askBookingComment(chatId, flow);
                        }
                        break;
                    case BOOK_COMMENT:
                        Room selectedRoom = roomRepository.findById(flow.roomId).orElse(null);
                        if (text.isEmpty()
                                || (selectedRoom != null && selectedRoom.commentRequired() && "-".equals(text))) {
                            send(chatId, "Комментарий не может быть пустым. Опишите, что будете делать.",
                                    commentKeyboard());
                        } else {
                            flow.comment = text;
                            showBookingConfirmation(chatId, flow);
                        }
                        break;
                    default:
                        showMainMenu(chatId);
                }
            }
        }
    }

    /// Разбирает callback-данные кнопки и передаёт действие соответствующему обработчику.
    ///
    /// @param data callback-данные нажатой кнопки
    /// @param chatId идентификатор чата пользователя
    private void handleCallback(String data, long chatId) {
        long userId = chatId;
        Flow flow = flow(userId);
        String[] parts = data.split(":");
        String action = parts[0];

        if (CALLBACK_BACK.equals(action)) {
            back(chatId, flow);
        } else if ("menu".equals(action)) {
            handleMenuAction(chatId, flow, parts[1]);
        } else if (CALLBACK_BOOK.equals(action)) {
            handleBookingCallback(chatId, userId, flow, parts);
        } else if (CALLBACK_ABOUT.equals(action)) {
            handleAboutCallback(chatId, flow, parts);
        }
    }

    /// Выполняет выбранное действие главного меню.
    ///
    /// @param chatId id чата пользователя
    /// @param flow   состояние диалога пользователя
    /// @param item   код пункта меню
    private void handleMenuAction(long chatId, Flow flow, String item) {
        switch (item) {
            case CALLBACK_BOOK:
                beginBooking(chatId, flow);
                break;
            case "mine":
                showMyBookings(chatId, chatId);
                break;
            case CALLBACK_ABOUT:
                showAboutBases(chatId);
                break;
            default:
                flow.step = Step.MENU;
                flow.clearBooking();
                showMainMenu(chatId);
        }
    }

    /// Обрабатывает callback-действия сценария бронирования.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param userId идентификатор пользователя, которому принадлежит бронь
    /// @param flow состояние диалога пользователя
    /// @param parts части callback-данных
    private void handleBookingCallback(long chatId, long userId, Flow flow, String[] parts) {
        if (CALLBACK_BASE.equals(parts[1])) {
            flow.clearBooking();
            flow.baseId = Long.parseLong(parts[2]);
            showRoomChoices(chatId, flow);
        } else if ("room".equals(parts[1])) {
            flow.roomId = Long.parseLong(parts[2]);
            flow.monthOffset = 0;
            showMonthChoices(chatId, flow);
        } else if ("months".equals(parts[1])) {
            flow.monthOffset = Integer.parseInt(parts[2]);
            showMonthChoices(chatId, flow);
        } else if ("month".equals(parts[1])) {
            YearMonth month = YearMonth.parse(parts[2]);
            flow.selectedMonth = month;
            showWeekChoices(chatId, flow, month);
        } else if ("week".equals(parts[1])) {
            flow.bookingDate = LocalDate.parse(parts[2]);
            flow.selectedWeek = flow.bookingDate;
            showDayChoices(chatId, flow);
        } else if ("day".equals(parts[1])) {
            flow.bookingDate = LocalDate.parse(parts[2]);
            showTimeChoices(chatId, flow);
        } else if ("time".equals(parts[1])) {
            flow.bookingDate = LocalDate.parse(parts[2]);
            flow.startHour = Integer.parseInt(parts[START_HOUR_PART]);
            showDurationChoices(chatId, flow);
        } else if ("hours".equals(parts[1])) {
            flow.hours = Integer.parseInt(parts[2]);
            flow.step = Step.BOOK_NAME;
            send(chatId, ENTER_NAME, backKeyboard());
        } else if ("skip".equals(parts[1]) && flow.step == Step.BOOK_COMMENT) {
            skipBookingComment(chatId, flow);
        } else if ("confirm".equals(parts[1]) && flow.step == Step.BOOK_CONFIRM) {
            confirmBooking(chatId, userId, flow);
        } else if ("cancel".equals(parts[1])) {
            flow.step = Step.MENU;
            flow.clearBooking();
            showMainMenu(chatId);
        }
    }

    /// Пропускает ввод комментария, если для выбранного зала он необязателен.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    private void skipBookingComment(long chatId, Flow flow) {
        Room room = roomRepository.findById(flow.roomId).orElse(null);
        if (room != null && !room.commentRequired()) {
            flow.comment = "";
            showBookingConfirmation(chatId, flow);
        }
    }

    /// Проверяет доступность выбранного времени и сохраняет бронирование.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param userId идентификатор пользователя, которому принадлежит бронь
    /// @param flow состояние диалога пользователя
    private void confirmBooking(long chatId, long userId, Flow flow) {
        LocalDateTime start = flow.bookingDate.atTime(flow.startHour, 0);
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        boolean created = base != null && rules.canBook(base, start, flow.hours)
                && bookingRepository.addIfFree(new Booking(flow.roomId, userId, start, flow.hours,
                flow.name, flow.phone, flow.comment == null ? "" : flow.comment));
        if (created) {
            flow.clearBooking();
            flow.step = Step.MENU;
            sendMainMenu(chatId, "Готово, запись создана.");
        } else {
            flow.clearBooking();
            flow.step = Step.MENU;
            send(chatId, "Это время больше недоступно. Выберите другое время.", choices(
                    button("Выбрать время", MENU_BOOK),
                    button("Главное меню", MENU_HOME)));
        }
    }

    /// Обрабатывает callback-действия раздела с информацией о базах.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    /// @param parts части callback-данных
    private void handleAboutCallback(long chatId, Flow flow, String[] parts) {
        if (CALLBACK_BASE.equals(parts[1])) {
            showBaseInformation(chatId, Long.parseLong(parts[2]));
        } else if ("list".equals(parts[1])) {
            showAboutBases(chatId);
        }
    }

    /// Начинает сценарий бронирования и показывает доступные базы.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    private void beginBooking(long chatId, Flow flow) {
        flow.clearBooking();
        flow.step = Step.BOOK_BASES;
        List<Base> bases = baseRepository.findAll();
        if (bases.isEmpty()) {
            send(chatId, "Пока нет доступных баз для бронирования.", choices(button(BACK, MENU_HOME)));
            return;
        }
        List<List<Button>> rows = new ArrayList<List<Button>>();
        for (Base base : bases) {
            addButton(rows, button(base.name(), BOOK_BASE + base.id()), 2);
        }
        rows.add(row(button(BACK, MENU_HOME)));
        send(chatId, SELECT_BASE, markup(rows));
    }

    /// Показывает залы выбранной базы.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    private void showRoomChoices(long chatId, Flow flow) {
        flow.step = Step.BOOK_ROOMS;
        List<Room> rooms = roomRepository.findByBaseId(flow.baseId);
        List<List<Button>> rows = new ArrayList<List<Button>>();
        for (Room room : rooms) {
            addButton(rows, button(room.name(), "book:room:" + room.id()), 2);
        }
        rows.add(row(button(BACK, MENU_BOOK)));
        if (rooms.isEmpty()) {
            send(chatId, "В этой базе пока нет доступных залов.", markup(rows));
        } else {
            send(chatId, "Выберите зал:", markup(rows));
        }
    }

    /// Показывает месяцы, в которых есть доступное время для бронирования.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    private void showMonthChoices(long chatId, Flow flow) {
        flow.step = Step.BOOK_MONTH;
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        if (base == null) {
            beginBooking(chatId, flow);
            return;
        }
        YearMonth current = YearMonth.from(rules.today(base));
        List<List<Button>> rows = new ArrayList<List<Button>>();
        int count = 0;
        for (int offset = 0; offset < MONTHS_PER_PAGE; offset++) {
            YearMonth month = current.plusMonths(flow.monthOffset + offset);
            if (monthHasAvailability(base, flow.roomId, month)) {
                addButton(rows, button(formatter.monthYear(month), BOOK_MONTH + month), 2);
                count++;
            }
        }
        List<Button> navigation = new ArrayList<Button>();
        if (flow.monthOffset > 0) {
            navigation.add(button("Предыдущие", BOOK_MONTHS
                    + Math.max(0, flow.monthOffset - MONTHS_PER_PAGE)));
        }
        navigation.add(button("Следующие", BOOK_MONTHS + (flow.monthOffset + MONTHS_PER_PAGE)));
        rows.add(navigation);
        rows.add(row(button(BACK, BOOK_BASE + base.id())));
        if (count == 0) {
            send(chatId, "В этих месяцах нет доступных дат.", markup(rows));
        } else {
            send(chatId, "Выберите месяц:", markup(rows));
        }
    }

    /// Показывает недели выбранного месяца с доступными днями.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    /// @param month выбранный месяц
    private void showWeekChoices(long chatId, Flow flow, YearMonth month) {
        flow.step = Step.BOOK_WEEK;
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        LocalDate cursor = month.atDay(1);
        int count = 0;
        List<List<Button>> rows = new ArrayList<List<Button>>();
        while (cursor.getMonthValue() == month.getMonthValue()) {
            LocalDate end = cursor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            if (end.isAfter(month.atEndOfMonth())) {
                end = month.atEndOfMonth();
            }
            if (rangeHasAvailability(base, flow.roomId, cursor, end)) {
                String label = formatter.weekRange(cursor, end);
                addButton(rows, button(label, BOOK_WEEK + cursor), 2);
                count++;
            }
            cursor = end.plusDays(1);
        }
        rows.add(row(button(BACK, BOOK_MONTHS + flow.monthOffset)));
        if (count == 0) {
            send(chatId, NO_DAYS, markup(rows));
        } else {
            send(chatId, "Выберите неделю:", markup(rows));
        }
    }

    /// Показывает доступные для бронирования дни выбранной недели.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    private void showDayChoices(long chatId, Flow flow) {
        flow.step = Step.BOOK_DAY;
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        LocalDate start = flow.selectedWeek;
        LocalDate end = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        if (end.getMonthValue() != start.getMonthValue()) {
            end = start.withDayOfMonth(start.lengthOfMonth());
        }
        List<List<Button>> rows = new ArrayList<List<Button>>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (dateHasAvailability(base, flow.roomId, date)) {
                String label = formatter.dayAndDate(date);
                addButton(rows, button(label, BOOK_DAY + date), 2);
            }
        }
        rows.add(row(button(BACK, BOOK_MONTH + flow.selectedMonth)));
        send(chatId, rows.size() == 1 ? NO_DAYS : "Выберите день:", markup(rows));
    }

    /// Показывает свободное время для выбранного дня.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    private void showTimeChoices(long chatId, Flow flow) {
        flow.step = Step.BOOK_TIME;
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        Room room = roomRepository.findById(flow.roomId).orElse(null);
        List<List<Button>> rows = new ArrayList<List<Button>>();
        if (base != null && room != null) {
            for (int hour : availableStarts(base, room, flow.bookingDate)) {
                addButton(rows, button(formatter.hour(hour), "book:time:" + flow.bookingDate + ":" + hour),
                        TIME_BUTTONS_PER_ROW);
            }
        }
        rows.add(row(button(BACK, BOOK_WEEK + flow.selectedWeek)));
        send(chatId, rows.size() == 1 ? "На этот день свободного времени нет." : "Выберите время начала:",
                markup(rows));
    }

    /// Показывает доступную длительность бронирования для выбранного времени начала.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    private void showDurationChoices(long chatId, Flow flow) {
        flow.step = Step.BOOK_DURATION;
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        OpeningHours hours = base == null ? null : base.hoursOn(flow.bookingDate.getDayOfWeek()).orElse(null);
        List<List<Button>> rows = new ArrayList<List<Button>>();
        if (hours != null) {
            LocalDateTime start = flow.bookingDate.atTime(flow.startHour, 0);
            for (int duration = 1; duration <= hours.closeHour() - flow.startHour; duration++) {
                if (!bookingRepository.hasConflict(flow.roomId, start, start.plusHours(duration))) {
                    addButton(rows, button(String.valueOf(duration), "book:hours:" + duration), MONTHS_PER_PAGE);
                }
            }
        }
        rows.add(row(button(BACK, BOOK_DAY + flow.bookingDate)));
        send(chatId, rows.size() == 1
                ? "До закрытия или свободного времени зала осталось меньше часа."
                : "Сколько часов?", markup(rows));
    }

    /// Запрашивает комментарий к бронированию с учётом требований выбранного зала.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    private void askBookingComment(long chatId, Flow flow) {
        Room room = roomRepository.findById(flow.roomId).orElse(null);
        if (room == null) {
            beginBooking(chatId, flow);
            return;
        }
        flow.step = Step.BOOK_COMMENT;
        String prompt = room.commentRequired()
                ? "Опишите, что будете делать в зале. Например, запись барабанов и баса."
                : "Оставьте комментарий к записи (необязательно). Например, что нужно подключить.";
        send(chatId, prompt, room.commentRequired() ? backKeyboard() : commentKeyboard());
    }

    /// Показывает пользователю сведения о бронировании перед подтверждением.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    private void showBookingConfirmation(long chatId, Flow flow) {
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        Room room = roomRepository.findById(flow.roomId).orElse(null);
        if (base == null || room == null
                || (room.commentRequired() && (flow.comment == null || flow.comment.isEmpty()))) {
            send(chatId, "В этом зале комментарий обязателен. Опишите, что будете делать.", backKeyboard());
            flow.step = Step.BOOK_COMMENT;
            return;
        }
        LocalDateTime start = flow.bookingDate.atTime(flow.startHour, 0);
        LocalDateTime end = start.plusHours(flow.hours);
        flow.step = Step.BOOK_CONFIRM;
        String text = "Проверьте:\nБаза «" + base.name() + "», " + room.name() + ", "
                + formatter.dateTime(start) + "-" + formatter.endTime(start, end) + ".\n"
                + "Имя: " + flow.name + "\nТелефон: " + flow.phone + "\nКомментарий: "
                + (flow.comment == null || flow.comment.isEmpty() ? "—" : flow.comment);
        send(chatId, text, choices(button("Подтвердить", "book:confirm"),
                button(BACK, CALLBACK_BACK), button("Отмена", "book:cancel")));
    }

    /// Собирает будущие бронирования пользователя и показывает их в сообщении.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param userId идентификатор пользователя
    private void showMyBookings(long chatId, long userId) {
        StringBuilder text = new StringBuilder("Ваши будущие записи:\n");
        boolean hasBookings = false;
        for (Booking booking : bookingRepository.findByUser(userId)) {
            Room room = roomRepository.findById(booking.roomId()).orElse(null);
            Base base = room == null ? null : baseRepository.findById(room.baseId()).orElse(null);
            if (base == null || !rules.isUpcoming(base, booking)) {
                continue;
            }
            hasBookings = true;
            text.append("• ").append(base.name()).append(", ").append(room.name()).append(", ")
                    .append(formatter.dateTime(booking.start())).append("-")
                    .append(formatter.endTime(booking.start(), booking.end())).append("\n");
        }
        sendMainMenu(chatId, hasBookings ? text.toString() : "У вас пока нет будущих записей.");
    }

    /// Показывает список баз для просмотра информации о них.
    ///
    /// @param chatId идентификатор чата пользователя
    private void showAboutBases(long chatId) {
        List<Base> bases = baseRepository.findAll();
        if (bases.isEmpty()) {
            send(chatId, "Пока нет доступных баз.", choices(button(BACK, MENU_HOME)));
            return;
        }
        List<List<Button>> rows = new ArrayList<List<Button>>();
        for (Base base : bases) {
            addButton(rows, button(base.name(), "about:base:" + base.id()), 2);
        }
        rows.add(row(button(BACK, MENU_HOME)));
        send(chatId, SELECT_BASE, markup(rows));
    }

    /// Показывает адрес, расписание и оборудование выбранной базы.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param baseId идентификатор базы
    private void showBaseInformation(long chatId, long baseId) {
        Base base = baseRepository.findById(baseId).orElse(null);
        if (base == null) {
            showAboutBases(chatId);
            return;
        }
        StringBuilder text = new StringBuilder("База «").append(base.name()).append("»\nАдрес: ")
                .append(base.address()).append("\nЧасовой пояс: ").append(base.timezone()).append("\n");
        for (int day = 1; day <= DAYS_IN_WEEK; day++) {
            OpeningHours hours = base.hoursOn(DayOfWeek.of(day)).orElse(null);
            text.append(formatter.dayName(DayOfWeek.of(day))).append(": ")
                    .append(hours == null
                            ? "выходной"
                            : formatter.hour(hours.openHour()) + "-" + formatter.hour(hours.closeHour()))
                    .append(day == DAYS_IN_WEEK ? "\n" : ", ");
        }
        text.append("Оборудование:\n").append(base.equipment().isEmpty() ? "не указано" : base.equipment());
        send(chatId, text.toString(), choices(button(BACK, "about:list")));
    }

    /// Проверяет, есть ли свободное время в выбранном месяце.
    ///
    /// @param base база, в которой выполняется поиск
    /// @param roomId идентификатор зала
    /// @param month месяц для проверки
    /// @return true, если в месяце есть доступное время
    private boolean monthHasAvailability(Base base, long roomId, YearMonth month) {
        return rangeHasAvailability(base, roomId, month.atDay(1), month.atEndOfMonth());
    }

    /// Проверяет доступность бронирования хотя бы на один день заданного периода.
    ///
    /// @param base база, в которой выполняется поиск
    /// @param roomId идентификатор зала
    /// @param start первый день периода
    /// @param end последний день периода
    /// @return true, если в периоде есть доступное время
    private boolean rangeHasAvailability(Base base, long roomId, LocalDate start, LocalDate end) {
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (dateHasAvailability(base, roomId, date)) {
                return true;
            }
        }
        return false;
    }

    /// Проверяет, доступен ли хотя бы один час в указанном зале и дне.
    ///
    /// @param base база, в которой находится зал
    /// @param roomId идентификатор зала
    /// @param date проверяемая дата
    /// @return true, если в этот день можно начать бронирование
    private boolean dateHasAvailability(Base base, long roomId, LocalDate date) {
        Room room = roomRepository.findById(roomId).orElse(null);
        return room != null && !availableStarts(base, room, date).isEmpty();
    }

    /// Возвращает свободные часы начала бронирования в заданном зале и дне.
    ///
    /// @param base база, в которой находится зал
    /// @param room зал для проверки
    /// @param date дата бронирования
    /// @return список доступных часов начала
    private List<Integer> availableStarts(Base base, Room room, LocalDate date) {
        OpeningHours hours = base.hoursOn(date.getDayOfWeek()).orElse(null);
        if (hours == null) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<Integer>();
        ZonedDateTime earliest = rules.earliestStart(base);
        for (int hour = hours.openHour(); hour < hours.closeHour(); hour++) {
            LocalDateTime start = date.atTime(hour, 0);
            if (start.atZone(ZoneId.of(base.timezone())).isBefore(earliest)) {
                continue;
            }
            if (!bookingRepository.hasConflict(room.id(), start, start.plusHours(1))) {
                result.add(hour);
            }
        }
        return result;
    }

    /// Возвращает диалог на предыдущий шаг или в главное меню.
    ///
    /// @param chatId идентификатор чата пользователя
    /// @param flow состояние диалога пользователя
    private void back(long chatId, Flow flow) {
        switch (flow.step) {
            case BOOK_BASES:
            case MENU:
                flow.clearBooking();
                flow.step = Step.MENU;
                showMainMenu(chatId);
                break;
            case BOOK_ROOMS:
                beginBooking(chatId, flow);
                break;
            case BOOK_MONTH:
                showRoomChoices(chatId, flow);
                break;
            case BOOK_WEEK:
                showMonthChoices(chatId, flow);
                break;
            case BOOK_DAY:
                showWeekChoices(chatId, flow, flow.selectedMonth);
                break;
            case BOOK_TIME:
                showDayChoices(chatId, flow);
                break;
            case BOOK_DURATION:
                flow.hours = 0;
                showTimeChoices(chatId, flow);
                break;
            case BOOK_NAME:
                flow.name = null;
                flow.phone = null;
                flow.comment = null;
                showDurationChoices(chatId, flow);
                break;
            case BOOK_PHONE:
                flow.name = null;
                flow.phone = null;
                flow.comment = null;
                flow.step = Step.BOOK_NAME;
                send(chatId, ENTER_NAME, backKeyboard());
                break;
            case BOOK_COMMENT:
                flow.phone = null;
                flow.comment = null;
                flow.step = Step.BOOK_PHONE;
                send(chatId, ENTER_PHONE, backKeyboard());
                break;
            case BOOK_CONFIRM:
                flow.comment = null;
                askBookingComment(chatId, flow);
                break;
            default:
                flow.step = Step.MENU;
                showMainMenu(chatId);
        }
    }

    /// Показывает главное меню бота.
    ///
    /// @param chatId идентификатор чата пользователя
    private void showMainMenu(long chatId) {
        List<List<Button>> rows = new ArrayList<List<Button>>();
        rows.add(row(button("Забронировать", MENU_BOOK), button("Мои записи", "menu:mine")));
        rows.add(row(button("О базе", "menu:about")));
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
    private Flow flow(long userId) {
        return flows.computeIfAbsent(userId, id -> new Flow());
    }

    /// Отправляет текстовое сообщение с клавиатурой.
    ///
    /// @param chatId идентификатор чата получателя
    /// @param text текст сообщения
    /// @param keyboard строки кнопок; null означает сообщение без клавиатуры
    private void send(long chatId, String text, List<List<Button>> keyboard) {
        List<List<Button>> safeKeyboard = keyboard == null ? List.of() : keyboard;
        transport.sendMessage(Message.builder().text(text).keyboard(safeKeyboard).build(), chatId);
    }

    /// Создаёт клавиатуру, размещая каждую кнопку в отдельной строке.
    ///
    /// @param buttons кнопки клавиатуры
    /// @return строки с одной кнопкой в каждой
    private List<List<Button>> choices(Button... buttons) {
        List<List<Button>> rows = new ArrayList<List<Button>>();
        for (Button button : buttons) {
            rows.add(row(button));
        }
        return markup(rows);
    }

    /// Создаёт клавиатуру комментария с вариантами пропуска и возврата.
    ///
    /// @return клавиатура комментария
    private List<List<Button>> commentKeyboard() {
        return choices(button("Пропустить", "book:skip"), button(BACK, CALLBACK_BACK));
    }

    /// Создаёт клавиатуру с кнопкой возврата.
    ///
    /// @return клавиатура возврата
    private List<List<Button>> backKeyboard() {
        return choices(button(BACK, CALLBACK_BACK));
    }

    /// Создаёт кнопку с подписью и callback-данными.
    ///
    /// @param text подпись кнопки
    /// @param callback callback-данные кнопки
    /// @return созданная кнопка
    private Button button(String text, String callback) {
        return new Button(text, callback);
    }

    /// Создаёт строку клавиатуры из переданных кнопок.
    ///
    /// @param buttons кнопки строки
    /// @return строка клавиатуры
    private List<Button> row(Button... buttons) {
        return new ArrayList<Button>(Arrays.asList(buttons));
    }

    /// Добавляет кнопку в последнюю строку или создаёт для неё новую.
    ///
    /// @param rows строки клавиатуры
    /// @param button добавляемая кнопка
    /// @param size максимальное число кнопок в строке
    private void addButton(List<List<Button>> rows, Button button, int size) {
        if (rows.isEmpty() || rows.get(rows.size() - 1).size() >= size) {
            rows.add(new ArrayList<Button>());
        }
        rows.get(rows.size() - 1).add(button);
    }

    /// Возвращает строки кнопок в виде клавиатуры.
    ///
    /// @param rows строки кнопок
    /// @return клавиатура для сообщения
    private List<List<Button>> markup(List<List<Button>> rows) {
        return rows;
    }

    /// Шаги диалога бронирования.
    private enum Step {
        MENU, BOOK_BASES, BOOK_ROOMS, BOOK_MONTH, BOOK_WEEK, BOOK_DAY, BOOK_TIME, BOOK_DURATION,
        BOOK_NAME, BOOK_PHONE, BOOK_COMMENT, BOOK_CONFIRM
    }

    /// Данные текущего сценария бронирования одного пользователя.
    private static final class Flow {
        /// Текущий шаг диалога.
        Step step = Step.MENU;
        /// Идентификатор выбранной базы.
        long baseId;
        /// Идентификатор выбранного зала.
        long roomId;
        /// Имя клиента.
        String name;
        /// Нормализованный номер телефона клиента.
        String phone;
        /// Комментарий к бронированию.
        String comment;
        /// Выбранный месяц.
        YearMonth selectedMonth;
        /// Первый день выбранной недели.
        LocalDate selectedWeek;
        /// Выбранная дата бронирования.
        LocalDate bookingDate;
        /// Час начала бронирования.
        int startHour;
        /// Длительность бронирования в часах.
        int hours;
        /// Смещение страницы списка месяцев.
        int monthOffset;

        /// Создаёт состояние диалога, установленное на главное меню.
        Flow() {}

        /// Сбрасывает параметры бронирования, сохраняя текущий шаг диалога.
        void clearBooking() {
            baseId = 0;
            roomId = 0;
            bookingDate = null;
            startHour = 0;
            hours = 0;
            name = null;
            phone = null;
            comment = null;
            selectedMonth = null;
            selectedWeek = null;
            monthOffset = 0;
        }
    }
}

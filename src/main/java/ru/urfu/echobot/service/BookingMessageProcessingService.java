package ru.urfu.echobot.service;

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

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BookingMessageProcessingService implements MessageProcessingService {
    private static final String BACK = "Назад";
    private static final Logger LOGGER = LoggerFactory.getLogger(BookingMessageProcessingService.class);

    private enum Step {
        MENU, BOOK_BASES, BOOK_ROOMS, BOOK_MONTH, BOOK_WEEK, BOOK_DAY, BOOK_TIME, BOOK_DURATION,
        BOOK_NAME, BOOK_PHONE, BOOK_COMMENT, BOOK_CONFIRM
    }

    private static final class Flow {
        Step step = Step.MENU;
        long baseId;
        long roomId;
        String name;
        String phone;
        String comment;
        YearMonth selectedMonth;
        LocalDate selectedWeek;
        LocalDate bookingDate;
        int startHour;
        int hours;
        int monthOffset;

        Flow() {
        }

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

    private final BaseRepository baseRepository;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final BookingRules rules;
    private volatile Bot transport;
    private final Map<Long, Flow> flows = new ConcurrentHashMap<Long, Flow>();
    private final PhoneNormalizer phoneNormalizer = new PhoneNormalizer();
    private final RussianDateFormatter formatter = new RussianDateFormatter();

    public BookingMessageProcessingService(BaseRepository baseRepository, RoomRepository roomRepository,
                                           BookingRepository bookingRepository, BookingRules rules) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.rules = rules;
    }

    @Override
    public void processMessage(@NotNull Message msg, long chatId, @NotNull Bot bot) {
        transport = bot;
        if (msg.buttonData() != null) {
            handleCallback(msg.buttonData(), chatId);
        } else if (msg.text() != null) {
            handleText(msg.text(), chatId);
        }
    }

    private void handleText(String rawText, long chatId) {
        long userId = chatId;
        String text = rawText.trim();
        Flow flow = flow(userId);

        if (text.equals("/start") || text.equals("/menu")) {
            flow.step = Step.MENU;
            flow.clearBooking();
            showMainMenu(chatId);
            return;
        }
        if (flow.step == Step.MENU) {
            return;
        }
        if (text.equals(BACK)) {
            back(chatId, flow);
            return;
        }

        switch (flow.step) {
            case BOOK_NAME:
                if (text.isEmpty()) {
                    send(chatId, "Введите имя для связи.", backKeyboard());
                    return;
                }
                flow.name = text;
                flow.step = Step.BOOK_PHONE;
                send(chatId, "Введите номер телефона для связи.", backKeyboard());
                break;
            case BOOK_PHONE:
                Optional<String> normalizedPhone = phoneNormalizer.normalize(text);
                if (normalizedPhone.isEmpty()) {
                    send(chatId, "Не удалось распознать номер. Введите российский номер, например\n" +
                            "+7 999 123-45-67 или 8 999 123-45-67.", backKeyboard());
                    return;
                }
                flow.phone = normalizedPhone.get();
                askBookingComment(chatId, flow);
                break;
            case BOOK_COMMENT:
                Room selectedRoom = roomRepository.findById(flow.roomId).orElse(null);
                if (text.isEmpty() || (selectedRoom != null && selectedRoom.commentRequired() && "-".equals(text))) {
                    send(chatId, "Комментарий не может быть пустым. Опишите, что будете делать.", commentKeyboard());
                    return;
                }
                flow.comment = text;
                showBookingConfirmation(chatId, flow);
                break;
            default:
                showMainMenu(chatId);
        }
    }

    private void handleCallback(String data, long chatId) {
        long userId = chatId;
        Flow flow = flow(userId);
        String[] parts = data.split(":");
        String action = parts[0];

        if ("back".equals(action)) {
            back(chatId, flow);
            return;
        }
        if ("menu".equals(action)) {
            handleMenuAction(chatId, flow, parts[1]);
            return;
        }
        if ("book".equals(action)) {
            handleBookingCallback(chatId, userId, flow, parts);
            return;
        }
        if ("about".equals(action)) {
            handleAboutCallback(chatId, flow, parts);
        }
    }

    /// Обрабатывает нажатие кнопки главного меню.
    ///
    /// @param chatId id чата пользователя
    /// @param flow   состояние диалога пользователя
    /// @param item   код пункта меню
    private void handleMenuAction(long chatId, Flow flow, String item) {
        switch (item) {
            case "book":
                beginBooking(chatId, flow);
                break;
            case "mine":
                showMyBookings(chatId, chatId);
                break;
            case "about":
                showAboutBases(chatId);
                break;
            default:
                flow.step = Step.MENU;
                flow.clearBooking();
                showMainMenu(chatId);
        }
    }

    private void handleBookingCallback(long chatId, long userId, Flow flow, String[] parts) {
        if ("base".equals(parts[1])) {
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
            flow.startHour = Integer.parseInt(parts[3]);
            showDurationChoices(chatId, flow);
        } else if ("hours".equals(parts[1])) {
            flow.hours = Integer.parseInt(parts[2]);
            flow.step = Step.BOOK_NAME;
            send(chatId, "Введите имя для связи.", backKeyboard());
        } else if ("skip".equals(parts[1]) && flow.step == Step.BOOK_COMMENT) {
            Room room = roomRepository.findById(flow.roomId).orElse(null);
            if (room != null && !room.commentRequired()) {
                flow.comment = "";
                showBookingConfirmation(chatId, flow);
            }
        } else if ("confirm".equals(parts[1]) && flow.step == Step.BOOK_CONFIRM) {
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
                        button("Выбрать время", "menu:book"),
                        button("Главное меню", "menu:home")));
            }
        } else if ("cancel".equals(parts[1])) {
            flow.step = Step.MENU;
            flow.clearBooking();
            showMainMenu(chatId);
        }
    }

    private void handleAboutCallback(long chatId, Flow flow, String[] parts) {
        if ("base".equals(parts[1])) {
            showBaseInformation(chatId, Long.parseLong(parts[2]));
        } else if ("list".equals(parts[1])) {
            showAboutBases(chatId);
        }
    }

    private void beginBooking(long chatId, Flow flow) {
        flow.clearBooking();
        flow.step = Step.BOOK_BASES;
        List<Base> bases = baseRepository.findAll();
        if (bases.isEmpty()) {
            send(chatId, "Пока нет доступных баз для бронирования.", choices(button(BACK, "menu:home")));
            return;
        }
        List<List<Button>> rows = new ArrayList<List<Button>>();
        for (Base base : bases) {
            addButton(rows, button(base.name(), "book:base:" + base.id()), 2);
        }
        rows.add(row(button(BACK, "menu:home")));
        send(chatId, "Выберите базу:", markup(rows));
    }

    private void showRoomChoices(long chatId, Flow flow) {
        flow.step = Step.BOOK_ROOMS;
        List<Room> rooms = roomRepository.findByBaseId(flow.baseId);
        List<List<Button>> rows = new ArrayList<List<Button>>();
        for (Room room : rooms) {
            addButton(rows, button(room.name(), "book:room:" + room.id()), 2);
        }
        rows.add(row(button(BACK, "menu:book")));
        if (rooms.isEmpty()) {
            send(chatId, "В этой базе пока нет доступных залов.", markup(rows));
        } else {
            send(chatId, "Выберите зал:", markup(rows));
        }
    }

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
        for (int offset = 0; offset < 6; offset++) {
            YearMonth month = current.plusMonths(flow.monthOffset + offset);
            if (monthHasAvailability(base, flow.roomId, month)) {
                addButton(rows, button(formatter.monthYear(month), "book:month:" + month), 2);
                count++;
            }
        }
        List<Button> navigation = new ArrayList<Button>();
        if (flow.monthOffset > 0) {
            navigation.add(button("Предыдущие", "book:months:" + Math.max(0, flow.monthOffset - 6)));
        }
        navigation.add(button("Следующие", "book:months:" + (flow.monthOffset + 6)));
        rows.add(navigation);
        rows.add(row(button(BACK, "book:base:" + base.id())));
        if (count == 0) {
            send(chatId, "В этих месяцах нет доступных дат.", markup(rows));
        } else {
            send(chatId, "Выберите месяц:", markup(rows));
        }
    }

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
                addButton(rows, button(label, "book:week:" + cursor), 2);
                count++;
            }
            cursor = end.plusDays(1);
        }
        rows.add(row(button(BACK, "book:months:" + flow.monthOffset)));
        if (count == 0) {
            send(chatId, "В этой неделе нет доступных дней.", markup(rows));
        } else {
            send(chatId, "Выберите неделю:", markup(rows));
        }
    }

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
                addButton(rows, button(label, "book:day:" + date), 2);
            }
        }
        rows.add(row(button(BACK, "book:month:" + flow.selectedMonth)));
        send(chatId, rows.size() == 1 ? "В этой неделе нет доступных дней." : "Выберите день:", markup(rows));
    }

    private void showTimeChoices(long chatId, Flow flow) {
        flow.step = Step.BOOK_TIME;
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        Room room = roomRepository.findById(flow.roomId).orElse(null);
        List<List<Button>> rows = new ArrayList<List<Button>>();
        if (base != null && room != null) {
            for (int hour : availableStarts(base, room, flow.bookingDate)) {
                addButton(rows, button(formatter.hour(hour), "book:time:" + flow.bookingDate + ":" + hour), 4);
            }
        }
        rows.add(row(button(BACK, "book:week:" + flow.selectedWeek)));
        send(chatId, rows.size() == 1 ? "На этот день свободного времени нет." : "Выберите время начала:", markup(rows));
    }

    private void showDurationChoices(long chatId, Flow flow) {
        flow.step = Step.BOOK_DURATION;
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        OpeningHours hours = base == null ? null : base.hoursOn(flow.bookingDate.getDayOfWeek()).orElse(null);
        List<List<Button>> rows = new ArrayList<List<Button>>();
        if (hours != null) {
            LocalDateTime start = flow.bookingDate.atTime(flow.startHour, 0);
            for (int duration = 1; duration <= hours.closeHour() - flow.startHour; duration++) {
                if (!bookingRepository.hasConflict(flow.roomId, start, start.plusHours(duration))) {
                    addButton(rows, button(String.valueOf(duration), "book:hours:" + duration), 6);
                }
            }
        }
        rows.add(row(button(BACK, "book:day:" + flow.bookingDate)));
        send(chatId, rows.size() == 1 ? "До закрытия или свободного времени зала осталось меньше часа." : "Сколько часов?", markup(rows));
    }

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

    private void showBookingConfirmation(long chatId, Flow flow) {
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        Room room = roomRepository.findById(flow.roomId).orElse(null);
        if (base == null || room == null || (room.commentRequired() && (flow.comment == null || flow.comment.isEmpty()))) {
            send(chatId, "В этом зале комментарий обязателен. Опишите, что будете делать.", backKeyboard());
            flow.step = Step.BOOK_COMMENT;
            return;
        }
        LocalDateTime start = flow.bookingDate.atTime(flow.startHour, 0);
        LocalDateTime end = start.plusHours(flow.hours);
        flow.step = Step.BOOK_CONFIRM;
        String text = "Проверьте:\nБаза «" + base.name() + "», " + room.name() + ", " +
                formatter.dateTime(start) + "-" + formatter.endTime(start, end) + ".\n" +
                "Имя: " + flow.name + "\nТелефон: " + flow.phone + "\nКомментарий: " +
                (flow.comment == null || flow.comment.isEmpty() ? "—" : flow.comment);
        send(chatId, text, choices(button("Подтвердить", "book:confirm"),
                button(BACK, "back"), button("Отмена", "book:cancel")));
    }

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

    private void showAboutBases(long chatId) {
        List<Base> bases = baseRepository.findAll();
        if (bases.isEmpty()) {
            send(chatId, "Пока нет доступных баз.", choices(button(BACK, "menu:home")));
            return;
        }
        List<List<Button>> rows = new ArrayList<List<Button>>();
        for (Base base : bases) {
            addButton(rows, button(base.name(), "about:base:" + base.id()), 2);
        }
        rows.add(row(button(BACK, "menu:home")));
        send(chatId, "Выберите базу:", markup(rows));
    }

    private void showBaseInformation(long chatId, long baseId) {
        Base base = baseRepository.findById(baseId).orElse(null);
        if (base == null) {
            showAboutBases(chatId);
            return;
        }
        StringBuilder text = new StringBuilder("База «").append(base.name()).append("»\nАдрес: ")
                .append(base.address()).append("\nЧасовой пояс: ").append(base.timezone()).append("\n");
        for (int day = 1; day <= 7; day++) {
            OpeningHours hours = base.hoursOn(DayOfWeek.of(day)).orElse(null);
            text.append(formatter.dayName(DayOfWeek.of(day))).append(": ")
                    .append(hours == null ? "выходной" : formatter.hour(hours.openHour()) + "-" + formatter.hour(hours.closeHour()))
                    .append(day == 7 ? "\n" : ", ");
        }
        text.append("Оборудование:\n").append(base.equipment().isEmpty() ? "не указано" : base.equipment());
        send(chatId, text.toString(), choices(button(BACK, "about:list")));
    }

    private boolean monthHasAvailability(Base base, long roomId, YearMonth month) {
        return rangeHasAvailability(base, roomId, month.atDay(1), month.atEndOfMonth());
    }

    private boolean rangeHasAvailability(Base base, long roomId, LocalDate start, LocalDate end) {
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (dateHasAvailability(base, roomId, date)) {
                return true;
            }
        }
        return false;
    }

    private boolean dateHasAvailability(Base base, long roomId, LocalDate date) {
        Room room = roomRepository.findById(roomId).orElse(null);
        return room != null && !availableStarts(base, room, date).isEmpty();
    }

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
                send(chatId, "Введите имя для связи.", backKeyboard());
                break;
            case BOOK_COMMENT:
                flow.phone = null;
                flow.comment = null;
                flow.step = Step.BOOK_PHONE;
                send(chatId, "Введите номер телефона для связи.", backKeyboard());
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

    private void showMainMenu(long chatId) {
        List<List<Button>> rows = new ArrayList<List<Button>>();
        rows.add(row(button("Забронировать", "menu:book"), button("Мои записи", "menu:mine")));
        rows.add(row(button("О базе", "menu:about")));
        send(chatId, "Здравствуйте! Что хотите сделать?", rows);
    }

    private void sendMainMenu(long chatId, String notice) {
        send(chatId, notice, null);
        showMainMenu(chatId);
    }

    private Flow flow(long userId) {
        return flows.computeIfAbsent(userId, id -> new Flow());
    }

    private void send(long chatId, String text, List<List<Button>> keyboard) {
        List<List<Button>> safeKeyboard = keyboard == null ? List.of() : keyboard;
        transport.sendMessage(Message.builder().text(text).keyboard(safeKeyboard).build(), chatId);
    }

    private List<List<Button>> choices(Button... buttons) {
        List<List<Button>> rows = new ArrayList<List<Button>>();
        for (Button button : buttons) {
            rows.add(row(button));
        }
        return markup(rows);
    }

    private List<List<Button>> commentKeyboard() {
        return choices(button("Пропустить", "book:skip"), button(BACK, "back"));
    }

    private List<List<Button>> backKeyboard() {
        return choices(button(BACK, "back"));
    }

    private Button button(String text, String callback) {
        return new Button(text, callback);
    }

    private List<Button> row(Button... buttons) {
        return new ArrayList<Button>(Arrays.asList(buttons));
    }

    private void addButton(List<List<Button>> rows, Button button, int size) {
        if (rows.isEmpty() || rows.get(rows.size() - 1).size() >= size) {
            rows.add(new ArrayList<Button>());
        }
        rows.get(rows.size() - 1).add(button);
    }

    private List<List<Button>> markup(List<List<Button>> rows) {
        return rows;
    }
}

package ru.urfu.echobot.service;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.urfu.echobot.bot.Bot;

public final class BookingMessageProcessingService implements MessageProcessingService {
    private static final Logger LOGGER = LogManager.getLogger(BookingMessageProcessingService.class);
    private static final Locale RU = new Locale("ru", "RU");
    private static final String BACK = "Назад";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", RU);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("LLLL yyyy", RU);
    private static final Pattern RUSSIAN_PHONE = Pattern.compile(
            "^(?:\\+7|8)[\\s()\\-]*(\\d{3})[\\s()\\-]*(\\d{3})[\\s()\\-]*(\\d{2})[\\s()\\-]*(\\d{2})$");
    private static final String[] DAY_NAMES = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};

    private enum Step {
        MENU, ADD_NAME, ADD_ADDRESS, ADD_TIMEZONE, ADD_OPEN, ADD_CLOSE, ADD_ROOM_NAME,
        ADD_ROOM_REQUIRED, ADD_ROOM_MORE, ADD_LEAD, ADD_EQUIPMENT,
        EDIT_NAME, EDIT_ADDRESS, EDIT_TIMEZONE, EDIT_EQUIPMENT, EDIT_LEAD,
        EDIT_ROOM_NAME, EDIT_ROOM_REQUIRED, EDIT_DAY_OPEN, EDIT_DAY_CLOSE,
        BOOK_BASES, BOOK_ROOMS, BOOK_MONTH, BOOK_WEEK, BOOK_DAY, BOOK_TIME, BOOK_DURATION,
        BOOK_NAME, BOOK_PHONE, BOOK_COMMENT, BOOK_CONFIRM
    }

    private static final class Flow {
        Step step = Step.MENU;
        final boolean owner;
        String baseName;
        String address;
        String timezone;
        String equipment;
        int leadHours = 2;
        int[][] schedule = new int[7][];
        int dayIndex;
        final List<String> roomNames = new ArrayList<String>();
        final List<Boolean> roomCommentRequired = new ArrayList<Boolean>();
        long baseId;
        long roomId;
        int editDay;
        int editOpen;
        String name;
        String phone;
        String comment;
        YearMonth selectedMonth;
        LocalDate selectedWeek;
        LocalDate bookingDate;
        int startHour;
        int hours;
        int monthOffset;
        boolean creatingBase;

        Flow(boolean owner) {
            this.owner = owner;
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

    private final long ownerId;
    private final Database database;
    private volatile Bot transport;
    private final Map<Long, Flow> flows = new ConcurrentHashMap<Long, Flow>();

    public BookingMessageProcessingService(long ownerId, Database database) {
        this.ownerId = ownerId;
        this.database = database;
    }

    @Override
    public void processUpdate(Update update, Bot bot) {
        transport = bot;
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            } else if (update.hasMessage() && update.getMessage().hasText()) {
                handleText(update.getMessage());
            }
        } catch (SQLException | TelegramApiException ex) {
            LOGGER.error("Ошибка при обработке обновления Telegram", ex);
            long chatId = chatId(update);
            if (chatId != 0) {
                try {
                    send(chatId, "Не удалось выполнить действие. Попробуйте ещё раз.", null);
                } catch (TelegramApiException sendException) {
                    LOGGER.error("Не удалось отправить сообщение об ошибке", sendException);
                }
            }
        }
    }

    private void handleText(org.telegram.telegrambots.meta.api.objects.Message message)
            throws SQLException, TelegramApiException {
        long userId = message.getFrom().getId();
        long chatId = message.getChatId();
        String text = message.getText().trim();
        Flow flow = flow(userId);

        if (text.equals("/start") || text.equals("/menu")) {
            flow.step = Step.MENU;
            flow.clearBooking();
            showMainMenu(chatId, flow.owner);
            return;
        }
        if (flow.step == Step.MENU) {
            if ("Базы".equals(text) && flow.owner) {
                showBases(chatId);
            } else if ("Забронировать".equals(text)) {
                beginBooking(chatId, flow);
            } else if ("Мои записи".equals(text)) {
                showMyBookings(chatId, userId);
            } else if ("О базе".equals(text)) {
                showAboutBases(chatId);
            }
            return;
        }
        if (text.equals(BACK)) {
            back(chatId, flow);
            return;
        }

        switch (flow.step) {
            case ADD_NAME:
                if (text.isEmpty()) {
                    send(chatId, "Введите название базы.", backKeyboard());
                    return;
                }
                flow.baseName = text;
                flow.step = Step.ADD_ADDRESS;
                send(chatId, "Введите адрес базы.", backKeyboard());
                break;
            case ADD_ADDRESS:
                if (text.isEmpty()) {
                    send(chatId, "Введите адрес базы.", backKeyboard());
                    return;
                }
                flow.address = text;
                flow.step = Step.ADD_TIMEZONE;
                send(chatId, "Введите часовой пояс базы. Например: Europe/Moscow.", backKeyboard());
                break;
            case ADD_TIMEZONE:
                if (!validZone(text)) {
                    send(chatId, "Не удалось распознать часовой пояс. Введите идентификатор, например Europe/Moscow.", backKeyboard());
                    return;
                }
                flow.timezone = text;
                flow.dayIndex = 0;
                askOpeningHour(chatId, flow);
                break;
            case ADD_ROOM_NAME:
                if (text.isEmpty()) {
                    send(chatId, "Название зала не должно быть пустым. Введите название зала.", backKeyboard());
                    return;
                }
                flow.roomNames.add(text);
                flow.step = Step.ADD_ROOM_REQUIRED;
                send(chatId, "Комментарий к записи в этом зале обязателен?", choices(
                        button("Да", "setup:required:yes"),
                        button("Нет", "setup:required:no"),
                        button(BACK, "back")));
                break;
            case ADD_EQUIPMENT:
                flow.equipment = text;
                saveNewBase(chatId, flow);
                break;
            case EDIT_NAME:
            case EDIT_ADDRESS:
            case EDIT_TIMEZONE:
            case EDIT_EQUIPMENT:
                saveEditedText(chatId, flow, text);
                break;
            case EDIT_ROOM_NAME:
                if (text.isEmpty()) {
                    send(chatId, "Название зала не должно быть пустым. Введите название зала.", backKeyboard());
                    return;
                }
                flow.baseName = text;
                flow.step = Step.EDIT_ROOM_REQUIRED;
                send(chatId, "Комментарий к записи в этом зале обязателен?", choices(
                        button("Да", "roomreq:yes"),
                        button("Нет", "roomreq:no"),
                        button(BACK, "back")));
                break;
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
                String normalizedPhone = normalizeRussianPhone(text);
                if (normalizedPhone == null) {
                    send(chatId, "Не удалось распознать номер. Введите российский номер, например\n" +
                            "+7 999 123-45-67 или 8 999 123-45-67.", backKeyboard());
                    return;
                }
                flow.phone = normalizedPhone;
                askBookingComment(chatId, flow);
                break;
            case BOOK_COMMENT:
                Database.Room selectedRoom = database.getRoom(flow.roomId);
                if (text.isEmpty() || (selectedRoom != null && selectedRoom.commentRequired && "-".equals(text))) {
                    send(chatId, "Комментарий не может быть пустым. Опишите, что будете делать.", commentKeyboard());
                    return;
                }
                flow.comment = text;
                showBookingConfirmation(chatId, flow);
                break;
            default:
                showMainMenu(chatId, flow.owner);
        }
    }

    private void handleCallback(CallbackQuery query) throws SQLException, TelegramApiException {
        answerCallback(query.getId());
        long userId = query.getFrom().getId();
        long chatId = query.getMessage().getChatId();
        Flow flow = flow(userId);
        String[] parts = query.getData().split(":");
        String action = parts[0];

        if ("back".equals(action)) {
            back(chatId, flow);
            return;
        }
        if ("menu".equals(action)) {
            if ("book".equals(parts[1])) {
                beginBooking(chatId, flow);
            } else {
                flow.step = Step.MENU;
                flow.clearBooking();
                showMainMenu(chatId, flow.owner);
            }
            return;
        }
        if ("setup".equals(action)) {
            if (!flow.owner) {
                send(chatId, "Эта команда доступна только владельцу.", null);
                return;
            }
            handleSetupCallback(chatId, flow, parts);
            return;
        }
        if ("owner".equals(action)) {
            if (!flow.owner) {
                send(chatId, "Эта команда доступна только владельцу.", null);
                return;
            }
            handleOwnerCallback(chatId, flow, parts);
            return;
        }
        if ("roomreq".equals(action)) {
            if (!flow.owner) {
                send(chatId, "Эта команда доступна только владельцу.", null);
                return;
            }
            boolean required = "yes".equals(parts[1]);
            if (flow.step != Step.EDIT_ROOM_REQUIRED) {
                return;
            }
            database.updateRoom(flow.roomId, flow.baseName, required);
            flow.step = Step.MENU;
            sendBaseSettings(chatId, flow.baseId, "Настройки зала сохранены.");
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

    private void handleSetupCallback(long chatId, Flow flow, String[] parts)
            throws SQLException, TelegramApiException {
        if ("open".equals(parts[1]) && flow.step == Step.ADD_OPEN) {
            int open = Integer.parseInt(parts[2]);
            flow.schedule[flow.dayIndex] = new int[]{open, -1};
            flow.step = Step.ADD_CLOSE;
            askClosingHour(chatId, flow);
        } else if ("closed".equals(parts[1]) && flow.step == Step.ADD_OPEN) {
            flow.schedule[flow.dayIndex] = null;
            advanceSchedule(chatId, flow);
        } else if ("close".equals(parts[1]) && flow.step == Step.ADD_CLOSE) {
            flow.schedule[flow.dayIndex][1] = Integer.parseInt(parts[2]);
            advanceSchedule(chatId, flow);
        } else if ("required".equals(parts[1]) && flow.step == Step.ADD_ROOM_REQUIRED) {
            boolean required = "yes".equals(parts[2]);
            if (flow.creatingBase) {
                flow.roomCommentRequired.add(required);
                flow.step = Step.ADD_ROOM_MORE;
                send(chatId, "Добавить ещё зал?", choices(
                        button("Добавить", "setup:addroom"),
                        button("Достаточно", "setup:roomsdone"),
                        button(BACK, "back")));
            } else {
                database.addRoom(flow.baseId, flow.roomNames.get(0), required);
                flow.roomNames.clear();
                flow.step = Step.MENU;
                showRooms(chatId, flow.baseId);
            }
        } else if ("addroom".equals(parts[1]) && flow.step == Step.ADD_ROOM_MORE) {
            flow.step = Step.ADD_ROOM_NAME;
            send(chatId, "Введите название зала.", backKeyboard());
        } else if ("roomsdone".equals(parts[1]) && flow.step == Step.ADD_ROOM_MORE) {
            askLeadHours(chatId, flow);
        } else if ("lead".equals(parts[1]) && flow.step == Step.ADD_LEAD) {
            flow.leadHours = Integer.parseInt(parts[2]);
            flow.step = Step.ADD_EQUIPMENT;
            send(chatId, "Введите список оборудования (можно оставить пустым).", backKeyboard());
        } else if ("leadpage".equals(parts[1]) && flow.step == Step.ADD_LEAD) {
            send(chatId, "За сколько часов до начала клиент ещё может бронировать?",
                    leadKeyboard(0, Integer.parseInt(parts[2])));
        }
    }

    private void handleOwnerCallback(long chatId, Flow flow, String[] parts)
            throws SQLException, TelegramApiException {
        if ("add".equals(parts[1])) {
            resetSetup(flow);
            flow.creatingBase = true;
            flow.step = Step.ADD_NAME;
            send(chatId, "Введите название базы.", backKeyboard());
        } else if ("base".equals(parts[1])) {
            flow.baseId = Long.parseLong(parts[2]);
            sendBaseSettings(chatId, flow.baseId, null);
        } else if ("list".equals(parts[1])) {
            showBases(chatId);
        } else if ("field".equals(parts[1])) {
            flow.baseId = Long.parseLong(parts[2]);
            String field = parts[3];
            if ("schedule".equals(field)) {
                showScheduleDays(chatId, flow.baseId);
            } else if ("rooms".equals(field)) {
                showRooms(chatId, flow.baseId);
            } else if ("lead".equals(field)) {
                flow.step = Step.EDIT_LEAD;
                send(chatId, "За сколько часов до начала клиент ещё может бронировать?",
                        leadKeyboard(flow.baseId, 0));
            } else {
                flow.step = "name".equals(field) ? Step.EDIT_NAME
                        : "address".equals(field) ? Step.EDIT_ADDRESS
                        : "timezone".equals(field) ? Step.EDIT_TIMEZONE : Step.EDIT_EQUIPMENT;
                String prompt = "name".equals(field) ? "Введите новое название базы."
                        : "address".equals(field) ? "Введите новый адрес."
                        : "timezone".equals(field) ? "Введите часовой пояс, например Europe/Moscow."
                        : "Введите список оборудования (можно оставить пустым).";
                send(chatId, prompt, backKeyboard());
            }
        } else if ("leadpage".equals(parts[1]) && flow.step == Step.EDIT_LEAD) {
            flow.baseId = Long.parseLong(parts[2]);
            send(chatId, "За сколько часов до начала клиент ещё может бронировать?",
                    leadKeyboard(flow.baseId, Integer.parseInt(parts[3])));
        } else if ("lead".equals(parts[1])) {
            if (flow.step != Step.EDIT_LEAD) {
                return;
            }
            database.updateLeadHours(flow.baseId, Integer.parseInt(parts[3]));
            flow.step = Step.MENU;
            sendBaseSettings(chatId, flow.baseId, "Срок бронирования сохранён.");
        } else if ("day".equals(parts[1])) {
            flow.baseId = Long.parseLong(parts[2]);
            flow.editDay = Integer.parseInt(parts[3]);
            int[] hours = database.getOpeningHours(flow.baseId, flow.editDay);
            String current = hours == null ? "выходной" : formatHour(hours[0]) + "-" + formatHour(hours[1]);
            send(chatId, DAY_NAMES[flow.editDay - 1] + ": " + current + ". Что изменить?",
                    choices(button("Час открытия", "owner:editopen:" + flow.baseId + ":" + flow.editDay),
                            button("Час закрытия", "owner:editclose:" + flow.baseId + ":" + flow.editDay),
                            button("Выходной", "owner:closed:" + flow.baseId + ":" + flow.editDay),
                            button(BACK, "owner:field:" + flow.baseId + ":schedule")));
        } else if ("editopen".equals(parts[1])) {
            flow.baseId = Long.parseLong(parts[2]);
            flow.editDay = Integer.parseInt(parts[3]);
            flow.step = Step.EDIT_DAY_OPEN;
            int[] hours = database.getOpeningHours(flow.baseId, flow.editDay);
            flow.editOpen = hours == null ? 0 : hours[0];
            List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
            for (int hour = 0; hour <= 23; hour++) {
                addButton(rows, button(formatHour(hour), "owner:setopen:" + hour), 4);
            }
            rows.add(row(button(BACK, "owner:day:" + flow.baseId + ":" + flow.editDay)));
            send(chatId, DAY_NAMES[flow.editDay - 1] + ". Новый час открытия:", markup(rows));
        } else if ("setopen".equals(parts[1]) && flow.step == Step.EDIT_DAY_OPEN) {
            int open = Integer.parseInt(parts[2]);
            int[] current = database.getOpeningHours(flow.baseId, flow.editDay);
            if (current != null && open < current[1]) {
                database.updateScheduleDay(flow.baseId, flow.editDay, new int[]{open, current[1]});
                flow.step = Step.MENU;
                sendBaseSettings(chatId, flow.baseId, "График сохранён.");
            } else {
                flow.editOpen = open;
                flow.step = Step.EDIT_DAY_CLOSE;
                askEditClosingHour(chatId, flow);
            }
        } else if ("editclose".equals(parts[1])) {
            flow.baseId = Long.parseLong(parts[2]);
            flow.editDay = Integer.parseInt(parts[3]);
            flow.step = Step.EDIT_DAY_CLOSE;
            int[] hours = database.getOpeningHours(flow.baseId, flow.editDay);
            flow.editOpen = hours == null ? 0 : hours[0];
            askEditClosingHour(chatId, flow);
        } else if ("setclose".equals(parts[1]) && flow.step == Step.EDIT_DAY_CLOSE) {
            database.updateScheduleDay(flow.baseId, flow.editDay,
                    new int[]{flow.editOpen, Integer.parseInt(parts[2])});
            flow.step = Step.MENU;
            sendBaseSettings(chatId, flow.baseId, "График сохранён.");
        } else if ("closed".equals(parts[1])) {
            flow.baseId = Long.parseLong(parts[2]);
            int day = Integer.parseInt(parts[3]);
            database.updateScheduleDay(flow.baseId, day, null);
            flow.step = Step.MENU;
            sendBaseSettings(chatId, flow.baseId, "График сохранён.");
        } else if ("room".equals(parts[1])) {
            flow.baseId = Long.parseLong(parts[2]);
            flow.roomId = Long.parseLong(parts[3]);
            Database.Room room = database.getRoom(flow.roomId);
            if (room != null) {
                flow.step = Step.MENU;
                send(chatId, "Зал «" + room.name + "». Что изменить?",
                        choices(button("Название", "owner:renameroom:" + flow.baseId + ":" + flow.roomId),
                                button("Обязательность комментария: " + (room.commentRequired ? "Да" : "Нет"),
                                        "owner:togglecomment:" + flow.roomId + ":" + (!room.commentRequired)),
                                button("Удалить зал", "owner:removeroom:" + flow.baseId + ":" + flow.roomId),
                                button("Назад", "owner:field:" + flow.baseId + ":rooms")));
            }
        } else if ("renameroom".equals(parts[1])) {
            flow.baseId = Long.parseLong(parts[2]);
            flow.roomId = Long.parseLong(parts[3]);
            Database.Room room = database.getRoom(flow.roomId);
            if (room != null) {
                flow.baseName = room.name;
                flow.step = Step.EDIT_ROOM_NAME;
                send(chatId, "Введите новое название зала.", backKeyboard());
            }
        } else if ("togglecomment".equals(parts[1])) {
            long roomId = Long.parseLong(parts[2]);
            boolean required = Boolean.parseBoolean(parts[3]);
            Database.Room room = database.getRoom(roomId);
            if (room != null) {
                flow.roomId = roomId;
                flow.baseId = room.baseId;
                database.updateRoom(roomId, room.name, required);
                showRooms(chatId, flow.baseId);
            }
        } else if ("removeroom".equals(parts[1])) {
            flow.baseId = Long.parseLong(parts[2]);
            database.deactivateRoom(Long.parseLong(parts[3]));
            showRooms(chatId, flow.baseId);
        } else if ("newroom".equals(parts[1])) {
            flow.baseId = Long.parseLong(parts[2]);
            flow.creatingBase = false;
            flow.roomNames.clear();
            flow.step = Step.ADD_ROOM_NAME;
            send(chatId, "Введите название нового зала.", backKeyboard());
        } else if ("schedule".equals(parts[1])) {
            showScheduleDays(chatId, Long.parseLong(parts[2]));
        }
    }

    private void handleBookingCallback(long chatId, long userId, Flow flow, String[] parts)
            throws SQLException, TelegramApiException {
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
            Database.Room room = database.getRoom(flow.roomId);
            if (room != null && !room.commentRequired) {
                flow.comment = "";
                showBookingConfirmation(chatId, flow);
            }
        } else if ("confirm".equals(parts[1]) && flow.step == Step.BOOK_CONFIRM) {
            LocalDateTime start = flow.bookingDate.atTime(flow.startHour, 0);
            boolean created = database.createBooking(flow.roomId, userId, start, flow.hours,
                    flow.name, flow.phone, flow.comment == null ? "" : flow.comment);
            if (created) {
                flow.clearBooking();
                flow.step = Step.MENU;
                sendMainMenu(chatId, userId, "Готово, запись создана.");
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
            showMainMenu(chatId, flow.owner);
        }
    }

    private void handleAboutCallback(long chatId, Flow flow, String[] parts)
            throws SQLException, TelegramApiException {
        if ("base".equals(parts[1])) {
            showBaseInformation(chatId, Long.parseLong(parts[2]));
        } else if ("list".equals(parts[1])) {
            showAboutBases(chatId);
        }
    }

    private void beginBooking(long chatId, Flow flow) throws SQLException, TelegramApiException {
        flow.clearBooking();
        flow.step = Step.BOOK_BASES;
        List<Database.Base> bases = database.getBases();
        if (bases.isEmpty()) {
            send(chatId, "Пока нет доступных баз для бронирования.", choices(button(BACK, "menu:home")));
            return;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
        for (Database.Base base : bases) {
            addButton(rows, button(base.name, "book:base:" + base.id), 2);
        }
        rows.add(row(button(BACK, "menu:home")));
        send(chatId, "Выберите базу:", markup(rows));
    }

    private void showRoomChoices(long chatId, Flow flow) throws SQLException, TelegramApiException {
        flow.step = Step.BOOK_ROOMS;
        List<Database.Room> rooms = database.getRooms(flow.baseId);
        List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
        for (Database.Room room : rooms) {
            addButton(rows, button(room.name, "book:room:" + room.id), 2);
        }
        rows.add(row(button(BACK, "menu:book")));
        if (rooms.isEmpty()) {
            send(chatId, "В этой базе пока нет доступных залов.", markup(rows));
        } else {
            send(chatId, "Выберите зал:", markup(rows));
        }
    }

    private void showMonthChoices(long chatId, Flow flow) throws SQLException, TelegramApiException {
        flow.step = Step.BOOK_MONTH;
        Database.Base base = database.getBase(flow.baseId);
        if (base == null) {
            beginBooking(chatId, flow);
            return;
        }
        YearMonth current = YearMonth.now(ZoneId.of(base.timezone));
        List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
        int count = 0;
        for (int offset = 0; offset < 6; offset++) {
            YearMonth month = current.plusMonths(flow.monthOffset + offset);
            if (monthHasAvailability(base, flow.roomId, month)) {
                addButton(rows, button(capitalize(month.format(MONTH)), "book:month:" + month), 2);
                count++;
            }
        }
        List<InlineKeyboardButton> navigation = new ArrayList<InlineKeyboardButton>();
        if (flow.monthOffset > 0) {
            navigation.add(button("Предыдущие", "book:months:" + Math.max(0, flow.monthOffset - 6)));
        }
        navigation.add(button("Следующие", "book:months:" + (flow.monthOffset + 6)));
        rows.add(navigation);
        rows.add(row(button(BACK, "book:base:" + base.id)));
        if (count == 0) {
            send(chatId, "В этих месяцах нет доступных дат.", markup(rows));
        } else {
            send(chatId, "Выберите месяц:", markup(rows));
        }
    }

    private void showWeekChoices(long chatId, Flow flow, YearMonth month)
            throws SQLException, TelegramApiException {
        flow.step = Step.BOOK_WEEK;
        Database.Base base = database.getBase(flow.baseId);
        LocalDate cursor = month.atDay(1);
        int count = 0;
        List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
        while (cursor.getMonthValue() == month.getMonthValue()) {
            LocalDate end = cursor.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            if (end.isAfter(month.atEndOfMonth())) {
                end = month.atEndOfMonth();
            }
            if (rangeHasAvailability(base, flow.roomId, cursor, end)) {
                String label = cursor.getDayOfMonth() + "-" + end.format(DateTimeFormatter.ofPattern("d MMM", RU));
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

    private void showDayChoices(long chatId, Flow flow) throws SQLException, TelegramApiException {
        flow.step = Step.BOOK_DAY;
        Database.Base base = database.getBase(flow.baseId);
        LocalDate start = flow.selectedWeek;
        LocalDate end = start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        if (end.getMonthValue() != start.getMonthValue()) {
            end = start.withDayOfMonth(start.lengthOfMonth());
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (dateHasAvailability(base, flow.roomId, date)) {
                String label = date.getDayOfWeek().getDisplayName(TextStyle.SHORT, RU) + ", " + date.format(DATE);
                addButton(rows, button(label, "book:day:" + date), 2);
            }
        }
        rows.add(row(button(BACK, "book:month:" + flow.selectedMonth)));
        send(chatId, rows.size() == 1 ? "В этой неделе нет доступных дней." : "Выберите день:", markup(rows));
    }

    private void showTimeChoices(long chatId, Flow flow) throws SQLException, TelegramApiException {
        flow.step = Step.BOOK_TIME;
        Database.Base base = database.getBase(flow.baseId);
        Database.Room room = database.getRoom(flow.roomId);
        List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
        if (base != null && room != null) {
            for (int hour : availableStarts(base, room, flow.bookingDate)) {
                addButton(rows, button(formatHour(hour), "book:time:" + flow.bookingDate + ":" + hour), 4);
            }
        }
        rows.add(row(button(BACK, "book:week:" + flow.selectedWeek)));
        send(chatId, rows.size() == 1 ? "На этот день свободного времени нет." : "Выберите время начала:", markup(rows));
    }

    private void showDurationChoices(long chatId, Flow flow) throws SQLException, TelegramApiException {
        flow.step = Step.BOOK_DURATION;
        Database.Base base = database.getBase(flow.baseId);
        int[] hours = base == null ? null : database.getOpeningHours(base.id, flow.bookingDate.getDayOfWeek().getValue());
        List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
        if (hours != null) {
            LocalDateTime start = flow.bookingDate.atTime(flow.startHour, 0);
            for (int duration = 1; duration <= hours[1] - flow.startHour; duration++) {
                if (!database.hasRoomConflict(flow.roomId, start, start.plusHours(duration))) {
                    addButton(rows, button(String.valueOf(duration), "book:hours:" + duration), 6);
                }
            }
        }
        rows.add(row(button(BACK, "book:day:" + flow.bookingDate)));
        send(chatId, rows.size() == 1 ? "До закрытия или свободного времени зала осталось меньше часа." : "Сколько часов?", markup(rows));
    }

    private void askBookingComment(long chatId, Flow flow) throws SQLException, TelegramApiException {
        Database.Room room = database.getRoom(flow.roomId);
        if (room == null) {
            beginBooking(chatId, flow);
            return;
        }
        flow.step = Step.BOOK_COMMENT;
        String prompt = room.commentRequired
                ? "Опишите, что будете делать в зале. Например, запись барабанов и баса."
                : "Оставьте комментарий к записи (необязательно). Например, что нужно подключить.";
        send(chatId, prompt, room.commentRequired ? backKeyboard() : commentKeyboard());
    }

    private void showBookingConfirmation(long chatId, Flow flow) throws SQLException, TelegramApiException {
        Database.Base base = database.getBase(flow.baseId);
        Database.Room room = database.getRoom(flow.roomId);
        if (base == null || room == null || (room.commentRequired && (flow.comment == null || flow.comment.isEmpty()))) {
            send(chatId, "В этом зале комментарий обязателен. Опишите, что будете делать.", backKeyboard());
            flow.step = Step.BOOK_COMMENT;
            return;
        }
        LocalDateTime start = flow.bookingDate.atTime(flow.startHour, 0);
        LocalDateTime end = start.plusHours(flow.hours);
        flow.step = Step.BOOK_CONFIRM;
        String text = "Проверьте:\nБаза «" + base.name + "», " + room.name + ", " +
                formatDateTime(start) + "-" + formatEnd(start, end) + ".\n" +
                "Имя: " + flow.name + "\nТелефон: " + flow.phone + "\nКомментарий: " +
                (flow.comment == null || flow.comment.isEmpty() ? "—" : flow.comment);
        send(chatId, text, choices(button("Подтвердить", "book:confirm"),
                button(BACK, "back"), button("Отмена", "book:cancel")));
    }

    private void showMyBookings(long chatId, long userId) throws SQLException, TelegramApiException {
        List<Database.Booking> bookings = database.getUpcomingBookings(userId);
        if (bookings.isEmpty()) {
            sendMainMenu(chatId, userId, "У вас пока нет будущих записей.");
            return;
        }
        StringBuilder text = new StringBuilder("Ваши будущие записи:\n");
        for (Database.Booking booking : bookings) {
            LocalDateTime end = booking.start.plusHours(booking.hours);
            text.append("• ").append(booking.baseName).append(", ").append(booking.roomName).append(", ")
                    .append(formatDateTime(booking.start)).append("-").append(formatEnd(booking.start, end))
                    .append("\n");
        }
        sendMainMenu(chatId, userId, text.toString());
    }

    private void showAboutBases(long chatId) throws SQLException, TelegramApiException {
        List<Database.Base> bases = database.getBases();
        if (bases.isEmpty()) {
            send(chatId, "Пока нет доступных баз.", choices(button(BACK, "menu:home")));
            return;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
        for (Database.Base base : bases) {
            addButton(rows, button(base.name, "about:base:" + base.id), 2);
        }
        rows.add(row(button(BACK, "menu:home")));
        send(chatId, "Выберите базу:", markup(rows));
    }

    private void showBaseInformation(long chatId, long baseId) throws SQLException, TelegramApiException {
        Database.Base base = database.getBase(baseId);
        if (base == null) {
            showAboutBases(chatId);
            return;
        }
        StringBuilder text = new StringBuilder("База «").append(base.name).append("»\nАдрес: ")
                .append(base.address).append("\nЧасовой пояс: ").append(base.timezone).append("\n");
        for (int day = 1; day <= 7; day++) {
            int[] hours = database.getOpeningHours(base.id, day);
            text.append(DAY_NAMES[day - 1]).append(": ")
                    .append(hours == null ? "выходной" : formatHour(hours[0]) + "-" + formatHour(hours[1]))
                    .append(day == 7 ? "\n" : ", ");
        }
        text.append("Оборудование:\n").append(base.equipment.isEmpty() ? "не указано" : base.equipment);
        send(chatId, text.toString(), choices(button(BACK, "about:list")));
    }

    private void showBases(long chatId) throws SQLException, TelegramApiException {
        List<Database.Base> bases = database.getBases();
        List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
        for (Database.Base base : bases) {
            addButton(rows, button(base.name, "owner:base:" + base.id), 2);
        }
        rows.add(row(button("Добавить базу", "owner:add")));
        rows.add(row(button(BACK, "menu:home")));
        send(chatId, bases.isEmpty() ? "Ваших баз пока нет." : "Ваши базы:", markup(rows));
    }

    private void sendBaseSettings(long chatId, long baseId, String notice)
            throws SQLException, TelegramApiException {
        Database.Base base = database.getBase(baseId);
        if (base == null) {
            showBases(chatId);
            return;
        }
        String text = (notice == null ? "" : notice + "\n") + "База «" + base.name + "». Что изменить?";
        send(chatId, text, choices(button("Название", "owner:field:" + base.id + ":name"),
                button("Адрес", "owner:field:" + base.id + ":address"),
                button("Часовой пояс", "owner:field:" + base.id + ":timezone"),
                button("График работы", "owner:field:" + base.id + ":schedule"),
                button("Залы", "owner:field:" + base.id + ":rooms"),
                button("Срок бронирования", "owner:field:" + base.id + ":lead"),
                button("Оборудование", "owner:field:" + base.id + ":equipment"),
                button(BACK, "owner:list")));
    }

    private void showScheduleDays(long chatId, long baseId) throws SQLException, TelegramApiException {
        List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
        for (int day = 1; day <= 7; day++) {
            int[] hours = database.getOpeningHours(baseId, day);
            String label = DAY_NAMES[day - 1] + " " + (hours == null ? "выходной"
                    : formatHour(hours[0]) + "-" + formatHour(hours[1]));
            addButton(rows, button(label, "owner:day:" + baseId + ":" + day), 2);
        }
        rows.add(row(button(BACK, "owner:base:" + baseId)));
        send(chatId, "Выберите день недели:", markup(rows));
    }

    private void showRooms(long chatId, long baseId) throws SQLException, TelegramApiException {
        List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
        for (Database.Room room : database.getRooms(baseId)) {
            addButton(rows, button(room.name, "owner:room:" + baseId + ":" + room.id), 2);
        }
        rows.add(row(button("Добавить зал", "owner:newroom:" + baseId)));
        rows.add(row(button(BACK, "owner:base:" + baseId)));
        send(chatId, "Залы базы:", markup(rows));
    }

    private void saveNewBase(long chatId, Flow flow) throws SQLException, TelegramApiException {
        int[][] schedule = new int[7][];
        for (int i = 0; i < 7; i++) {
            schedule[i] = flow.schedule[i] == null ? null : flow.schedule[i].clone();
        }
        boolean[] required = new boolean[flow.roomCommentRequired.size()];
        for (int i = 0; i < required.length; i++) {
            required[i] = flow.roomCommentRequired.get(i);
        }
        database.createBase(flow.baseName, flow.address, flow.timezone, flow.leadHours,
                flow.equipment == null ? "" : flow.equipment, schedule, flow.roomNames, required);
        String createdName = flow.baseName;
        flow.step = Step.MENU;
        flow.creatingBase = false;
        flow.clearBooking();
        send(chatId, "База «" + createdName + "» создана.", null);
        showBases(chatId);
    }

    private void saveEditedText(long chatId, Flow flow, String text) throws SQLException, TelegramApiException {
        if (flow.step == Step.EDIT_TIMEZONE && !validZone(text)) {
            send(chatId, "Не удалось распознать часовой пояс. Введите, например, Europe/Moscow.", backKeyboard());
            return;
        }
        if ((flow.step == Step.EDIT_NAME || flow.step == Step.EDIT_ADDRESS) && text.isEmpty()) {
            send(chatId, "Значение не должно быть пустым. Введите ещё раз.", backKeyboard());
            return;
        }
        String field = flow.step == Step.EDIT_NAME ? "name"
                : flow.step == Step.EDIT_ADDRESS ? "address"
                : flow.step == Step.EDIT_TIMEZONE ? "timezone" : "equipment";
        database.updateBaseField(flow.baseId, field, text);
        flow.step = Step.MENU;
        sendBaseSettings(chatId, flow.baseId, "Изменения сохранены.");
    }

    private void askOpeningHour(long chatId, Flow flow) throws TelegramApiException {
        flow.step = Step.ADD_OPEN;
        List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
        for (int hour = 0; hour <= 23; hour++) {
            addButton(rows, button(formatHour(hour), "setup:open:" + hour), 4);
        }
        rows.add(row(button("Выходной", "setup:closed"), button(BACK, "back")));
        send(chatId, DAY_NAMES[flow.dayIndex] + ". Час открытия:", markup(rows));
    }

    private void askClosingHour(long chatId, Flow flow) throws TelegramApiException {
        List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
        int open = flow.schedule[flow.dayIndex][0];
        for (int hour = open + 1; hour <= 24; hour++) {
            addButton(rows, button(formatHour(hour), "setup:close:" + hour), 4);
        }
        rows.add(row(button(BACK, "back")));
        send(chatId, DAY_NAMES[flow.dayIndex] + ". Час закрытия:", markup(rows));
    }

    private void advanceSchedule(long chatId, Flow flow) throws TelegramApiException {
        flow.dayIndex++;
        if (flow.dayIndex < 7) {
            askOpeningHour(chatId, flow);
        } else {
            flow.step = Step.ADD_ROOM_NAME;
            send(chatId, "Введите название первого зала.", backKeyboard());
        }
    }

    private void askLeadHours(long chatId, Flow flow) throws TelegramApiException {
        flow.step = Step.ADD_LEAD;
        send(chatId, "За сколько часов до начала клиент ещё может бронировать?", leadKeyboard(0, 0));
    }

    private void askEditClosingHour(long chatId, Flow flow) throws TelegramApiException {
        List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
        for (int hour = flow.editOpen + 1; hour <= 24; hour++) {
            addButton(rows, button(formatHour(hour), "owner:setclose:" + hour), 4);
        }
        rows.add(row(button(BACK, "owner:day:" + flow.baseId + ":" + flow.editDay)));
        send(chatId, DAY_NAMES[flow.editDay - 1] + ". Новый час закрытия:", markup(rows));
    }

    private boolean monthHasAvailability(Database.Base base, long roomId, YearMonth month)
            throws SQLException {
        return rangeHasAvailability(base, roomId, month.atDay(1), month.atEndOfMonth());
    }

    private boolean rangeHasAvailability(Database.Base base, long roomId, LocalDate start, LocalDate end)
            throws SQLException {
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (dateHasAvailability(base, roomId, date)) {
                return true;
            }
        }
        return false;
    }

    private boolean dateHasAvailability(Database.Base base, long roomId, LocalDate date) throws SQLException {
        Database.Room room = database.getRoom(roomId);
        return room != null && !availableStarts(base, room, date).isEmpty();
    }

    private List<Integer> availableStarts(Database.Base base, Database.Room room, LocalDate date)
            throws SQLException {
        int[] hours = database.getOpeningHours(base.id, date.getDayOfWeek().getValue());
        if (hours == null) {
            return Collections.emptyList();
        }
        List<Integer> result = new ArrayList<Integer>();
        ZonedDateTime earliest = ZonedDateTime.now(ZoneId.of(base.timezone)).plusHours(base.bookingLeadHours);
        for (int hour = hours[0]; hour < hours[1]; hour++) {
            LocalDateTime start = date.atTime(hour, 0);
            if (start.atZone(ZoneId.of(base.timezone)).isBefore(earliest)) {
                continue;
            }
            if (!database.hasRoomConflict(room.id, start, start.plusHours(1))) {
                result.add(hour);
            }
        }
        return result;
    }

    private void back(long chatId, Flow flow) throws SQLException, TelegramApiException {
        if (flow.owner && flow.step != Step.MENU && !isBookingStep(flow.step)) {
            flow.step = Step.MENU;
            flow.clearBooking();
            showBases(chatId);
            return;
        }
        switch (flow.step) {
            case BOOK_BASES:
            case MENU:
                flow.clearBooking();
                flow.step = Step.MENU;
                showMainMenu(chatId, flow.owner);
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
                showMainMenu(chatId, flow.owner);
        }
    }

    private boolean isBookingStep(Step step) {
        return step.ordinal() >= Step.BOOK_BASES.ordinal();
    }

    private void showMainMenu(long chatId, boolean owner) throws TelegramApiException {
        List<KeyboardRow> rows = new ArrayList<KeyboardRow>();
        KeyboardRow first = new KeyboardRow();
        first.add("Забронировать");
        first.add("Мои записи");
        rows.add(first);
        KeyboardRow second = new KeyboardRow();
        second.add("О базе");
        if (owner) {
            second.add("Базы");
        }
        rows.add(second);
        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setKeyboard(rows);
        keyboard.setResizeKeyboard(true);
        send(chatId, "Здравствуйте! Что хотите сделать?", keyboard);
    }

    private void showMainMenu(long chatId, boolean owner, String notice) throws TelegramApiException {
        showMainMenu(chatId, owner);
        if (notice != null) {
            send(chatId, notice, null);
        }
    }

    private void sendMainMenu(long chatId, long userId, String notice) throws TelegramApiException {
        send(chatId, notice, null);
        showMainMenu(chatId, flow(userId).owner);
    }

    private Flow flow(long userId) {
        Flow current = flows.get(userId);
        if (current != null) {
            return current;
        }
        Flow created = new Flow(userId == ownerId);
        Flow previous = flows.putIfAbsent(userId, created);
        return previous == null ? created : previous;
    }

    private void resetSetup(Flow flow) {
        flow.creatingBase = true;
        flow.baseName = null;
        flow.address = null;
        flow.timezone = null;
        flow.equipment = null;
        flow.leadHours = 2;
        flow.schedule = new int[7][];
        flow.dayIndex = 0;
        flow.roomNames.clear();
        flow.roomCommentRequired.clear();
    }

    private boolean validZone(String timezone) {
        try {
            ZoneId.of(timezone);
            return true;
        } catch (java.time.DateTimeException ex) {
            return false;
        }
    }

    private String normalizeRussianPhone(String input) {
        String trimmed = input.trim();
        Matcher matcher = RUSSIAN_PHONE.matcher(trimmed);
        if (!matcher.matches()) {
            return null;
        }
        return "+7" + matcher.group(1) + matcher.group(2) + matcher.group(3) + matcher.group(4);
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ofPattern("EEE d MMM, H:mm", RU));
    }

    private String formatEnd(LocalDateTime start, LocalDateTime end) {
        if (end.getHour() == 0 && end.toLocalDate().isAfter(start.toLocalDate())) {
            return "24:00";
        }
        return formatHour(end.getHour());
    }

    private String formatHour(int hour) {
        return String.valueOf(hour) + ":00";
    }

    private String capitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(RU) + value.substring(1);
    }

    private void send(long chatId, String text, org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard keyboard)
            throws TelegramApiException {
        transport.sendMessage(new Message(text, keyboard), chatId);
    }

    private void answerCallback(String callbackId) throws TelegramApiException {
        AnswerCallbackQuery answer = new AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackId);
        transport.answerCallbackQuery(answer);
    }

    private InlineKeyboardMarkup choices(InlineKeyboardButton... buttons) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
        for (InlineKeyboardButton button : buttons) {
            rows.add(row(button));
        }
        return markup(rows);
    }

    private InlineKeyboardMarkup commentKeyboard() {
        return choices(button("Пропустить", "book:skip"), button(BACK, "back"));
    }

    private InlineKeyboardMarkup backKeyboard() {
        return choices(button(BACK, "back"));
    }

    private InlineKeyboardMarkup leadKeyboard(long baseId, int offset) {
        List<List<InlineKeyboardButton>> rows = new ArrayList<List<InlineKeyboardButton>>();
        int firstHour = Math.max(1, offset + 1);
        int lastHour = (int) Math.min(Integer.MAX_VALUE, (long) offset + 24);
        for (long hours = firstHour; hours <= lastHour; hours++) {
            String callback = baseId == 0
                    ? "setup:lead:" + hours
                    : "owner:lead:" + baseId + ":" + hours;
            addButton(rows, button(String.valueOf(hours), callback), 6);
        }
        List<InlineKeyboardButton> navigation = new ArrayList<InlineKeyboardButton>();
        if (offset > 0) {
            int previous = Math.max(0, offset - 24);
            navigation.add(button("Предыдущие", baseId == 0
                    ? "setup:leadpage:" + previous
                    : "owner:leadpage:" + baseId + ":" + previous));
        }
        if (lastHour < Integer.MAX_VALUE) {
            navigation.add(button("Следующие", baseId == 0
                    ? "setup:leadpage:" + lastHour
                    : "owner:leadpage:" + baseId + ":" + lastHour));
        }
        if (!navigation.isEmpty()) {
            rows.add(navigation);
        }
        rows.add(row(button(BACK, baseId == 0 ? "back" : "owner:base:" + baseId)));
        return markup(rows);
    }

    private InlineKeyboardButton button(String text, String callback) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callback);
        return button;
    }

    private List<InlineKeyboardButton> row(InlineKeyboardButton... buttons) {
        return new ArrayList<InlineKeyboardButton>(Arrays.asList(buttons));
    }

    private void addButton(List<List<InlineKeyboardButton>> rows, InlineKeyboardButton button, int size) {
        if (rows.isEmpty() || rows.get(rows.size() - 1).size() >= size) {
            rows.add(new ArrayList<InlineKeyboardButton>());
        }
        rows.get(rows.size() - 1).add(button);
    }

    private InlineKeyboardMarkup markup(List<List<InlineKeyboardButton>> rows) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    private long chatId(Update update) {
        if (update.hasMessage()) {
            return update.getMessage().getChatId();
        }
        if (update.hasCallbackQuery() && update.getCallbackQuery().getMessage() != null) {
            return update.getCallbackQuery().getMessage().getChatId();
        }
        return 0;
    }
}

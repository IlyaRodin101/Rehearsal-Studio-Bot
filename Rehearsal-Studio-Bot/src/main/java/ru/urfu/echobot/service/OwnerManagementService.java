package ru.urfu.echobot.service;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Handles owner-only base and room configuration conversations.
@SuppressWarnings({"checkstyle:MagicNumber", "checkstyle:MultipleStringLiterals", "checkstyle:ReturnCount"})
public final class OwnerManagementService {
    private static final String BACK = "owner:back";
    private final BaseRepository baseRepository;
    private final RoomRepository roomRepository;
    private final Set<Long> ownerIds;
    private final BookingKeyboardFactory keyboards = new BookingKeyboardFactory();
    private final Map<Long, State> states = new ConcurrentHashMap<>();

    public OwnerManagementService(BaseRepository baseRepository, RoomRepository roomRepository, Set<Long> ownerIds) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.ownerIds = Set.copyOf(ownerIds);
    }

    public boolean isOwner(long userId) {
        return ownerIds.contains(userId);
    }

    public void cancel(long userId) {
        State state = states.get(userId);
        if (state != null) {
            state.reset();
        }
    }

    public void showBaseList(long userId, Bot bot) {
        if (!requireOwner(userId, bot)) {
            return;
        }
        State state = state(userId);
        state.reset();
        List<List<Button>> rows = new ArrayList<>();
        List<Base> bases = baseRepository.findByOwnerId(userId);
        for (Base base : bases) {
            keyboards.addButton(rows, button(base.name(), "owner:base:" + base.id()), 2);
        }
        rows.add(keyboards.row(button("Добавить базу", "owner:add")));
        rows.add(keyboards.row(button("Назад", "menu:home")));
        send(bot, userId, bases.isEmpty() ? "У вас пока нет баз:" : "Ваши базы:", rows);
    }

    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    public boolean handleCallback(long userId, String[] parts, Bot bot) {
        if (!requireOwner(userId, bot)) {
            return true;
        }
        if (parts.length < 2) {
            return true;
        }
        try {
            switch (parts[1]) {
                case "list" -> showBaseList(userId, bot);
                case "back" -> back(userId, bot);
                case "add" -> beginCreate(userId, bot);
                case "base" -> selectBase(userId, Long.parseLong(parts[2]), bot);
                case "setting" -> showBaseMenu(userId, bot);
                case "edit" -> beginEdit(userId, parts[2], bot);
                case "schedule" -> showSchedule(userId, bot);
                case "day" -> showDay(userId, Integer.parseInt(parts[2]), bot);
                case "off" -> closeDay(userId, parts.length == 2 ? 0 : Integer.parseInt(parts[2]), bot);
                case "opening" -> showOpeningHours(userId, Integer.parseInt(parts[2]), bot);
                case "closing" -> showClosingHours(userId, Integer.parseInt(parts[2]), bot);
                case "setopen" -> {
                    if (parts.length == 3) {
                        setOpeningHour(userId, Integer.parseInt(parts[2]), bot);
                    } else {
                        setOpeningHour(userId, Integer.parseInt(parts[3]), bot);
                    }
                }
                case "setclose" -> {
                    if (parts.length == 3) {
                        setClosingHour(userId, Integer.parseInt(parts[2]), bot);
                    } else {
                        setClosingHour(userId, Integer.parseInt(parts[3]), bot);
                    }
                }
                case "rooms" -> showRooms(userId, bot);
                case "room" -> selectRoom(userId, Long.parseLong(parts[2]), bot);
                case "addroom" -> askRoomName(userId, bot, false);
                case "roomname" -> beginRoomNameEdit(userId, Long.parseLong(parts[2]), bot);
                case "toggle" -> toggleRoom(userId, Long.parseLong(parts[2]), bot);
                case "delete" -> confirmRoomDelete(userId, Long.parseLong(parts[2]), bot);
                case "deleteyes" -> deleteRoom(userId, Long.parseLong(parts[2]), bot);
                case "newrequired" -> setNewRoomRequirement(userId, parts[2], bot);
                case "newroom" -> addAnotherRoom(userId, parts[2], bot);
                case "lead" -> selectLeadHours(userId, Integer.parseInt(parts[2]), bot);
                default -> send(bot, userId, "Действие недоступно.", backKeyboard());
            }
        } catch (NumberFormatException | IndexOutOfBoundsException exception) {
            send(bot, userId, "Действие недоступно.", backKeyboard());
        }
        return true;
    }

    public boolean handleText(long userId, String text, Bot bot) {
        State state = states.get(userId);
        if (state == null || state.step == Step.IDLE) {
            return false;
        }
        if (!requireOwner(userId, bot)) {
            state.reset();
            return true;
        }
        switch (state.step) {
            case CREATE_NAME -> {
                state.name = requireText(text, "Введите название базы.", userId, bot);
                if (state.name != null) {
                    promptAddress(userId, bot);
                }
            }
            case ROOM_CREATE_NAME -> {
                String name = requireText(text, "Введите название зала.", userId, bot);
                if (name != null) {
                    state.pendingRoomName = name;
                    state.step = Step.ROOM_CREATE_REQUIRED;
                    send(bot, userId, "Комментарий к записи в этом зале обязателен?",
                            choices(button("Да", "owner:newrequired:yes"),
                                    button("Нет", "owner:newrequired:no"), button("Назад", BACK)));
                }
            }
            case CREATE_ADDRESS -> {
                state.address = requireText(text, "Введите адрес базы.", userId, bot);
                if (state.address != null) {
                    promptTimezone(userId, bot);
                }
            }
            case CREATE_TIMEZONE -> {
                try {
                    state.timezone = ZoneId.of(text).getId();
                    state.day = 1;
                    promptCreateOpening(userId, bot);
                } catch (DateTimeException exception) {
                    send(bot, userId, "Не удалось распознать часовой пояс. Например: Europe/Moscow.",
                            backKeyboard());
                }
            }
            case CREATE_ROOM_NAME -> {
                String name = requireText(text, "Введите название зала.", userId, bot);
                if (name != null) {
                    state.pendingRoomName = name;
                    state.step = Step.CREATE_ROOM_REQUIRED;
                    send(bot, userId, "Комментарий к записи в этом зале обязателен?",
                            choices(button("Да", "owner:newrequired:yes"),
                                    button("Нет", "owner:newrequired:no"), button("Назад", BACK)));
                }
            }
            case CREATE_EQUIPMENT -> {
                state.equipment = text;
                saveNewBase(userId, bot);
            }
            case EDIT_NAME, EDIT_ADDRESS, EDIT_TIMEZONE, EDIT_EQUIPMENT, ROOM_EDIT_NAME -> saveEditedText(
                    userId, text, bot);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void beginCreate(long userId, Bot bot) {
        State state = state(userId);
        state.reset();
        state.step = Step.CREATE_NAME;
        send(bot, userId, "Введите название базы.", backKeyboard());
    }

    private void promptAddress(long userId, Bot bot) {
        state(userId).step = Step.CREATE_ADDRESS;
        send(bot, userId, "Введите адрес базы.", backKeyboard());
    }

    private void promptTimezone(long userId, Bot bot) {
        state(userId).step = Step.CREATE_TIMEZONE;
        send(bot, userId, "Введите часовой пояс базы. Например: Europe/Moscow.", backKeyboard());
    }

    private void promptCreateOpening(long userId, Bot bot) {
        State state = state(userId);
        state.step = Step.CREATE_OPENING;
        DayOfWeek day = DayOfWeek.of(state.day);
        List<List<Button>> rows = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            keyboards.addButton(rows, button(hour + ":00", "owner:setopen:" + hour), 6);
        }
        keyboards.addButton(rows, button("Выходной", "owner:off"), 6);
        rows.add(keyboards.row(button("Назад", BACK)));
        send(bot, userId, formatterDay(day) + ". Час открытия:", rows);
    }

    private void promptCreateClosing(long userId, int openHour, Bot bot) {
        State state = state(userId);
        state.step = Step.CREATE_CLOSING;
        state.openHour = openHour;
        List<List<Button>> rows = new ArrayList<>();
        for (int hour = openHour + 1; hour <= 24; hour++) {
            keyboards.addButton(rows, button(hour + ":00", "owner:setclose:" + hour), 6);
        }
        rows.add(keyboards.row(button("Назад", BACK)));
        send(bot, userId, formatterDay(DayOfWeek.of(state.day)) + ". Час закрытия:", rows);
    }

    private void finishCreateDay(long userId, Bot bot) {
        State state = state(userId);
        state.day++;
        if (state.day <= 7) {
            promptCreateOpening(userId, bot);
        } else {
            state.step = Step.CREATE_ROOM_NAME;
            send(bot, userId, "Введите название первого зала.", backKeyboard());
        }
    }

    private void saveNewBase(long userId, Bot bot) {
        State state = state(userId);
        Base saved = baseRepository.save(new Base(0, userId, state.name, state.address, state.timezone, state.leadHours,
                state.equipment, state.schedule));
        for (Room room : state.newRooms) {
            roomRepository.save(new Room(0, saved.id(), room.name(), room.commentRequired()));
        }
        state.selectedBaseId = saved.id();
        state.resetDraft();
        showBaseList(userId, bot);
        send(bot, userId, "База «" + saved.name() + "» создана.", baseListButtons(userId));
    }

    private void selectBase(long userId, long baseId, Bot bot) {
        if (!ownedBase(userId, baseId, bot)) {
            return;
        }
        state(userId).selectedBaseId = baseId;
        showBaseMenu(userId, bot);
    }

    private void showBaseMenu(long userId, Bot bot) {
        Base base = selectedBase(userId, bot);
        if (base == null) {
            return;
        }
        state(userId).step = Step.IDLE;
        List<List<Button>> rows = new ArrayList<>();
        rows.add(keyboards.row(button("Название", "owner:edit:name"), button("Адрес", "owner:edit:address")));
        rows.add(keyboards.row(button("Часовой пояс", "owner:edit:timezone"),
                button("График работы", "owner:schedule")));
        rows.add(keyboards.row(button("Залы", "owner:rooms"), button("Срок бронирования", "owner:edit:lead")));
        rows.add(keyboards.row(button("Оборудование", "owner:edit:equipment")));
        rows.add(keyboards.row(button("Назад", "owner:list")));
        send(bot, userId, "База «" + base.name() + "». Что изменить?", rows);
    }

    private void beginEdit(long userId, String field, Bot bot) {
        Base base = selectedBase(userId, bot);
        if (base == null) {
            return;
        }
        State state = state(userId);
        switch (field) {
            case "name" -> setTextStep(userId, Step.EDIT_NAME, bot, "Введите новое название базы.");
            case "address" -> setTextStep(userId, Step.EDIT_ADDRESS, bot, "Введите новый адрес базы.");
            case "timezone" -> setTextStep(userId, Step.EDIT_TIMEZONE, bot,
                    "Введите новый часовой пояс базы. Например: Europe/Moscow.");
            case "equipment" -> setTextStep(userId, Step.EDIT_EQUIPMENT, bot,
                    "Введите список оборудования (можно оставить пустым).");
            case "lead" -> showLeadHours(userId, bot);
            default -> send(bot, userId, "Действие недоступно.", backKeyboard());
        }
    }

    private void showLeadHours(long userId, Bot bot) {
        List<List<Button>> rows = new ArrayList<>();
        for (int hour = 1; hour <= 48; hour++) {
            keyboards.addButton(rows, button(String.valueOf(hour), "owner:lead:" + hour), 8);
        }
        rows.add(keyboards.row(button("Назад", BACK)));
        send(bot, userId, "За сколько часов до начала клиент ещё может бронировать?", rows);
    }

    private void selectLeadHours(long userId, int hours, Bot bot) {
        State state = state(userId);
        if (state.step == Step.CREATE_LEAD && hours >= 1 && hours <= 48) {
            state.leadHours = hours;
            state.step = Step.CREATE_EQUIPMENT;
            send(bot, userId, "Введите список оборудования (можно оставить пустым).", backKeyboard());
            return;
        }
        Base base = selectedBase(userId, bot);
        if (base == null || hours < 1 || hours > 48) {
            send(bot, userId, "Действие недоступно.", backKeyboard());
            return;
        }
        updateBase(userId, new Base(base.id(), base.ownerId(), base.name(), base.address(), base.timezone(), hours,
                base.equipment(), base.openingHours()));
        showBaseMenu(userId, bot);
    }

    private void showSchedule(long userId, Bot bot) {
        Base base = selectedBase(userId, bot);
        if (base == null) {
            return;
        }
        List<List<Button>> rows = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            OpeningHours hours = base.hoursOn(day).orElse(null);
            String label = formatterDay(day) + " " + (hours == null
                    ? "выходной" : hours.openHour() + ":00-" + hours.closeHour() + ":00");
            keyboards.addButton(rows, button(label, "owner:day:" + day.getValue()), 2);
        }
        rows.add(keyboards.row(button("Назад", "owner:setting")));
        send(bot, userId, "График работы. Выберите день недели:", rows);
    }

    private void showDay(long userId, int day, Bot bot) {
        Base base = selectedBase(userId, bot);
        if (base == null || day < 1 || day > 7) {
            return;
        }
        state(userId).selectedDay = day;
        state(userId).step = Step.IDLE;
        OpeningHours hours = base.hoursOn(DayOfWeek.of(day)).orElse(null);
        List<List<Button>> rows = new ArrayList<>();
        rows.add(keyboards.row(button("Час открытия", "owner:opening:" + day),
                button("Час закрытия", "owner:closing:" + day)));
        rows.add(keyboards.row(button("Выходной", "owner:off:" + day)));
        rows.add(keyboards.row(button("Назад", "owner:schedule")));
        String text = formatterDay(DayOfWeek.of(day)) + (hours == null
                ? ": выходной. Что изменить?" : ": " + hours.openHour() + ":00-" + hours.closeHour()
                + ":00. Что изменить?");
        send(bot, userId, text, rows);
    }

    private void showOpeningHours(long userId, int day, Bot bot) {
        Base base = selectedBase(userId, bot);
        if (base == null || day < 1 || day > 7) {
            return;
        }
        state(userId).selectedDay = day;
        OpeningHours current = base.hoursOn(DayOfWeek.of(day)).orElse(new OpeningHours(9, 24));
        List<List<Button>> rows = new ArrayList<>();
        for (int hour = 0; hour < current.closeHour(); hour++) {
            keyboards.addButton(rows, button(hour + ":00", "owner:setopen:" + hour), 6);
        }
        rows.add(keyboards.row(button("Назад", "owner:day:" + day)));
        send(bot, userId, formatterDay(DayOfWeek.of(day)) + ". Новый час открытия:", rows);
    }

    private void showClosingHours(long userId, int day, Bot bot) {
        Base base = selectedBase(userId, bot);
        if (base == null || day < 1 || day > 7) {
            return;
        }
        OpeningHours current = base.hoursOn(DayOfWeek.of(day)).orElse(new OpeningHours(8, 24));
        state(userId).selectedDay = day;
        List<List<Button>> rows = new ArrayList<>();
        for (int hour = current.openHour() + 1; hour <= 24; hour++) {
            keyboards.addButton(rows, button(hour + ":00", "owner:setclose:" + hour), 6);
        }
        rows.add(keyboards.row(button("Назад", "owner:day:" + day)));
        send(bot, userId, formatterDay(DayOfWeek.of(day)) + ". Новый час закрытия:", rows);
    }

    private void closeDay(long userId, int day, Bot bot) {
        State state = state(userId);
        if (state.step == Step.CREATE_OPENING && partsInCreation(state)) {
            state.schedule.remove(DayOfWeek.of(state.day));
            finishCreateDay(userId, bot);
            return;
        }
        Base base = selectedBase(userId, bot);
        if (base == null || day < 1 || day > 7) {
            return;
        }
        Map<DayOfWeek, OpeningHours> schedule = copySchedule(base);
        schedule.remove(DayOfWeek.of(day));
        updateBase(userId, copy(base, base.name(), base.address(), base.timezone(), base.bookingLeadHours(),
                base.equipment(), schedule));
        showDay(userId, day, bot);
    }

    private boolean partsInCreation(State state) {
        return state.selectedBaseId == 0;
    }

    private void setOpeningHour(long userId, int hour, Bot bot) {
        State state = state(userId);
        if (state.step == Step.CREATE_OPENING) {
            if (hour < 0 || hour > 23) {
                send(bot, userId, "Действие недоступно.", backKeyboard());
                return;
            }
            promptCreateClosing(userId, hour, bot);
            return;
        }
        Base base = selectedBase(userId, bot);
        int day = state.selectedDay;
        if (base == null || day < 1 || day > 7) {
            return;
        }
        OpeningHours current = base.hoursOn(DayOfWeek.of(day)).orElse(new OpeningHours(9, 24));
        if (hour < 0 || hour >= current.closeHour()) {
            showOpeningHours(userId, day, bot);
            return;
        }
        Map<DayOfWeek, OpeningHours> schedule = copySchedule(base);
        schedule.put(DayOfWeek.of(day), new OpeningHours(hour, current.closeHour()));
        updateBase(userId, copy(base, base.name(), base.address(), base.timezone(), base.bookingLeadHours(),
                base.equipment(), schedule));
        showDay(userId, day, bot);
    }

    private void setClosingHour(long userId, int hour, Bot bot) {
        State state = state(userId);
        if (state.step == Step.CREATE_CLOSING) {
            if (hour <= state.openHour || hour > 24) {
                send(bot, userId, "Действие недоступно.", backKeyboard());
                return;
            }
            state.schedule.put(DayOfWeek.of(state.day), new OpeningHours(state.openHour, hour));
            finishCreateDay(userId, bot);
            return;
        }
        Base base = selectedBase(userId, bot);
        int day = state.selectedDay;
        if (base == null || day < 1 || day > 7) {
            return;
        }
        OpeningHours current = base.hoursOn(DayOfWeek.of(day)).orElse(null);
        if (current == null || hour <= current.openHour() || hour > 24) {
            showClosingHours(userId, day, bot);
            return;
        }
        Map<DayOfWeek, OpeningHours> schedule = copySchedule(base);
        schedule.put(DayOfWeek.of(day), new OpeningHours(current.openHour(), hour));
        updateBase(userId, copy(base, base.name(), base.address(), base.timezone(), base.bookingLeadHours(),
                base.equipment(), schedule));
        showDay(userId, day, bot);
    }

    private void showRooms(long userId, Bot bot) {
        Base base = selectedBase(userId, bot);
        if (base == null) {
            return;
        }
        state(userId).step = Step.IDLE;
        List<List<Button>> rows = new ArrayList<>();
        List<Room> rooms = roomRepository.findByBaseId(base.id());
        for (Room room : rooms) {
            keyboards.addButton(rows, button(room.name(), "owner:room:" + room.id()), 2);
        }
        rows.add(keyboards.row(button("Добавить зал", "owner:addroom")));
        rows.add(keyboards.row(button("Назад", "owner:setting")));
        send(bot, userId, "Залы базы:", rows);
    }

    private void selectRoom(long userId, long roomId, Bot bot) {
        Room room = activeOwnedRoom(userId, roomId, bot);
        if (room == null) {
            return;
        }
        State state = state(userId);
        state.selectedRoomId = room.id();
        state.step = Step.IDLE;
        List<List<Button>> rows = new ArrayList<>();
        rows.add(keyboards.row(button("Название", "owner:roomname:" + room.id()),
                button("Комментарий обязателен: " + (room.commentRequired() ? "да" : "нет"),
                        "owner:toggle:" + room.id())));
        rows.add(keyboards.row(button("Удалить зал", "owner:delete:" + room.id())));
        rows.add(keyboards.row(button("Назад", "owner:rooms")));
        send(bot, userId, "Зал «" + room.name() + "». Что изменить?", rows);
    }

    private void askRoomName(long userId, Bot bot, boolean create) {
        state(userId).step = create ? Step.CREATE_ROOM_NAME : Step.ROOM_CREATE_NAME;
        send(bot, userId, "Введите название нового зала.", backKeyboard());
    }

    private void setTextStep(long userId, Step step, Bot bot, String prompt) {
        State state = state(userId);
        state.step = step;
        send(bot, userId, prompt, backKeyboard());
    }

    private void setNewRoomRequirement(long userId, String value, Bot bot) {
        State state = state(userId);
        if ((state.step != Step.CREATE_ROOM_REQUIRED && state.step != Step.ROOM_CREATE_REQUIRED)
                || !List.of("yes", "no").contains(value)) {
            send(bot, userId, "Действие недоступно.", backKeyboard());
            return;
        }
        if (state.step == Step.ROOM_CREATE_REQUIRED) {
            Base base = selectedBase(userId, bot);
            if (base == null) {
                return;
            }
            roomRepository.save(new Room(0, base.id(), state.pendingRoomName, "yes".equals(value)));
            state.pendingRoomName = null;
            state.step = Step.IDLE;
            showRooms(userId, bot);
            return;
        }
        state.newRooms.add(new Room(0, 0, state.pendingRoomName, "yes".equals(value)));
        state.pendingRoomName = null;
        state.step = Step.CREATE_MORE_ROOMS;
        send(bot, userId, "Добавить ещё зал?",
                choices(button("Добавить", "owner:newroom:add"),
                        button("Достаточно", "owner:newroom:done"), button("Назад", BACK)));
    }

    private void addAnotherRoom(long userId, String choice, Bot bot) {
        State state = state(userId);
        if (state.step != Step.CREATE_MORE_ROOMS) {
            send(bot, userId, "Действие недоступно.", backKeyboard());
            return;
        }
        if ("add".equals(choice)) {
            state.step = Step.CREATE_ROOM_NAME;
            send(bot, userId, "Введите название зала.", backKeyboard());
        } else if ("done".equals(choice)) {
            state.step = Step.CREATE_LEAD;
            showLeadHoursCreate(userId, bot);
        } else {
            send(bot, userId, "Действие недоступно.", backKeyboard());
        }
    }

    private void showLeadHoursCreate(long userId, Bot bot) {
        List<List<Button>> rows = new ArrayList<>();
        for (int hour = 1; hour <= 48; hour++) {
            keyboards.addButton(rows, button(String.valueOf(hour), "owner:lead:" + hour), 8);
        }
        rows.add(keyboards.row(button("Назад", BACK)));
        send(bot, userId, "За сколько часов до начала клиент ещё может бронировать?", rows);
    }

    private void beginRoomNameEdit(long userId, long roomId, Bot bot) {
        Room room = activeOwnedRoom(userId, roomId, bot);
        if (room != null) {
            setTextStep(userId, Step.ROOM_EDIT_NAME, bot, "Введите новое название зала.");
        }
    }

    private void confirmRoomDelete(long userId, long roomId, Bot bot) {
        Room room = activeOwnedRoom(userId, roomId, bot);
        if (room == null) {
            return;
        }
        state(userId).step = Step.DELETE_ROOM;
        send(bot, userId, "Удалить зал «" + room.name()
                + "»? Новые записи в нём будут недоступны, уже созданные сохранятся.",
                choices(button("Удалить", "owner:deleteyes:" + room.id()),
                        button("Отмена", "owner:room:" + room.id())));
    }

    private void deleteRoom(long userId, long roomId, Bot bot) {
        Room room = activeOwnedRoom(userId, roomId, bot);
        if (room == null || state(userId).step != Step.DELETE_ROOM) {
            send(bot, userId, "Действие недоступно.", backKeyboard());
            return;
        }
        roomRepository.deactivate(room.id());
        state(userId).step = Step.IDLE;
        showRooms(userId, bot);
        send(bot, userId, "Зал «" + room.name() + "» удалён.", roomsButtons(userId));
    }

    private void toggleRoom(long userId, long roomId, Bot bot) {
        Room room = activeOwnedRoom(userId, roomId, bot);
        if (room == null) {
            return;
        }
        roomRepository.update(new Room(room.id(), room.baseId(), room.name(), !room.commentRequired()));
        selectRoom(userId, room.id(), bot);
    }

    private void saveEditedText(long userId, String text, Bot bot) {
        State state = state(userId);
        String value = text.trim();
        if (state.step == Step.ROOM_EDIT_NAME) {
            if (value.isEmpty()) {
                send(bot, userId, "Название зала не может быть пустым.", backKeyboard());
                return;
            }
            Room room = activeOwnedRoom(userId, state.selectedRoomId, bot);
            if (room != null) {
                roomRepository.update(new Room(room.id(), room.baseId(), value, room.commentRequired()));
                selectRoom(userId, room.id(), bot);
            }
            return;
        }
        Base base = selectedBase(userId, bot);
        if (base == null) {
            return;
        }
        if (state.step == Step.EDIT_TIMEZONE) {
            try {
                value = ZoneId.of(value).getId();
            } catch (DateTimeException exception) {
                send(bot, userId, "Не удалось распознать часовой пояс. Например: Europe/Moscow.", backKeyboard());
                return;
            }
        } else if (state.step != Step.EDIT_EQUIPMENT && value.isEmpty()) {
            send(bot, userId, "Значение не может быть пустым.", backKeyboard());
            return;
        }
        String name = state.step == Step.EDIT_NAME ? value : base.name();
        String address = state.step == Step.EDIT_ADDRESS ? value : base.address();
        String timezone = state.step == Step.EDIT_TIMEZONE ? value : base.timezone();
        String equipment = state.step == Step.EDIT_EQUIPMENT ? text : base.equipment();
        updateBase(userId, copy(base, name, address, timezone, base.bookingLeadHours(), equipment,
                base.openingHours()));
        showBaseMenu(userId, bot);
    }

    private void updateBase(long userId, Base base) {
        if (base.ownerId() != userId) {
            throw new IllegalStateException("Attempt to update a base owned by another user");
        }
        baseRepository.update(base);
    }

    private Base copy(Base base, String name, String address, String timezone, int leadHours, String equipment,
                      Map<DayOfWeek, OpeningHours> schedule) {
        return new Base(base.id(), base.ownerId(), name, address, timezone, leadHours, equipment, schedule);
    }

    private Map<DayOfWeek, OpeningHours> copySchedule(Base base) {
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        schedule.putAll(base.openingHours());
        return schedule;
    }

    private Base selectedBase(long userId, Bot bot) {
        long baseId = state(userId).selectedBaseId;
        if (baseId == 0 || !ownedBase(userId, baseId, bot)) {
            return null;
        }
        return baseRepository.findById(baseId).orElse(null);
    }

    private boolean ownedBase(long userId, long baseId, Bot bot) {
        Base base = baseRepository.findById(baseId).orElse(null);
        if (base == null || base.ownerId() != userId) {
            send(bot, userId, "Действие недоступно.", backKeyboard());
            return false;
        }
        return true;
    }

    private Room activeOwnedRoom(long userId, long roomId, Bot bot) {
        Room room = roomRepository.findById(roomId).orElse(null);
        Base base = room == null ? null : baseRepository.findById(room.baseId()).orElse(null);
        if (room == null || base == null || !room.active() || base.ownerId() != userId
                || base.id() != state(userId).selectedBaseId) {
            send(bot, userId, "Действие недоступно.", backKeyboard());
            return null;
        }
        return room;
    }

    private boolean requireOwner(long userId, Bot bot) {
        if (!isOwner(userId)) {
            send(bot, userId, "Действие недоступно.", List.of());
            return false;
        }
        return true;
    }

    private void back(long userId, Bot bot) {
        State state = state(userId);
        if (state.selectedBaseId != 0 && baseRepository.findById(state.selectedBaseId)
                .filter(base -> base.ownerId() == userId).isPresent()) {
            state.step = Step.IDLE;
            showBaseMenu(userId, bot);
        } else {
            showBaseList(userId, bot);
        }
    }

    private State state(long userId) {
        return states.computeIfAbsent(userId, ignored -> new State());
    }

    private String requireText(String text, String prompt, long userId, Bot bot) {
        if (text.isBlank()) {
            send(bot, userId, prompt, backKeyboard());
            return null;
        }
        return text.trim();
    }

    private List<List<Button>> baseListButtons(long userId) {
        List<List<Button>> rows = new ArrayList<>();
        for (Base base : baseRepository.findByOwnerId(userId)) {
            keyboards.addButton(rows, button(base.name(), "owner:base:" + base.id()), 2);
        }
        rows.add(keyboards.row(button("Добавить базу", "owner:add")));
        rows.add(keyboards.row(button("Назад", "menu:home")));
        return rows;
    }

    private List<List<Button>> roomsButtons(long userId) {
        List<List<Button>> rows = new ArrayList<>();
        for (Room room : roomRepository.findByBaseId(state(userId).selectedBaseId)) {
            keyboards.addButton(rows, button(room.name(), "owner:room:" + room.id()), 2);
        }
        rows.add(keyboards.row(button("Добавить зал", "owner:addroom")));
        rows.add(keyboards.row(button("Назад", "owner:setting")));
        return rows;
    }

    private List<List<Button>> backKeyboard() {
        return keyboards.choices(button("Назад", BACK));
    }

    private List<List<Button>> choices(Button... buttons) {
        return keyboards.choices(buttons);
    }

    private Button button(String text, String data) {
        return keyboards.button(text, data);
    }

    private String formatterDay(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Пн";
            case TUESDAY -> "Вт";
            case WEDNESDAY -> "Ср";
            case THURSDAY -> "Чт";
            case FRIDAY -> "Пт";
            case SATURDAY -> "Сб";
            case SUNDAY -> "Вс";
        };
    }

    private void send(Bot bot, long chatId, String text, List<List<Button>> rows) {
        bot.sendMessage(new Message(text, null, rows), chatId);
    }

    private enum Step {
        IDLE, CREATE_NAME, CREATE_ADDRESS, CREATE_TIMEZONE, CREATE_OPENING, CREATE_CLOSING, CREATE_ROOM_NAME,
        CREATE_ROOM_REQUIRED, CREATE_MORE_ROOMS, CREATE_LEAD, CREATE_EQUIPMENT, ROOM_CREATE_NAME,
        ROOM_CREATE_REQUIRED, EDIT_NAME, EDIT_ADDRESS,
        EDIT_TIMEZONE, EDIT_EQUIPMENT, ROOM_EDIT_NAME, DELETE_ROOM
    }

    private static final class State {
        private Step step = Step.IDLE;
        private long selectedBaseId;
        private long selectedRoomId;
        private int selectedDay;
        private int day;
        private int openHour;
        private int leadHours = 2;
        private String name;
        private String address;
        private String timezone;
        private String equipment = "";
        private String pendingRoomName;
        private final Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        private final List<Room> newRooms = new ArrayList<>();

        private void reset() {
            selectedBaseId = 0;
            selectedRoomId = 0;
            selectedDay = 0;
            resetDraft();
            step = Step.IDLE;
        }

        private void resetDraft() {
            day = 0;
            openHour = 0;
            leadHours = 2;
            name = null;
            address = null;
            timezone = null;
            equipment = "";
            pendingRoomName = null;
            schedule.clear();
            newRooms.clear();
        }
    }
}

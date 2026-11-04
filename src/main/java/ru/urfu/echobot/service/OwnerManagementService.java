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

/// Coordinates owner-only base, schedule, and room configuration conversations.
@SuppressWarnings({"checkstyle:MagicNumber", "checkstyle:MultipleStringLiterals", "checkstyle:ReturnCount"})
public final class OwnerManagementService {
    /// Callback action for returning to the previous owner screen.
    private static final String BACK = "owner:back";
    /// Index containing the action name in callback segments.
    private static final int CALLBACK_ACTION_INDEX = 1;
    /// Expected segment count for callbacks with no argument.
    private static final int CALLBACK_ACTION_ONLY_LENGTH = 2;
    /// Expected segment count for callbacks with one argument.
    private static final int CALLBACK_WITH_VALUE_LENGTH = 3;
    /// Repository for owner bases and their schedules.
    private final BaseRepository baseRepository;
    /// Repository for owner rooms.
    private final RoomRepository roomRepository;
    /// Authorized Telegram owner IDs.
    private final Set<Long> ownerIds;
    /// Shared keyboard helper.
    final BookingKeyboardFactory keyboards = new BookingKeyboardFactory();
    /// Manager for schedule configuration.
    private final OwnerScheduleService scheduleService;
    /// Conversation state indexed by Telegram user ID.
    private final Map<Long, State> states = new ConcurrentHashMap<>();

    /// Creates owner management with the repositories and authorized owner IDs.
    ///
    /// @param baseRepository persistent base repository
    /// @param roomRepository persistent room repository
    /// @param ownerIds authorized Telegram user IDs
    public OwnerManagementService(BaseRepository baseRepository, RoomRepository roomRepository, Set<Long> ownerIds) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.ownerIds = Set.copyOf(ownerIds);
        scheduleService = new OwnerScheduleService(this);
    }

    /// Validates and stores a new room name before asking for its comment requirement.
    ///
    /// @param state current owner conversation state
    /// @param userId owner sender ID
    /// @param text entered room name
    /// @param bot bot used to display the next prompt
    private void askNewRoomName(State state, long userId, String text, Bot bot) {
        String name = requireText(text, "Введите название зала.", userId, bot);
        if (name != null) {
            state.pendingRoomName = name;
            state.step = state.selectedBaseId == 0 ? Step.CREATE_ROOM_REQUIRED : Step.ROOM_CREATE_REQUIRED;
            send(bot, userId, "Комментарий к записи в этом зале обязателен?",
                    choices(button("Да", "owner:newrequired:yes"),
                            button("Нет", "owner:newrequired:no"), button("Назад", BACK)));
        }
    }

    /// Checks whether a Telegram user is configured as a base owner.
    ///
    /// @param userId Telegram sender ID
    /// @return true when the sender is an authorized owner
    public boolean isOwner(long userId) {
        return ownerIds.contains(userId);
    }

    /// Cancels any active owner configuration conversation for a user.
    ///
    /// @param userId Telegram sender ID
    public void cancel(long userId) {
        states.remove(userId);
    }

    /// Displays all bases belonging to the owner.
    ///
    /// @param userId owner sender ID
    /// @param bot bot used to display the list
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

    /// Handles an owner callback for a private chat.
    ///
    /// @param userId Telegram sender and private chat ID
    /// @param parts callback data split by colons
    /// @param bot bot used to send responses
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    public boolean handleCallback(long userId, String[] parts, Bot bot) {
        return handleCallback(userId, userId, parts, bot);
    }

    /// Handles an owner callback using the sender ID for authorization and chat ID for replies.
    @SuppressWarnings("checkstyle:CyclomaticComplexity")
    public boolean handleCallback(long chatId, long userId, String[] parts, Bot bot) {
        if (!isOwner(userId)) {
            bot.sendMessage(new Message("Действие недоступно.", null, List.of()), chatId);
            return true;
        }
        state(userId).chatId = chatId;
        if (parts.length <= CALLBACK_ACTION_INDEX) {
            return true;
        }
        try {
            String action = parts[CALLBACK_ACTION_INDEX];
            if (!hasExpectedParts(action, parts.length)) {
                send(bot, userId, "Действие недоступно.", backKeyboard());
                return true;
            }
            switch (action) {
                case "list" -> showBaseList(userId, bot);
                case "back" -> back(userId, bot);
                case "add" -> beginCreate(userId, bot);
                case "base" -> selectBase(userId, Long.parseLong(parts[2]), bot);
                case "setting" -> showBaseMenu(userId, bot);
                case "edit" -> beginEdit(userId, parts[2], bot);
                case "schedule" -> scheduleService.showSchedule(userId, bot);
                case "day" -> scheduleService.showDay(userId, Integer.parseInt(parts[2]), bot);
                case "off" -> scheduleService.closeDay(userId, parts.length == CALLBACK_ACTION_ONLY_LENGTH
                        ? 0 : Integer.parseInt(parts[2]), bot);
                case "opening" -> scheduleService.showOpeningHours(userId, Integer.parseInt(parts[2]), bot);
                case "closing" -> scheduleService.showClosingHours(userId, Integer.parseInt(parts[2]), bot);
                case "setopen" -> scheduleService.setOpeningHour(userId, Integer.parseInt(parts[2]), bot);
                case "setclose" -> scheduleService.setClosingHour(userId, Integer.parseInt(parts[2]), bot);
                case "rooms" -> showRooms(userId, bot);
                case "room" -> selectRoom(userId, Long.parseLong(parts[2]), bot);
                case "addroom" -> askRoomName(userId, bot);
                case "roomname" -> beginRoomNameEdit(userId, Long.parseLong(parts[2]), bot);
                case "toggle" -> toggleRoom(userId, Long.parseLong(parts[2]), bot);
                case "delete" -> confirmRoomDelete(userId, Long.parseLong(parts[2]), bot);
                case "deleteyes" -> deleteRoom(userId, Long.parseLong(parts[2]), bot);
                case "newrequired" -> setNewRoomRequirement(userId, parts[2], bot);
                case "newroom" -> addAnotherRoom(userId, parts[2], bot);
                case "lead" -> selectLeadHours(userId, Integer.parseInt(parts[2]), bot);
                default -> send(bot, userId, "Действие недоступно.", backKeyboard());
            }
        } catch (NumberFormatException exception) {
            send(bot, userId, "Действие недоступно.", backKeyboard());
        }
        return true;
    }

    /// Checks the exact callback segment count required for an owner action.
    ///
    /// @param action callback action
    /// @param length number of callback segments
    /// @return true when the callback has the expected number of segments
    private boolean hasExpectedParts(String action, int length) {
        if ("off".equals(action)) {
            return length == CALLBACK_ACTION_ONLY_LENGTH || length == CALLBACK_WITH_VALUE_LENGTH;
        }
        int expectedLength = switch (action) {
            case "list", "back", "add", "setting", "schedule", "rooms", "addroom" -> CALLBACK_ACTION_ONLY_LENGTH;
            case "base", "day", "opening", "closing", "setopen", "setclose", "room", "roomname",
                    "toggle", "delete", "deleteyes", "newrequired", "newroom", "lead" -> CALLBACK_WITH_VALUE_LENGTH;
            case "edit" -> CALLBACK_WITH_VALUE_LENGTH;
            default -> -1;
        };
        return expectedLength == length;
    }

    /// Handles owner text input for a private chat.
    ///
    /// @param userId Telegram sender and private chat ID
    /// @param text incoming text
    /// @param bot bot used to send responses
    public boolean handleText(long userId, String text, Bot bot) {
        return handleText(userId, userId, text, bot);
    }

    /// Handles owner text input using the sender ID for state and chat ID for replies.
    public boolean handleText(long chatId, long userId, String text, Bot bot) {
        State state = states.get(userId);
        if (state == null || state.step == Step.IDLE) {
            return false;
        }
        state.chatId = chatId;
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
            case ROOM_CREATE_NAME -> askNewRoomName(state, userId, text, bot);
            case CREATE_ADDRESS -> {
                state.address = requireText(text, "Введите адрес базы.", userId, bot);
                if (state.address != null) {
                    promptTimezone(userId, bot);
                }
            }
            case CREATE_TIMEZONE -> {
                try {
                    state.timezone = normalizeTimezone(text);
                    state.day = 1;
                    scheduleService.promptCreateOpening(userId, bot);
                } catch (DateTimeException exception) {
                    send(bot, userId, "Не удалось распознать часовой пояс. Например: Europe/Moscow.",
                            backKeyboard());
                }
            }
            case CREATE_ROOM_NAME -> askNewRoomName(state, userId, text, bot);
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

    /// Resets the owner state and starts creating a new base.
    ///
    /// @param userId owner sender ID
    /// @param bot bot used to request the base name
    private void beginCreate(long userId, Bot bot) {
        State state = state(userId);
        state.reset();
        state.step = Step.CREATE_NAME;
        send(bot, userId, "Введите название базы.", backKeyboard());
    }

    /// Moves a new-base conversation to address entry.
    ///
    /// @param userId owner sender ID
    /// @param bot bot used to send the prompt
    private void promptAddress(long userId, Bot bot) {
        state(userId).step = Step.CREATE_ADDRESS;
        send(bot, userId, "Введите адрес базы.", backKeyboard());
    }

    /// Moves a new-base conversation to time-zone entry.
    ///
    /// @param userId owner sender ID
    /// @param bot bot used to send the prompt
    private void promptTimezone(long userId, Bot bot) {
        state(userId).step = Step.CREATE_TIMEZONE;
        send(bot, userId, "Введите часовой пояс базы. Например: Europe/Moscow.", backKeyboard());
    }

    /// Atomically persists a new base, its weekly schedule, and initial rooms.
    ///
    /// @param userId owner sender ID
    /// @param bot bot used to report the result
    private void saveNewBase(long userId, Bot bot) {
        State state = state(userId);
        Base saved = baseRepository.save(new Base(0, userId, state.name, state.address, state.timezone,
                state.leadHours, state.equipment, state.schedule), state.newRooms);
        state.selectedBaseId = saved.id();
        state.resetDraft();
        showBaseList(userId, bot);
        send(bot, userId, "База «" + saved.name() + "» создана.", baseListButtons(userId));
    }

    /// Selects an owned base and opens its configuration menu.
    ///
    /// @param userId owner sender ID
    /// @param baseId selected base ID
    /// @param bot bot used to display the menu
    private void selectBase(long userId, long baseId, Bot bot) {
        if (!ownedBase(userId, baseId, bot)) {
            return;
        }
        state(userId).selectedBaseId = baseId;
        showBaseMenu(userId, bot);
    }

    /// Displays configuration options for the selected base.
    ///
    /// @param userId owner sender ID
    /// @param bot bot used to display the menu
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

    /// Starts editing one supported base field.
    ///
    /// @param userId owner sender ID
    /// @param field field selected for editing
    /// @param bot bot used to display the prompt
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

    /// Displays allowed booking lead-time choices.
    ///
    /// @param userId owner sender ID
    /// @param bot bot used to display the choices
    private void showLeadHours(long userId, Bot bot) {
        if (state(userId).selectedBaseId == 0) {
            state(userId).step = Step.CREATE_LEAD;
        }
        List<List<Button>> rows = new ArrayList<>();
        for (int hour = 1; hour <= 48; hour++) {
            keyboards.addButton(rows, button(String.valueOf(hour), "owner:lead:" + hour), 8);
        }
        rows.add(keyboards.row(button("Назад", BACK)));
        send(bot, userId, "За сколько часов до начала клиент ещё может бронировать?", rows);
    }

    /// Applies a booking lead-time choice or advances the creation wizard.
    ///
    /// @param userId owner sender ID
    /// @param hours selected lead time
    /// @param bot bot used to display the next screen
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

    /// Displays active rooms belonging to the selected base.
    ///
    /// @param userId owner sender ID
    /// @param bot bot used to display the rooms
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

    /// Selects an active room and displays its configuration actions.
    ///
    /// @param userId owner sender ID
    /// @param roomId selected room ID
    /// @param bot bot used to display the room menu
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

    /// Starts the common new-room name-entry step for a selected base.
    ///
    /// @param userId owner sender ID
    /// @param bot bot used to request the room name
    private void askRoomName(long userId, Bot bot) {
        state(userId).step = Step.ROOM_CREATE_NAME;
        send(bot, userId, "Введите название нового зала.", backKeyboard());
    }

    /// Sets the expected text-input step and displays its prompt.
    ///
    /// @param userId owner sender ID
    /// @param step next owner conversation step
    /// @param bot bot used to display the prompt
    /// @param prompt prompt text
    private void setTextStep(long userId, Step step, Bot bot, String prompt) {
        State state = state(userId);
        state.step = step;
        send(bot, userId, prompt, backKeyboard());
    }

    /// Saves the new room's comment requirement in the current flow.
    ///
    /// @param userId owner sender ID
    /// @param value selected yes/no choice
    /// @param bot bot used to display the next screen
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

    /// Continues adding rooms or advances to booking lead-time configuration.
    ///
    /// @param userId owner sender ID
    /// @param choice selected continuation option
    /// @param bot bot used to display the next prompt
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
            showLeadHours(userId, bot);
        } else {
            send(bot, userId, "Действие недоступно.", backKeyboard());
        }
    }

    /// Starts editing an active room's name.
    ///
    /// @param userId owner sender ID
    /// @param roomId room being edited
    /// @param bot bot used to request a new name
    private void beginRoomNameEdit(long userId, long roomId, Bot bot) {
        Room room = activeOwnedRoom(userId, roomId, bot);
        if (room != null) {
            setTextStep(userId, Step.ROOM_EDIT_NAME, bot, "Введите новое название зала.");
        }
    }

    /// Asks the owner to confirm deactivation of a room.
    ///
    /// @param userId owner sender ID
    /// @param roomId room selected for deletion
    /// @param bot bot used to display the confirmation
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

    /// Deactivates a room after confirming ownership and the current conversation step.
    ///
    /// @param userId owner sender ID
    /// @param roomId room being deactivated
    /// @param bot bot used to report the result
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

    /// Toggles whether bookings for a room require a comment.
    ///
    /// @param userId owner sender ID
    /// @param roomId room being updated
    /// @param bot bot used to display the updated room
    private void toggleRoom(long userId, long roomId, Bot bot) {
        Room room = activeOwnedRoom(userId, roomId, bot);
        if (room == null) {
            return;
        }
        roomRepository.update(new Room(room.id(), room.baseId(), room.name(), !room.commentRequired()));
        selectRoom(userId, room.id(), bot);
    }

    /// Validates and applies text entered while editing a base or room.
    ///
    /// @param userId owner sender ID
    /// @param text entered value
    /// @param bot bot used to display the result
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
                value = normalizeTimezone(value);
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

    /// Persists an updated base only when it belongs to the requesting owner.
    ///
    /// @param userId owner sender ID
    /// @param base updated base value
    void updateBase(long userId, Base base) {
        if (base.ownerId() != userId) {
            throw new IllegalStateException("Attempt to update a base owned by another user");
        }
        baseRepository.update(base);
    }

    /// Creates a base copy with selected fields changed.
    ///
    /// @param base source base
    /// @param name base name
    /// @param address base address
    /// @param timezone base time-zone ID
    /// @param leadHours booking lead time
    /// @param equipment equipment description
    /// @param schedule weekly opening schedule
    /// @return copied base value
    Base copy(Base base, String name, String address, String timezone, int leadHours, String equipment,
              Map<DayOfWeek, OpeningHours> schedule) {
        return new Base(base.id(), base.ownerId(), name, address, timezone, leadHours, equipment, schedule);
    }

    /// Copies a base's weekly schedule into a mutable map.
    ///
    /// @param base source base
    /// @return mutable schedule copy
    Map<DayOfWeek, OpeningHours> copySchedule(Base base) {
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        schedule.putAll(base.openingHours());
        return schedule;
    }

    /// Trims and canonicalizes a time-zone ID.
    ///
    /// @param input user-entered time-zone ID
    /// @return canonical time-zone ID
    private String normalizeTimezone(String input) {
        return ZoneId.of(input.trim()).getId();
    }

    /// Loads the selected base after verifying that it belongs to the owner.
    ///
    /// @param userId owner sender ID
    /// @param bot bot used to report access errors
    /// @return selected base or null when unavailable
    Base selectedBase(long userId, Bot bot) {
        long baseId = state(userId).selectedBaseId;
        if (baseId == 0 || !ownedBase(userId, baseId, bot)) {
            return null;
        }
        return baseRepository.findById(baseId).orElse(null);
    }

    /// Verifies that a base exists and belongs to the requesting user.
    ///
    /// @param userId owner sender ID
    /// @param baseId base being accessed
    /// @param bot bot used to report access errors
    /// @return true when the base belongs to the user
    private boolean ownedBase(long userId, long baseId, Bot bot) {
        Base base = baseRepository.findById(baseId).orElse(null);
        if (base == null || base.ownerId() != userId) {
            send(bot, userId, "Действие недоступно.", backKeyboard());
            return false;
        }
        return true;
    }

    /// Loads an active room that belongs to the selected base and owner.
    ///
    /// @param userId owner sender ID
    /// @param roomId room being accessed
    /// @param bot bot used to report access errors
    /// @return room when it is active and owned, otherwise null
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

    /// Rejects requests from users not configured as owners.
    ///
    /// @param userId Telegram sender ID
    /// @param bot bot used to report access errors
    /// @return true when the sender is authorized
    private boolean requireOwner(long userId, Bot bot) {
        if (!isOwner(userId)) {
            send(bot, userId, "Действие недоступно.", List.of());
            return false;
        }
        return true;
    }

    /// Navigates to the appropriate previous owner screen.
    ///
    /// @param userId owner sender ID
    /// @param bot bot used to display the previous screen
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

    /// Returns or creates the conversation state for one owner.
    ///
    /// @param userId owner sender ID
    /// @return mutable owner state
    State state(long userId) {
        return states.computeIfAbsent(userId, ignored -> new State());
    }

    /// Rejects blank text and returns a trimmed value otherwise.
    ///
    /// @param text user input
    /// @param prompt prompt to repeat for blank input
    /// @param userId owner sender ID
    /// @param bot bot used to report validation errors
    /// @return trimmed text or null when blank
    private String requireText(String text, String prompt, long userId, Bot bot) {
        if (text.isBlank()) {
            send(bot, userId, prompt, backKeyboard());
            return null;
        }
        return text.trim();
    }

    /// Builds the base list keyboard for an owner.
    ///
    /// @param userId owner sender ID
    /// @return keyboard rows
    private List<List<Button>> baseListButtons(long userId) {
        List<List<Button>> rows = new ArrayList<>();
        for (Base base : baseRepository.findByOwnerId(userId)) {
            keyboards.addButton(rows, button(base.name(), "owner:base:" + base.id()), 2);
        }
        rows.add(keyboards.row(button("Добавить базу", "owner:add")));
        rows.add(keyboards.row(button("Назад", "menu:home")));
        return rows;
    }

    /// Builds the active-room list keyboard for the selected base.
    ///
    /// @param userId owner sender ID
    /// @return keyboard rows
    private List<List<Button>> roomsButtons(long userId) {
        List<List<Button>> rows = new ArrayList<>();
        for (Room room : roomRepository.findByBaseId(state(userId).selectedBaseId)) {
            keyboards.addButton(rows, button(room.name(), "owner:room:" + room.id()), 2);
        }
        rows.add(keyboards.row(button("Добавить зал", "owner:addroom")));
        rows.add(keyboards.row(button("Назад", "owner:setting")));
        return rows;
    }

    /// Builds a one-button keyboard returning to the previous owner screen.
    ///
    /// @return keyboard rows
    List<List<Button>> backKeyboard() {
        return keyboards.choices(button("Назад", BACK));
    }

    /// Wraps buttons into rows using the shared keyboard factory.
    ///
    /// @param buttons buttons to display
    /// @return keyboard rows
    private List<List<Button>> choices(Button... buttons) {
        return keyboards.choices(buttons);
    }

    /// Creates a keyboard button.
    ///
    /// @param text visible button label
    /// @param data callback data
    /// @return button value
    Button button(String text, String data) {
        return keyboards.button(text, data);
    }

    /// Formats a weekday with its short Russian label.
    ///
    /// @param day weekday to format
    /// @return short Russian weekday name
    String formatterDay(DayOfWeek day) {
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

    /// Sends a message to the user's most recently active chat.
    ///
    /// @param bot bot used to send the message
    /// @param chatId user ID used to locate the reply chat
    /// @param text message text
    /// @param rows keyboard rows
    void send(Bot bot, long chatId, String text, List<List<Button>> rows) {
        State state = states.get(chatId);
        long replyChatId = state == null ? chatId : state.chatId;
        bot.sendMessage(new Message(text, null, rows), replyChatId);
    }

    /// Steps in the owner configuration wizard.
    enum Step {
        /// No text input is currently expected.
        IDLE, CREATE_NAME, CREATE_ADDRESS, CREATE_TIMEZONE, CREATE_OPENING, CREATE_CLOSING, CREATE_ROOM_NAME,
        CREATE_ROOM_REQUIRED, CREATE_MORE_ROOMS, CREATE_LEAD, CREATE_EQUIPMENT, ROOM_CREATE_NAME,
        ROOM_CREATE_REQUIRED, EDIT_NAME, EDIT_ADDRESS,
        EDIT_TIMEZONE, EDIT_EQUIPMENT, ROOM_EDIT_NAME, DELETE_ROOM
    }

    /// Mutable conversation data for one owner.
    final class State {
        /// Current step of the owner configuration wizard.
        Step step = Step.IDLE;
        /// Chat receiving owner replies.
        long chatId;
        /// ID of the base currently being managed.
        long selectedBaseId;
        /// ID of the room currently being managed.
        long selectedRoomId;
        /// ISO weekday currently being configured.
        int selectedDay;
        /// Current weekday in the base-creation wizard.
        int day;
        /// Draft opening hour for base creation.
        int openHour;
        /// Minimum booking lead time in the draft.
        int leadHours = 2;
        /// Draft base name.
        String name;
        /// Draft base address.
        String address;
        /// Draft base time-zone ID.
        String timezone;
        /// Draft equipment description.
        String equipment = "";
        /// Name of the room being created.
        String pendingRoomName;
        /// Draft weekly opening schedule.
        final Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        /// Rooms to persist together with a newly created base.
        final List<Room> newRooms = new ArrayList<>();

        /// Clears selected base and room while preserving the reply chat ID.
        void reset() {
            long previousChatId = chatId;
            selectedBaseId = 0;
            selectedRoomId = 0;
            selectedDay = 0;
            resetDraft();
            step = Step.IDLE;
            chatId = previousChatId;
        }

        /// Clears values that belong only to a new-base draft.
        void resetDraft() {
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

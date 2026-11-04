package ru.urfu.echobot.service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.OpeningHours;

/// Manages owner-facing weekly schedule configuration and its creation flow.
@SuppressWarnings({"checkstyle:MagicNumber", "checkstyle:MultipleStringLiterals", "checkstyle:ReturnCount"})
final class OwnerScheduleService {
    /// Parent service providing shared owner state and persistence helpers.
    private final OwnerManagementService owner;

    /// Creates the schedule manager with access to shared owner-management operations.
    ///
    /// @param owner parent owner-management service
    OwnerScheduleService(OwnerManagementService owner) {
        this.owner = owner;
    }

    /// Shows the first opening-hour selector for the base currently being created.
    ///
    /// @param userId owner sender ID
    /// @param bot bot used to send the prompt
    void promptCreateOpening(long userId, Bot bot) {
        OwnerManagementService.State state = owner.state(userId);
        state.step = OwnerManagementService.Step.CREATE_OPENING;
        DayOfWeek day = DayOfWeek.of(state.day);
        List<List<Button>> rows = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            owner.keyboards.addButton(rows, owner.button(hour + ":00", "owner:setopen:" + hour), 6);
        }
        owner.keyboards.addButton(rows, owner.button("Выходной", "owner:off"), 6);
        rows.add(owner.keyboards.row(owner.button("Назад", "owner:back")));
        owner.send(bot, userId, owner.formatterDay(day) + ". Час открытия:", rows);
    }

    /// Shows closing hours after the opening hour has been selected during base creation.
    ///
    /// @param userId owner sender ID
    /// @param openHour selected opening hour
    /// @param bot bot used to send the prompt
    void promptCreateClosing(long userId, int openHour, Bot bot) {
        OwnerManagementService.State state = owner.state(userId);
        state.step = OwnerManagementService.Step.CREATE_CLOSING;
        state.openHour = openHour;
        List<List<Button>> rows = new ArrayList<>();
        for (int hour = openHour + 1; hour <= 24; hour++) {
            owner.keyboards.addButton(rows, owner.button(hour + ":00", "owner:setclose:" + hour), 6);
        }
        rows.add(owner.keyboards.row(owner.button("Назад", "owner:back")));
        owner.send(bot, userId, owner.formatterDay(DayOfWeek.of(state.day)) + ". Час закрытия:", rows);
    }

    /// Advances the creation wizard to another weekday or to initial room creation.
    ///
    /// @param userId owner sender ID
    /// @param bot bot used to send the next prompt
    void finishCreateDay(long userId, Bot bot) {
        OwnerManagementService.State state = owner.state(userId);
        state.day++;
        if (state.day <= 7) {
            promptCreateOpening(userId, bot);
        } else {
            state.step = OwnerManagementService.Step.CREATE_ROOM_NAME;
            owner.send(bot, userId, "Введите название первого зала.", owner.backKeyboard());
        }
    }

    /// Displays all weekdays with their current opening schedules.
    ///
    /// @param userId owner sender ID
    /// @param bot bot used to display the schedule
    void showSchedule(long userId, Bot bot) {
        Base base = owner.selectedBase(userId, bot);
        if (base == null) {
            return;
        }
        List<List<Button>> rows = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            OpeningHours hours = base.hoursOn(day).orElse(null);
            String label = owner.formatterDay(day) + " " + (hours == null
                    ? "выходной" : hours.openHour() + ":00-" + hours.closeHour() + ":00");
            owner.keyboards.addButton(rows, owner.button(label, "owner:day:" + day.getValue()), 2);
        }
        rows.add(owner.keyboards.row(owner.button("Назад", "owner:setting")));
        owner.send(bot, userId, "График работы. Выберите день недели:", rows);
    }

    /// Displays schedule options for one weekday.
    ///
    /// @param userId owner sender ID
    /// @param day ISO weekday number
    /// @param bot bot used to display the day
    void showDay(long userId, int day, Bot bot) {
        Base base = owner.selectedBase(userId, bot);
        if (base == null || day < 1 || day > 7) {
            return;
        }
        OwnerManagementService.State state = owner.state(userId);
        state.selectedDay = day;
        state.step = OwnerManagementService.Step.IDLE;
        OpeningHours hours = base.hoursOn(DayOfWeek.of(day)).orElse(null);
        List<List<Button>> rows = new ArrayList<>();
        rows.add(owner.keyboards.row(owner.button("Час открытия", "owner:opening:" + day),
                owner.button("Час закрытия", "owner:closing:" + day)));
        rows.add(owner.keyboards.row(owner.button("Выходной", "owner:off:" + day)));
        rows.add(owner.keyboards.row(owner.button("Назад", "owner:schedule")));
        String text = owner.formatterDay(DayOfWeek.of(day)) + (hours == null
                ? ": выходной. Что изменить?" : ": " + hours.openHour() + ":00-" + hours.closeHour()
                + ":00. Что изменить?");
        owner.send(bot, userId, text, rows);
    }

    /// Displays valid opening hours for one weekday.
    ///
    /// @param userId owner sender ID
    /// @param day ISO weekday number
    /// @param bot bot used to display available hours
    void showOpeningHours(long userId, int day, Bot bot) {
        Base base = owner.selectedBase(userId, bot);
        if (base == null || day < 1 || day > 7) {
            return;
        }
        owner.state(userId).selectedDay = day;
        OpeningHours current = base.hoursOn(DayOfWeek.of(day)).orElse(new OpeningHours(9, 24));
        List<List<Button>> rows = new ArrayList<>();
        for (int hour = 0; hour < current.closeHour(); hour++) {
            owner.keyboards.addButton(rows, owner.button(hour + ":00", "owner:setopen:" + hour), 6);
        }
        rows.add(owner.keyboards.row(owner.button("Назад", "owner:day:" + day)));
        owner.send(bot, userId, owner.formatterDay(DayOfWeek.of(day)) + ". Новый час открытия:", rows);
    }

    /// Displays valid closing hours or explains that opening must be set first.
    ///
    /// @param userId owner sender ID
    /// @param day ISO weekday number
    /// @param bot bot used to display available hours
    void showClosingHours(long userId, int day, Bot bot) {
        Base base = owner.selectedBase(userId, bot);
        if (base == null || day < 1 || day > 7) {
            return;
        }
        OpeningHours current = base.hoursOn(DayOfWeek.of(day)).orElse(null);
        if (current == null) {
            owner.send(bot, userId, "Сначала задайте час открытия.", owner.backKeyboard());
            return;
        }
        owner.state(userId).selectedDay = day;
        List<List<Button>> rows = new ArrayList<>();
        for (int hour = current.openHour() + 1; hour <= 24; hour++) {
            owner.keyboards.addButton(rows, owner.button(hour + ":00", "owner:setclose:" + hour), 6);
        }
        rows.add(owner.keyboards.row(owner.button("Назад", "owner:day:" + day)));
        owner.send(bot, userId, owner.formatterDay(DayOfWeek.of(day)) + ". Час закрытия:", rows);
    }

    /// Removes a weekday from the draft schedule or the selected persisted base.
    ///
    /// @param userId owner sender ID
    /// @param day ISO weekday number, or zero during base creation
    /// @param bot bot used to report the updated state
    void closeDay(long userId, int day, Bot bot) {
        OwnerManagementService.State state = owner.state(userId);
        if (state.step == OwnerManagementService.Step.CREATE_OPENING && partsInCreation(state)) {
            state.schedule.remove(DayOfWeek.of(state.day));
            finishCreateDay(userId, bot);
            return;
        }
        Base base = owner.selectedBase(userId, bot);
        if (base == null || day < 1 || day > 7) {
            return;
        }
        Map<DayOfWeek, OpeningHours> schedule = owner.copySchedule(base);
        schedule.remove(DayOfWeek.of(day));
        owner.updateBase(userId, owner.copy(base, base.name(), base.address(), base.timezone(),
                base.bookingLeadHours(), base.equipment(), schedule));
        showDay(userId, day, bot);
    }

    /// Saves an opening hour in the creation draft or selected base schedule.
    ///
    /// @param userId owner sender ID
    /// @param hour opening hour
    /// @param bot bot used to report the updated state
    void setOpeningHour(long userId, int hour, Bot bot) {
        OwnerManagementService.State state = owner.state(userId);
        if (state.step == OwnerManagementService.Step.CREATE_OPENING) {
            if (hour < 0 || hour > 23) {
                owner.send(bot, userId, "Действие недоступно.", owner.backKeyboard());
                return;
            }
            promptCreateClosing(userId, hour, bot);
            return;
        }
        Base base = owner.selectedBase(userId, bot);
        int day = state.selectedDay;
        if (base == null || day < 1 || day > 7) {
            return;
        }
        OpeningHours current = base.hoursOn(DayOfWeek.of(day)).orElse(new OpeningHours(9, 24));
        if (hour < 0 || hour >= current.closeHour()) {
            showOpeningHours(userId, day, bot);
            return;
        }
        Map<DayOfWeek, OpeningHours> schedule = owner.copySchedule(base);
        schedule.put(DayOfWeek.of(day), new OpeningHours(hour, current.closeHour()));
        owner.updateBase(userId, owner.copy(base, base.name(), base.address(), base.timezone(),
                base.bookingLeadHours(), base.equipment(), schedule));
        showDay(userId, day, bot);
    }

    /// Saves a closing hour in the creation draft or selected base schedule.
    ///
    /// @param userId owner sender ID
    /// @param hour closing hour
    /// @param bot bot used to report the updated state
    void setClosingHour(long userId, int hour, Bot bot) {
        OwnerManagementService.State state = owner.state(userId);
        if (state.step == OwnerManagementService.Step.CREATE_CLOSING) {
            if (hour <= state.openHour || hour > 24) {
                owner.send(bot, userId, "Действие недоступно.", owner.backKeyboard());
                return;
            }
            state.schedule.put(DayOfWeek.of(state.day), new OpeningHours(state.openHour, hour));
            finishCreateDay(userId, bot);
            return;
        }
        Base base = owner.selectedBase(userId, bot);
        int day = state.selectedDay;
        if (base == null || day < 1 || day > 7) {
            return;
        }
        OpeningHours current = base.hoursOn(DayOfWeek.of(day)).orElse(null);
        if (current == null || hour <= current.openHour() || hour > 24) {
            owner.send(bot, userId, "Сначала задайте час открытия.", owner.backKeyboard());
            return;
        }
        Map<DayOfWeek, OpeningHours> schedule = owner.copySchedule(base);
        schedule.put(DayOfWeek.of(day), new OpeningHours(current.openHour(), hour));
        owner.updateBase(userId, owner.copy(base, base.name(), base.address(), base.timezone(),
                base.bookingLeadHours(), base.equipment(), schedule));
        showDay(userId, day, bot);
    }

    /// Indicates whether an opening-hours flow belongs to creation of a new base.
    ///
    /// @param state current owner conversation state
    /// @return true when the draft has no selected persisted base
    private boolean partsInCreation(OwnerManagementService.State state) {
        return state.selectedBaseId == 0;
    }
}

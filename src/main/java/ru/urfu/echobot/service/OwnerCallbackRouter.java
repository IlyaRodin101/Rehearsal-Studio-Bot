package ru.urfu.echobot.service;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import ru.urfu.echobot.bot.Bot;

/// Направляет callback-команды владельца профильным сервисам настройки.
final class OwnerCallbackRouter {
    /// Позиция идентификатора аргумента.
    private static final int ARGUMENT_PART = 2;
    /// Позиция часа в командах с дополнительным индексом.
    private static final int HOUR_ALTERNATE_PART = 3;
    /// Минимальное число частей команды с действием.
    private static final int MINIMAL_PARTS = 2;
    /// Число частей команды с дополнительным индексом часа.
    private static final int ALTERNATE_HOUR_PARTS = 4;

    /// Проверка прав владельца.
    private final OwnerAccess access;
    /// Основные операции с базами.
    private final OwnerBaseService baseService;
    /// Создание новой базы.
    private final OwnerBaseCreationService baseCreation;
    /// Редактирование базы.
    private final OwnerBaseEditingService editing;
    /// График работы.
    private final OwnerScheduleService schedule;
    /// Залы существующей базы.
    private final OwnerRoomService roomService;
    /// Залы новой базы.
    private final OwnerRoomCreationService roomCreation;
    /// Обработчики действий.
    private final Map<String, BiConsumer<OwnerCallback, Bot>> actions;

    /// Создаёт маршрутизатор команд владельца.
    ///
    /// @param access       проверка прав
    /// @param baseService  основные операции с базами
    /// @param baseCreation создание базы
    /// @param editing      редактирование базы
    /// @param schedule     график работы
    /// @param roomService  залы существующей базы
    /// @param roomCreation залы новой базы
    OwnerCallbackRouter(OwnerAccess access, OwnerBaseService baseService,
                        OwnerBaseCreationService baseCreation, OwnerBaseEditingService editing,
                        OwnerScheduleService schedule, OwnerRoomService roomService,
                        OwnerRoomCreationService roomCreation) {
        this.access = access;
        this.baseService = baseService;
        this.baseCreation = baseCreation;
        this.editing = editing;
        this.schedule = schedule;
        this.roomService = roomService;
        this.roomCreation = roomCreation;
        actions = new HashMap<>();
        registerActions();
    }

    /// Выполняет callback-команду владельца.
    ///
    /// @param userId идентификатор владельца
    /// @param parts  части callback-команды
    /// @param bot    бот для отправки
    void handle(long userId, String[] parts, Bot bot) {
        if (!access.requireOwner(userId, bot)) {
            return;
        }
        if (parts.length >= MINIMAL_PARTS) {
            execute(new OwnerCallback(userId, parts), bot);
        }
    }

    /// Находит и выполняет обработчик действия.
    ///
    /// @param callback callback-команда
    /// @param bot      бот для отправки
    private void execute(OwnerCallback callback, Bot bot) {
        BiConsumer<OwnerCallback, Bot> handler = actions.get(callback.action());
        try {
            if (handler == null) {
                baseService.unavailable(callback.userId(), bot);
            } else {
                handler.accept(callback, bot);
            }
        } catch (NumberFormatException | IndexOutOfBoundsException exception) {
            baseService.unavailable(callback.userId(), bot);
        }
    }

    /// Заполняет таблицу действий владельца.
    private void registerActions() {
        actions.put("list", (callback, bot) -> baseService.showBaseList(callback.userId(), bot));
        actions.put("back", (callback, bot) -> baseService.back(callback.userId(), bot));
        actions.put("add", (callback, bot) -> baseCreation.beginCreate(callback.userId(), bot));
        actions.put("base", (callback, bot) ->
                baseService.selectBase(callback.userId(), callback.longPart(ARGUMENT_PART), bot));
        actions.put("setting", (callback, bot) -> baseService.showBaseMenu(callback.userId(), bot));
        actions.put("timezone", (callback, bot) ->
                baseCreation.chooseTimezone(callback.userId(), callback.part(ARGUMENT_PART), bot));
        actions.put("deletebase", (callback, bot) ->
                baseService.confirmBaseDelete(callback.userId(), callback.longPart(ARGUMENT_PART), bot));
        actions.put("deletebaseyes", (callback, bot) ->
                baseService.deleteBase(callback.userId(), callback.longPart(ARGUMENT_PART), bot));
        actions.put("edit", (callback, bot) ->
                editing.beginEdit(callback.userId(), callback.part(ARGUMENT_PART), bot));
        actions.put("schedule", (callback, bot) -> schedule.showSchedule(callback.userId(), bot));
        actions.put("day", (callback, bot) ->
                schedule.showDay(callback.userId(), callback.intPart(ARGUMENT_PART), bot));
        actions.put("off", (callback, bot) -> schedule.closeDay(callback.userId(), day(callback), bot));
        actions.put("opening", (callback, bot) ->
                schedule.showOpeningHours(callback.userId(), callback.intPart(ARGUMENT_PART), bot));
        actions.put("closing", (callback, bot) ->
                schedule.showClosingHours(callback.userId(), callback.intPart(ARGUMENT_PART), bot));
        actions.put("setopen", (callback, bot) -> schedule.setOpeningHour(callback.userId(), hour(callback), bot));
        actions.put("setclose", (callback, bot) -> schedule.setClosingHour(callback.userId(), hour(callback), bot));
        actions.put("rooms", (callback, bot) -> roomService.showRooms(callback.userId(), bot));
        actions.put("room", (callback, bot) ->
                roomService.selectRoom(callback.userId(), callback.longPart(ARGUMENT_PART), bot));
        actions.put("addroom", (callback, bot) -> roomService.askRoomName(callback.userId(), bot));
        actions.put("roomname", (callback, bot) ->
                roomService.beginRoomNameEdit(callback.userId(), callback.longPart(ARGUMENT_PART), bot));
        actions.put("toggle", (callback, bot) ->
                roomService.toggleRoom(callback.userId(), callback.longPart(ARGUMENT_PART), bot));
        actions.put("delete", (callback, bot) ->
                roomService.confirmRoomDelete(callback.userId(), callback.longPart(ARGUMENT_PART), bot));
        actions.put("deleteyes", (callback, bot) ->
                roomService.deleteRoom(callback.userId(), callback.longPart(ARGUMENT_PART), bot));
        actions.put("newrequired", (callback, bot) ->
                roomService.setNewRoomRequirement(callback.userId(), callback.part(ARGUMENT_PART), bot));
        actions.put("newroom", (callback, bot) ->
                roomCreation.addAnotherRoom(callback.userId(), callback.part(ARGUMENT_PART), bot));
        actions.put("lead", (callback, bot) ->
                editing.selectLeadHours(callback.userId(), callback.intPart(ARGUMENT_PART), bot));
    }

    /// Добавляет действия настройки правил отмены.
    ///
    /// @param rules сервис правил отмены
    void addCancellationRules(OwnerCancellationRulesService rules) {
        actions.put("cancelrules", (callback, bot) -> rules.showRules(callback.userId(), bot));
        actions.put("cancelhours", (callback, bot) -> rules.showHours(callback.userId(), bot));
        actions.put("cancelhoursset", (callback, bot) ->
                rules.selectHours(callback.userId(), callback.intPart(ARGUMENT_PART), bot));
        actions.put("cancelpenalty", (callback, bot) -> rules.showPenalty(callback.userId(), bot));
        actions.put("cancelpenaltyset", (callback, bot) ->
                rules.selectPenalty(callback.userId(), callback.intPart(ARGUMENT_PART), bot));
    }

    /// Возвращает день недели из команды или 0 для черновика.
    ///
    /// @param callback callback-команда
    /// @return номер дня или 0
    private int day(OwnerCallback callback) {
        return callback.size() == MINIMAL_PARTS ? 0 : callback.intPart(ARGUMENT_PART);
    }

    /// Возвращает час из команды.
    ///
    /// @param callback callback-команда
    /// @return выбранный час
    private int hour(OwnerCallback callback) {
        int index = callback.size() == ALTERNATE_HOUR_PARTS ? HOUR_ALTERNATE_PART : ARGUMENT_PART;
        return callback.intPart(index);
    }
}

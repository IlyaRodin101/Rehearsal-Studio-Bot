package ru.urfu.echobot.service.owner;

import ru.urfu.echobot.service.GenericStateMachine;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import ru.urfu.echobot.bot.Bot;

/// Направляет текст владельца шагу его текущей формы.
final class OwnerTextRouter {
    /// Проверка прав владельца.
    private final OwnerAccess access;
    /// Сессии владельцев.
    private final OwnerSessions sessions;
    /// Автомат состояний.
    private final GenericStateMachine<OwnerSession.Step, OwnerSession.Event> stateMachine;
    /// Создание новой базы.
    private final OwnerBaseCreationService baseCreation;
    /// Редактирование базы.
    private final OwnerBaseEditingService editing;
    /// Залы существующей базы.
    private final OwnerRoomService roomService;
    /// Черновик новой базы.
    private final OwnerDraftScheduleService draftSchedule;
    /// Обработчики текста по шагам.
    private final Map<OwnerSession.Step, BiConsumer<OwnerText, Bot>> handlers;

    /// Создаёт маршрутизатор текста владельца.
    ///
    /// @param access        проверка прав
    /// @param sessions      сессии владельцев
    /// @param stateMachine  автомат состояний
    /// @param baseCreation  создание базы
    /// @param editing       редактирование базы
    /// @param roomService   залы существующей базы
    /// @param draftSchedule черновик новой базы
    OwnerTextRouter(OwnerAccess access, OwnerSessions sessions,
                    GenericStateMachine<OwnerSession.Step, OwnerSession.Event> stateMachine,
                    OwnerBaseCreationService baseCreation, OwnerBaseEditingService editing,
                    OwnerRoomService roomService, OwnerDraftScheduleService draftSchedule) {
        this.access = access;
        this.sessions = sessions;
        this.stateMachine = stateMachine;
        this.baseCreation = baseCreation;
        this.editing = editing;
        this.roomService = roomService;
        this.draftSchedule = draftSchedule;
        handlers = new HashMap<>();
        handlers.put(OwnerSession.Step.CREATE_NAME, (text, bot) ->
                baseCreation.handleName(text.userId(), text.value(), bot));
        handlers.put(OwnerSession.Step.CREATE_ADDRESS, (text, bot) ->
                baseCreation.handleAddress(text.userId(), text.value(), bot));
        handlers.put(OwnerSession.Step.CREATE_TIMEZONE, (text, bot) ->
                baseCreation.handleTimezone(text.userId(), text.value(), bot));
        handlers.put(OwnerSession.Step.CREATE_ROOM_NAME, (text, bot) ->
                draftSchedule.handleRoomName(text.userId(), text.value(), bot));
        handlers.put(OwnerSession.Step.CREATE_EQUIPMENT, (text, bot) ->
                baseCreation.handleEquipment(text.userId(), text.value(), bot));
        handlers.put(OwnerSession.Step.ROOM_CREATE_NAME, (text, bot) ->
                roomService.handleRoomName(text.userId(), text.value(), bot));
        handlers.put(OwnerSession.Step.EDIT_NAME, (text, bot) ->
                editing.handleEditText(text.userId(), text.value(), bot));
        handlers.put(OwnerSession.Step.EDIT_ADDRESS, (text, bot) ->
                editing.handleEditText(text.userId(), text.value(), bot));
        handlers.put(OwnerSession.Step.EDIT_TIMEZONE, (text, bot) ->
                editing.handleEditText(text.userId(), text.value(), bot));
        handlers.put(OwnerSession.Step.EDIT_EQUIPMENT, (text, bot) ->
                editing.handleEditText(text.userId(), text.value(), bot));
        handlers.put(OwnerSession.Step.ROOM_EDIT_NAME, (text, bot) ->
                editing.handleEditText(text.userId(), text.value(), bot));
    }

    /// Обрабатывает текст владельца, если у него есть активная форма.
    ///
    /// @param userId идентификатор владельца
    /// @param text   введённый текст
    /// @param bot    бот для отправки
    /// @return true, если текст обработан формой владельца
    boolean handle(long userId, String text, Bot bot) {
        OwnerSession session = sessions.existing(userId);
        if (session == null || session.step == OwnerSession.Step.IDLE) {
            return false;
        }
        if (!access.requireOwner(userId, bot)) {
            stateMachine.reset(session, OwnerSession.Step.IDLE);
            return true;
        }
        BiConsumer<OwnerText, Bot> handler = handlers.get(session.step);
        if (handler == null) {
            return false;
        }
        handler.accept(new OwnerText(userId, text), bot);
        return true;
    }

    /// Данные текстового сообщения владельца.
    ///
    /// @param userId идентификатор владельца
    /// @param value  введённый текст
    private record OwnerText(long userId, String value) {
    }
}

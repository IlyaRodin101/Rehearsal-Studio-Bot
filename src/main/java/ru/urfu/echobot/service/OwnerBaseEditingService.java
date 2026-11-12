package ru.urfu.echobot.service;

import java.time.DateTimeException;
import java.time.ZoneId;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Room;

/// Редактирует поля выбранной базы и её залов.
final class OwnerBaseEditingService {
    /// Сообщение о неверном часовом поясе.
    private static final String INVALID_TIMEZONE = "Неверный формат часового пояса. Пример: Europe/Moscow.";
    /// Сообщение о пустом значении.
    private static final String EMPTY_VALUE = "Значение не может быть пустым.";
    /// Сообщение об пустом названии зала.
    private static final String EMPTY_ROOM_NAME = "Название зала не может быть пустым.";
    /// Текст запроса оборудования.
    private static final String ASK_EQUIPMENT = "Введите список оборудования (можно оставить пустым).";

    /// Сессии владельцев.
    private final OwnerSessions sessions;
    /// Проверка прав и принадлежности.
    private final OwnerAccess access;
    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Автомат состояний.
    private final OwnerStateMachine stateMachine;
    /// Основные операции с базами.
    private final OwnerBaseService baseService;
    /// Операции с залами.
    private final OwnerRoomService roomService;
    /// Срок бронирования.
    private final OwnerLeadService leadService;

    /// Создаёт сервис редактирования.
    ///
    /// @param sessions     сессии владельцев
    /// @param access       проверка прав
    /// @param messaging    сообщения и клавиатуры
    /// @param stateMachine автомат состояний
    /// @param baseService  основные операции с базами
    /// @param roomService  операции с залами
    /// @param leadService  срок бронирования
    OwnerBaseEditingService(OwnerSessions sessions, OwnerAccess access, OwnerMessaging messaging,
                            OwnerStateMachine stateMachine, OwnerBaseService baseService,
                            OwnerRoomService roomService, OwnerLeadService leadService) {
        this.sessions = sessions;
        this.access = access;
        this.messaging = messaging;
        this.stateMachine = stateMachine;
        this.baseService = baseService;
        this.roomService = roomService;
        this.leadService = leadService;
    }

    /// Начинает редактирование поля базы.
    ///
    /// @param userId идентификатор владельца
    /// @param field  имя поля
    /// @param bot    бот для отправки
    void beginEdit(long userId, String field, Bot bot) {
        OwnerSession session = sessions.get(userId);
        Base base = access.selectedBase(userId, session, bot);
        if (base != null) {
            if ("lead".equals(field)) {
                messaging.showLeadHours(userId, bot);
            } else {
                startTextEdit(userId, field, bot);
            }
        }
    }

    /// Выбирает срок бронирования для новой или существующей базы.
    ///
    /// @param userId идентификатор владельца
    /// @param hours  срок в часах
    /// @param bot    бот для отправки
    void selectLeadHours(long userId, int hours, Bot bot) {
        leadService.selectLeadHours(userId, hours, bot);
    }

    /// Сохраняет отредактированное текстовое значение.
    ///
    /// @param userId идентификатор владельца
    /// @param text   введённый текст
    /// @param bot    бот для отправки
    void handleEditText(long userId, String text, Bot bot) {
        OwnerSession session = sessions.get(userId);
        String value = text.trim();
        if (session.step == OwnerStep.ROOM_EDIT_NAME) {
            saveRoomName(userId, session, value, bot);
        } else {
            saveBaseField(userId, session, text, value, bot);
        }
    }

    /// Сохраняет новое название зала.
    ///
    /// @param userId  идентификатор владельца
    /// @param session сессия владельца
    /// @param value   новое название
    /// @param bot     бот для отправки
    private void saveRoomName(long userId, OwnerSession session, String value, Bot bot) {
        Room room = access.activeOwnedRoom(userId, session, session.selectedRoomId, bot);
        if (value.isEmpty()) {
            messaging.send(bot, userId, EMPTY_ROOM_NAME, messaging.backKeyboard());
        } else if (room != null && stateMachine.transition(session, OwnerStateMachine.Event.SAVE_TEXT)) {
            roomService.renameRoom(room, value);
            roomService.selectRoom(userId, room.id(), bot);
        }
    }

    /// Сохраняет отредактированное поле базы.
    ///
    /// @param userId  идентификатор владельца
    /// @param session сессия владельца
    /// @param text    исходный текст
    /// @param value   текст без внешних пробелов
    /// @param bot     бот для отправки
    private void saveBaseField(long userId, OwnerSession session, String text, String value, Bot bot) {
        Base base = access.selectedBase(userId, session, bot);
        String timezone = editedTimezone(userId, session, value, bot);
        if (base == null || timezone == null || !validBaseValue(userId, session, value, bot)) {
            return;
        }
        Base edited = baseService.copy(base,
                session.step == OwnerStep.EDIT_NAME ? value : base.name(),
                session.step == OwnerStep.EDIT_ADDRESS ? value : base.address(),
                session.step == OwnerStep.EDIT_TIMEZONE ? timezone : base.timezone(),
                base.bookingLeadHours(),
                session.step == OwnerStep.EDIT_EQUIPMENT ? text : base.equipment(),
                base.openingHours());
        if (stateMachine.transition(session, OwnerStateMachine.Event.SAVE_TEXT)) {
            baseService.updateBase(userId, edited);
            baseService.showBaseMenu(userId, bot);
        }
    }

    /// Проверяет и нормализует часовой пояс.
    ///
    /// @param userId  идентификатор владельца
    /// @param session сессия владельца
    /// @param value   введённый часовой пояс
    /// @param bot     бот для отправки
    /// @return нормализованный пояс или null, если он не нужен или неверен
    private String editedTimezone(long userId, OwnerSession session, String value, Bot bot) {
        String timezone = value;
        if (session.step == OwnerStep.EDIT_TIMEZONE) {
            try {
                timezone = ZoneId.of(value).getId();
            } catch (DateTimeException exception) {
                messaging.send(bot, userId, INVALID_TIMEZONE, messaging.backKeyboard());
                timezone = null;
            }
        }
        return timezone;
    }

    /// Проверяет, что обязательное текстовое значение не пустое.
    ///
    /// @param userId  идентификатор владельца
    /// @param session сессия владельца
    /// @param value   введённое значение
    /// @param bot     бот для отправки
    /// @return true, если значение допустимо
    private boolean validBaseValue(long userId, OwnerSession session, String value, Bot bot) {
        boolean empty = value.isEmpty() && session.step != OwnerStep.EDIT_EQUIPMENT;
        if (empty) {
            messaging.send(bot, userId, EMPTY_VALUE, messaging.backKeyboard());
        }
        return !empty;
    }

    /// Переводит сессию к редактированию текстового поля.
    ///
    /// @param userId идентификатор владельца
    /// @param field  имя поля
    /// @param bot    бот для отправки
    private void startTextEdit(long userId, String field, Bot bot) {
        OwnerSession session = sessions.get(userId);
        if ("name".equals(field)
                && stateMachine.transition(session, OwnerStateMachine.Event.EDIT_NAME)) {
            messaging.send(bot, userId, "Введите новое название базы.", messaging.backKeyboard());
        } else if ("address".equals(field)
                && stateMachine.transition(session, OwnerStateMachine.Event.EDIT_ADDRESS)) {
            messaging.send(bot, userId, "Введите новый адрес базы.", messaging.backKeyboard());
        } else if ("timezone".equals(field)
                && stateMachine.transition(session, OwnerStateMachine.Event.EDIT_TIMEZONE)) {
            messaging.send(bot, userId, "Введите новый часовой пояс базы. Например: Europe/Moscow.",
                    messaging.backKeyboard());
        } else if ("equipment".equals(field)
                && stateMachine.transition(session, OwnerStateMachine.Event.EDIT_EQUIPMENT)) {
            messaging.send(bot, userId, ASK_EQUIPMENT, messaging.backKeyboard());
        }
    }
}

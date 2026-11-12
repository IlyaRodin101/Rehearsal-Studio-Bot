package ru.urfu.echobot.service;

import java.util.Map;
import java.util.Objects;

/// Управляет переходами между шагами настройки баз владельцем.
final class OwnerStateMachine {
    /// Имя параметра сессии в проверках.
    private static final String SESSION = "session";

    /// События, вызывающие переход между шагами.
    enum Event {
        /// Владелец начал создание базы.
        BEGIN_CREATE,
        /// Введено название базы.
        ENTER_BASE_NAME,
        /// Введён адрес базы.
        ENTER_BASE_ADDRESS,
        /// Выбран часовой пояс по умолчанию.
        USE_DEFAULT_TIMEZONE,
        /// Владелец решил ввести часовой пояс вручную.
        ENTER_CUSTOM_TIMEZONE,
        /// Введён часовой пояс.
        ENTER_TIMEZONE,
        /// Выбран час открытия.
        ENTER_OPENING,
        /// Выбран час закрытия.
        ENTER_CLOSING,
        /// День графика завершён и есть следующие дни.
        FINISH_DAY,
        /// Все дни графика заполнены.
        LAST_DAY,
        /// Введено название зала новой базы.
        ENTER_ROOM_NAME,
        /// Выбрана обязательность комментария для зала.
        SET_ROOM_REQUIREMENT,
        /// Владелец добавляет ещё один зал новой базы.
        ADD_ROOM,
        /// Список залов новой базы готов.
        FINISH_ROOMS,
        /// Выбран срок бронирования новой базы.
        ENTER_LEAD,
        /// Введено оборудование новой базы.
        ENTER_EQUIPMENT,
        /// Начато редактирование названия.
        EDIT_NAME,
        /// Начато редактирование адреса.
        EDIT_ADDRESS,
        /// Начато редактирование часового пояса.
        EDIT_TIMEZONE,
        /// Начато редактирование оборудования.
        EDIT_EQUIPMENT,
        /// Начато редактирование названия зала.
        EDIT_ROOM_NAME,
        /// Начато добавление зала существующей базы.
        ADD_ROOM_TO_BASE,
        /// Текстовое значение сохранено.
        SAVE_TEXT,
        /// Запрошено удаление зала.
        DELETE_ROOM,
        /// Удаление зала подтверждено.
        DELETE_ROOM_CONFIRMED,
        /// Запрошено удаление базы.
        DELETE_BASE,
        /// Удаление базы подтверждено.
        DELETE_BASE_CONFIRMED
    }

    /// Таблица допустимых переходов.
    private final Map<OwnerStep, Map<Event, OwnerStep>> transitions;

    /// Создаёт автомат и заполняет таблицу переходов.
    OwnerStateMachine() {
        transitions = Map.ofEntries(
                Map.entry(OwnerStep.IDLE, Map.ofEntries(
                        Map.entry(Event.BEGIN_CREATE, OwnerStep.CREATE_NAME),
                        Map.entry(Event.USE_DEFAULT_TIMEZONE, OwnerStep.CREATE_OPENING),
                        Map.entry(Event.ENTER_CUSTOM_TIMEZONE, OwnerStep.CREATE_TIMEZONE),
                        Map.entry(Event.EDIT_NAME, OwnerStep.EDIT_NAME),
                        Map.entry(Event.EDIT_ADDRESS, OwnerStep.EDIT_ADDRESS),
                        Map.entry(Event.EDIT_TIMEZONE, OwnerStep.EDIT_TIMEZONE),
                        Map.entry(Event.EDIT_EQUIPMENT, OwnerStep.EDIT_EQUIPMENT),
                        Map.entry(Event.EDIT_ROOM_NAME, OwnerStep.ROOM_EDIT_NAME),
                        Map.entry(Event.ADD_ROOM_TO_BASE, OwnerStep.ROOM_CREATE_NAME),
                        Map.entry(Event.DELETE_ROOM, OwnerStep.DELETE_ROOM),
                        Map.entry(Event.DELETE_BASE, OwnerStep.DELETE_BASE))),
                Map.entry(OwnerStep.CREATE_NAME, Map.of(
                        Event.ENTER_BASE_NAME, OwnerStep.CREATE_ADDRESS)),
                Map.entry(OwnerStep.CREATE_ADDRESS, Map.of(
                        Event.ENTER_BASE_ADDRESS, OwnerStep.IDLE)),
                Map.entry(OwnerStep.CREATE_TIMEZONE, Map.of(
                        Event.ENTER_TIMEZONE, OwnerStep.CREATE_OPENING)),
                Map.entry(OwnerStep.CREATE_OPENING, Map.of(
                        Event.ENTER_OPENING, OwnerStep.CREATE_CLOSING,
                        Event.FINISH_DAY, OwnerStep.CREATE_OPENING,
                        Event.LAST_DAY, OwnerStep.CREATE_ROOM_NAME)),
                Map.entry(OwnerStep.CREATE_CLOSING, Map.of(
                        Event.FINISH_DAY, OwnerStep.CREATE_OPENING)),
                Map.entry(OwnerStep.CREATE_ROOM_NAME, Map.of(
                        Event.ENTER_ROOM_NAME, OwnerStep.CREATE_ROOM_REQUIRED)),
                Map.entry(OwnerStep.CREATE_ROOM_REQUIRED, Map.of(
                        Event.SET_ROOM_REQUIREMENT, OwnerStep.CREATE_MORE_ROOMS)),
                Map.entry(OwnerStep.CREATE_MORE_ROOMS, Map.of(
                        Event.ADD_ROOM, OwnerStep.CREATE_ROOM_NAME,
                        Event.FINISH_ROOMS, OwnerStep.CREATE_LEAD)),
                Map.entry(OwnerStep.CREATE_LEAD, Map.of(
                        Event.ENTER_LEAD, OwnerStep.CREATE_EQUIPMENT)),
                Map.entry(OwnerStep.CREATE_EQUIPMENT, Map.of(
                        Event.ENTER_EQUIPMENT, OwnerStep.IDLE)),
                Map.entry(OwnerStep.ROOM_CREATE_NAME, Map.of(
                        Event.ENTER_ROOM_NAME, OwnerStep.ROOM_CREATE_REQUIRED)),
                Map.entry(OwnerStep.ROOM_CREATE_REQUIRED, Map.of(
                        Event.SET_ROOM_REQUIREMENT, OwnerStep.IDLE)),
                Map.entry(OwnerStep.EDIT_NAME, Map.of(Event.SAVE_TEXT, OwnerStep.IDLE)),
                Map.entry(OwnerStep.EDIT_ADDRESS, Map.of(Event.SAVE_TEXT, OwnerStep.IDLE)),
                Map.entry(OwnerStep.EDIT_TIMEZONE, Map.of(Event.SAVE_TEXT, OwnerStep.IDLE)),
                Map.entry(OwnerStep.EDIT_EQUIPMENT, Map.of(Event.SAVE_TEXT, OwnerStep.IDLE)),
                Map.entry(OwnerStep.ROOM_EDIT_NAME, Map.of(Event.SAVE_TEXT, OwnerStep.IDLE)),
                Map.entry(OwnerStep.DELETE_ROOM, Map.of(
                        Event.DELETE_ROOM_CONFIRMED, OwnerStep.IDLE)),
                Map.entry(OwnerStep.DELETE_BASE, Map.of(
                        Event.DELETE_BASE_CONFIRMED, OwnerStep.IDLE)));
    }

    /// Выполняет переход между шагами настройки.
    ///
    /// @param session сессия владельца
    /// @param event   событие перехода
    /// @return true, если переход разрешён и выполнен
    boolean transition(OwnerSession session, Event event) {
        Objects.requireNonNull(session, SESSION);
        Objects.requireNonNull(event, "event");
        Map<Event, OwnerStep> stateTransitions = transitions.get(session.step);
        if (stateTransitions == null) {
            return false;
        }
        OwnerStep nextStep = stateTransitions.get(event);
        if (nextStep == null) {
            return false;
        }
        session.step = nextStep;
        return true;
    }

    /// Возвращает сессию в состояние без активной формы.
    ///
    /// @param session сессия владельца
    void reset(OwnerSession session) {
        Objects.requireNonNull(session, SESSION);
        session.step = OwnerStep.IDLE;
    }
}

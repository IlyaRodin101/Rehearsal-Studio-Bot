package ru.urfu.echobot.service.owner;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.service.State;

/// Хранит незавершённую форму и выбранные объекты одного владельца базы.
final class OwnerSession implements State<OwnerSession.Step> {
    /// Шаги диалога владельца базы.
    enum Step {
        /// Владелец не заполняет форму.
        IDLE,
        /// Ввод названия новой базы.
        CREATE_NAME,
        /// Ввод адреса новой базы.
        CREATE_ADDRESS,
        /// Ввод часового пояса новой базы.
        CREATE_TIMEZONE,
        /// Выбор часа открытия при создании базы.
        CREATE_OPENING,
        /// Выбор часа закрытия при создании базы.
        CREATE_CLOSING,
        /// Ввод названия зала новой базы.
        CREATE_ROOM_NAME,
        /// Выбор обязательности комментария для зала новой базы.
        CREATE_ROOM_REQUIRED,
        /// Добавление ещё одного зала новой базы.
        CREATE_MORE_ROOMS,
        /// Выбор срока бронирования новой базы.
        CREATE_LEAD,
        /// Ввод оборудования новой базы.
        CREATE_EQUIPMENT,
        /// Ввод названия нового зала существующей базы.
        ROOM_CREATE_NAME,
        /// Выбор обязательности комментария для нового зала.
        ROOM_CREATE_REQUIRED,
        /// Редактирование названия базы.
        EDIT_NAME,
        /// Редактирование адреса базы.
        EDIT_ADDRESS,
        /// Редактирование часового пояса базы.
        EDIT_TIMEZONE,
        /// Редактирование оборудования базы.
        EDIT_EQUIPMENT,
        /// Редактирование названия зала.
        ROOM_EDIT_NAME,
        /// Подтверждение удаления зала.
        DELETE_ROOM,
        /// Подтверждение удаления базы.
        DELETE_BASE
    }

    /// События, вызывающие переход между шагами настройки базы.
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

    /// Текущий шаг диалога.
    Step step = Step.IDLE;
    /// Идентификатор выбранной базы.
    long selectedBaseId;
    /// Идентификатор выбранного зала.
    long selectedRoomId;
    /// Выбранный день недели при редактировании графика.
    int selectedDay;
    /// День недели при создании графика.
    int day;
    /// Час открытия при создании графика.
    int openHour;
    /// Срок бронирования новой базы.
    int leadHours = 2;
    /// Название новой базы.
    String name;
    /// Адрес новой базы.
    String address;
    /// Часовой пояс новой базы.
    String timezone;
    /// Оборудование новой базы.
    String equipment = "";
    /// Название зала, ожидающего выбора обязательности комментария.
    String pendingRoomName;
    /// График новой базы.
    final Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
    /// Залы, добавленные до сохранения новой базы.
    final List<Room> newRooms = new ArrayList<>();

    /// Возвращает текущий шаг настройки.
    ///
    /// @return текущий шаг
    @Override
    public Step state() {
        return step;
    }

    /// Устанавливает текущий шаг настройки.
    ///
    /// @param value новый шаг
    @Override
    public void state(Step value) {
        step = value;
    }

    /// Полностью сбрасывает выбор и незавершённую форму.
    void reset() {
        selectedBaseId = 0;
        selectedRoomId = 0;
        selectedDay = 0;
        resetDraft();
        step = Step.IDLE;
    }

    /// Очищает данные создаваемой базы, сохраняя выбранные объекты.
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

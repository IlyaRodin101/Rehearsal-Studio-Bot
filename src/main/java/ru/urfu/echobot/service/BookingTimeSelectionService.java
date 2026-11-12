package ru.urfu.echobot.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Показывает свободное время начала и доступную длительность бронирования.
public final class BookingTimeSelectionService {
    /// Подпись кнопки возврата.
    private static final String BACK = "Назад";
    /// Callback-префикс выбора времени.
    private static final String BOOK_TIME = "book:time:";
    /// Callback-префикс выбора длительности.
    private static final String BOOK_HOURS = "book:hours:";
    /// Максимальное число кнопок времени в строке.
    private static final int TIME_BUTTONS_PER_ROW = 4;
    /// Максимальное число кнопок длительности в строке.
    private static final int DURATIONS_PER_ROW = 6;

    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Хранилище залов.
    private final RoomRepository roomRepository;
    /// Хранилище броней.
    private final BookingRepository bookingRepository;
    /// Проверка доступности времени.
    private final BookingAvailabilityService availabilityService;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards;
    /// Форматер времени.
    private final RussianDateFormatter formatter;

    /// Создаёт сервис выбора времени и длительности.
    ///
    /// @param baseRepository      хранилище баз
    /// @param roomRepository      хранилище залов
    /// @param bookingRepository   хранилище броней
    /// @param availabilityService проверка доступности
    /// @param keyboards           фабрика клавиатур
    /// @param formatter           форматер времени
    public BookingTimeSelectionService(BaseRepository baseRepository, RoomRepository roomRepository,
                                       BookingRepository bookingRepository,
                                       BookingAvailabilityService availabilityService,
                                       BookingKeyboardFactory keyboards, RussianDateFormatter formatter) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.availabilityService = availabilityService;
        this.keyboards = keyboards;
        this.formatter = formatter;
    }

    /// Показывает свободное время для выбранного дня.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщения
    public void showTimeChoices(long chatId, BookingFlow flow, Bot bot) {
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        Room room = roomRepository.findById(flow.roomId).orElse(null);
        List<List<Button>> rows = new ArrayList<>();
        if (base != null && room != null) {
            for (int hour : availabilityService.availableStarts(base, room, flow.bookingDate)) {
                keyboards.addButton(rows,
                        keyboards.button(formatter.hour(hour), BOOK_TIME + flow.bookingDate + ":" + hour),
                        TIME_BUTTONS_PER_ROW);
            }
        }
        rows.add(keyboards.row(keyboards.button(BACK, "book:week:" + flow.selectedWeek)));
        String text = rows.size() == 1 ? "На этот день свободного времени нет." : "Выберите время начала:";
        send(bot, chatId, text, keyboards.markup(rows));
    }

    /// Показывает доступную длительность для выбранного времени начала.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщения
    public void showDurationChoices(long chatId, BookingFlow flow, Bot bot) {
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        OpeningHours hours = base == null ? null : base.hoursOn(flow.bookingDate.getDayOfWeek()).orElse(null);
        List<List<Button>> rows = new ArrayList<>();
        if (hours != null) {
            LocalDateTime start = flow.bookingDate.atTime(flow.startHour, 0);
            int availableHours = hours.closeHour() - flow.startHour;
            for (int duration = 1; duration <= availableHours; duration++) {
                if (!bookingRepository.hasConflict(flow.roomId, start, start.plusHours(duration))) {
                    keyboards.addButton(rows, keyboards.button(String.valueOf(duration),
                            BOOK_HOURS + duration), DURATIONS_PER_ROW);
                }
            }
        }
        rows.add(keyboards.row(keyboards.button(BACK, "book:day:" + flow.bookingDate)));
        String text = rows.size() == 1
                ? "До закрытия или свободного времени зала осталось меньше часа."
                : "Сколько часов?";
        send(bot, chatId, text, keyboards.markup(rows));
    }

    /// Отправляет сообщение с клавиатурой.
    ///
    /// @param bot      бот для отправки
    /// @param chatId   идентификатор чата
    /// @param text     текст сообщения
    /// @param keyboard строки кнопок
    private void send(Bot bot, long chatId, String text, List<List<Button>> keyboard) {
        bot.sendMessage(new Message(text, null, keyboard), chatId);
    }
}

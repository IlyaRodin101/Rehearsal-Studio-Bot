package ru.urfu.echobot.service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Формирует сведения о будущих бронях и справочную информацию о репетиционных базах.
public final class BookingInformationService {
    /// Число дней в неделе.
    private static final int DAYS_IN_WEEK = 7;
    /// Текст приглашения выбрать базу.
    private static final String SELECT_BASE = "Выберите базу:";
    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Хранилище залов.
    private final RoomRepository roomRepository;
    /// Хранилище бронирований.
    private final BookingRepository bookingRepository;
    /// Правила определения будущих броней.
    private final BookingRules rules;
    /// Форматер дат и времени.
    private final RussianDateFormatter formatter;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards;
    /// строковые константы, чтобы не ругался checkstyle
    private final String home = "menu:home";
    private final String back = "Назад";

    /// Создаёт сервис отображения броней и информации о базах.
    ///
    /// @param baseRepository    хранилище баз
    /// @param roomRepository    хранилище залов
    /// @param bookingRepository хранилище броней
    /// @param rules             правила бронирования
    /// @param formatter         форматер дат и времени
    /// @param keyboards         фабрика клавиатур
    public BookingInformationService(BaseRepository baseRepository, RoomRepository roomRepository,
                                     BookingRepository bookingRepository, BookingRules rules,
                                     RussianDateFormatter formatter, BookingKeyboardFactory keyboards) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.rules = rules;
        this.formatter = formatter;
        this.keyboards = keyboards;
    }

    /// Возвращает текст со списком будущих броней выбранного пользователя.
    ///
    /// @param userId идентификатор пользователя
    /// @return текст для раздела «Мои записи»
    public String myBookingsMessage(long userId) {
        StringBuilder text = new StringBuilder("Ваши будущие записи:\n");
        boolean hasBookings = false;
        for (Booking booking : bookingRepository.findByUser(userId)) {
            Room room = roomRepository.findById(booking.roomId()).orElse(null);
            Base base = room == null ? null : baseRepository.findById(room.baseId()).orElse(null);
            if (base == null || !rules.isUpcoming(base, booking)) {
                continue;
            }
            hasBookings = true;
            text.append("• ").append(base.name()).append(", ").append(room.name()).append(", ")
                    .append(formatter.dateTime(booking.start())).append("-")
                    .append(formatter.endTime(booking.start(), booking.end())).append("\n");
        }
        return hasBookings ? text.toString() : "У вас пока нет будущих записей.";
    }

    /// Показывает список баз для просмотра справочной информации.
    ///
    /// @param chatId идентификатор Telegram-чата
    /// @param bot    бот для отправки сообщений
    public void showAboutBases(long chatId, Bot bot) {
        List<Base> bases = baseRepository.findAll();
        if (bases.isEmpty()) {
            send(bot, chatId, "Пока нет доступных баз.",
                    keyboards.choices(keyboards.button(back, home)));
            return;
        }
        List<List<Button>> rows = new ArrayList<>();
        for (Base base : bases) {
            keyboards.addButton(rows, keyboards.button(base.name(), "about:base:" + base.id()), 2);
        }
        rows.add(keyboards.row(keyboards.button(back, home)));
        send(bot, chatId, SELECT_BASE, keyboards.markup(rows));
    }

    /// Показывает адрес, расписание и оборудование выбранной базы.
    ///
    /// @param chatId идентификатор Telegram-чата
    /// @param baseId идентификатор базы
    /// @param bot    бот для отправки сообщений
    public void showBaseInformation(long chatId, long baseId, Bot bot) {
        Base base = baseRepository.findById(baseId).orElse(null);
        if (base == null) {
            showAboutBases(chatId, bot);
            return;
        }
        StringBuilder text = new StringBuilder("База «").append(base.name()).append("»\nАдрес: ")
                .append(base.address()).append("\nЧасовой пояс: ").append(base.timezone()).append("\n");
        for (int day = 1; day <= DAYS_IN_WEEK; day++) {
            OpeningHours hours = base.hoursOn(DayOfWeek.of(day)).orElse(null);
            text.append(formatter.dayName(DayOfWeek.of(day))).append(": ")
                    .append(hours == null
                            ? "выходной"
                            : formatter.hour(hours.openHour()) + "-" + formatter.hour(hours.closeHour()))
                    .append(day == DAYS_IN_WEEK ? "\n" : ", ");
        }
        text.append("Оборудование:\n").append(base.equipment().isEmpty() ? "не указано" : base.equipment());
        send(bot, chatId, text.toString(), keyboards.choices(keyboards.button(back, "about:list")));
    }

    /// Отправляет текст и клавиатуру через бот.
    ///
    /// @param bot      транспорт для отправки
    /// @param chatId   идентификатор Telegram-чата
    /// @param text     текст сообщения
    /// @param keyboard клавиатура сообщения
    private void send(Bot bot, long chatId, String text, List<List<Button>> keyboard) {
        bot.sendMessage(new Message(text, null, keyboard), chatId);
    }
}

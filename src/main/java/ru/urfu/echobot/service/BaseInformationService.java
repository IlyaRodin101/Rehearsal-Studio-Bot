package ru.urfu.echobot.service;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.repository.BaseRepository;

/// Показывает справочную информацию о репетиционных базах.
public final class BaseInformationService {
    /// Число дней в неделе.
    private static final int DAYS_IN_WEEK = 7;
    /// Текст приглашения выбрать базу.
    private static final String SELECT_BASE = "Выберите базу:";
    /// Подпись кнопки возврата.
    private static final String BACK = "Назад";
    /// Callback главного меню.
    private static final String MENU_HOME = "menu:home";
    /// Callback списка баз в разделе «О базе».
    private static final String ABOUT_LIST = "about:list";

    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Форматер дат и времени.
    private final RussianDateFormatter formatter;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards;

    /// Создаёт сервис справочной информации.
    ///
    /// @param baseRepository хранилище баз
    /// @param formatter      форматер дат и времени
    /// @param keyboards      фабрика клавиатур
    public BaseInformationService(BaseRepository baseRepository, RussianDateFormatter formatter,
                                  BookingKeyboardFactory keyboards) {
        this.baseRepository = baseRepository;
        this.formatter = formatter;
        this.keyboards = keyboards;
    }

    /// Показывает список баз для просмотра справочной информации.
    ///
    /// @param chatId идентификатор чата
    /// @param bot    бот для отправки сообщений
    public void showAboutBases(long chatId, Bot bot) {
        List<Base> bases = baseRepository.findAll();
        if (bases.isEmpty()) {
            send(bot, chatId, "Пока нет доступных баз.",
                    keyboards.choices(keyboards.button(BACK, MENU_HOME)));
        } else {
            List<List<Button>> rows = new ArrayList<>();
            for (Base base : bases) {
                keyboards.addButton(rows, keyboards.button(base.name(), "about:base:" + base.id()), 2);
            }
            rows.add(keyboards.row(keyboards.button(BACK, MENU_HOME)));
            send(bot, chatId, SELECT_BASE, keyboards.markup(rows));
        }
    }

    /// Показывает адрес, расписание и оборудование выбранной базы.
    ///
    /// @param chatId идентификатор чата
    /// @param baseId идентификатор базы
    /// @param bot    бот для отправки сообщений
    public void showBaseInformation(long chatId, long baseId, Bot bot) {
        Base base = baseRepository.findById(baseId).orElse(null);
        if (base == null) {
            showAboutBases(chatId, bot);
        } else {
            StringBuilder text = new StringBuilder("База «").append(base.name()).append("»\nАдрес: ")
                    .append(base.address()).append("\nЧасовой пояс: ").append(base.timezone()).append("\n");
            appendSchedule(text, base);
            text.append("Оборудование:\n").append(base.equipment().isEmpty() ? "не указано" : base.equipment());
            send(bot, chatId, text.toString(), keyboards.choices(keyboards.button(BACK, ABOUT_LIST)));
        }
    }

    /// Добавляет к тексту часы работы по дням недели.
    ///
    /// @param text текст сообщения
    /// @param base база с графиком
    private void appendSchedule(StringBuilder text, Base base) {
        for (int day = 1; day <= DAYS_IN_WEEK; day++) {
            OpeningHours hours = base.hoursOn(DayOfWeek.of(day)).orElse(null);
            text.append(formatter.dayName(DayOfWeek.of(day))).append(": ")
                    .append(hours == null
                            ? "выходной"
                            : formatter.hour(hours.openHour()) + "-" + formatter.hour(hours.closeHour()))
                    .append(day == DAYS_IN_WEEK ? "\n" : ", ");
        }
    }

    /// Отправляет текст и клавиатуру через бота.
    ///
    /// @param bot      транспорт для отправки
    /// @param chatId   идентификатор чата
    /// @param text     текст сообщения
    /// @param keyboard клавиатура сообщения
    private void send(Bot bot, long chatId, String text, List<List<Button>> keyboard) {
        bot.sendMessage(new Message(text, null, keyboard), chatId);
    }
}

package ru.urfu.echobot.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.SubscriptionRepository;

/// Рассылает подписчикам базы уведомления об освободившемся «горячем» слоте.
public final class SubscriptionHotSlotNotifier implements HotSlotNotifier {
    /// Формат времени начала в callback-кнопках.
    private static final String CALLBACK_PATTERN = "uuuuMMddHHmm";
    /// Callback начала бронирования «горячего» слота.
    private static final String BOOK_CALLBACK = "hot:book:";
    /// Callback закрытия уведомления.
    private static final String SKIP_CALLBACK = "hot:skip";
    /// Подпись кнопки бронирования.
    private static final String BOOK_BUTTON = "Забронировать";
    /// Подпись кнопки пропуска.
    private static final String SKIP_BUTTON = "Пропустить";

    /// Журнал рассылки уведомлений.
    private final Logger logger = LoggerFactory.getLogger(SubscriptionHotSlotNotifier.class);
    /// Хранилище подписок.
    private final SubscriptionRepository subscriptionRepository;
    /// Правила бронирования для определения дат базы.
    private final BookingRules rules;
    /// Форматер дат и времени.
    private final RussianDateFormatter formatter = new RussianDateFormatter();
    /// Формат времени начала в callback-кнопках.
    private final DateTimeFormatter callbackFormat = DateTimeFormatter.ofPattern(CALLBACK_PATTERN);

    /// Создаёт рассылку уведомлений.
    ///
    /// @param subscriptionRepository хранилище подписок
    /// @param rules                  правила бронирования
    public SubscriptionHotSlotNotifier(SubscriptionRepository subscriptionRepository, BookingRules rules) {
        this.subscriptionRepository = subscriptionRepository;
        this.rules = rules;
    }

    /// Сообщает подписчикам базы об освободившемся времени.
    ///
    /// @param base    база, в которой освободилось время
    /// @param room    зал
    /// @param booking освободившаяся бронь
    /// @param bot     бот для отправки сообщений
    @Override
    public void notifySubscribers(Base base, Room room, Booking booking, Bot bot) {
        String text = "«Горячий» слот: база «" + base.name() + "», " + room.name() + ", "
                + formatter.relativeDate(booking.start().toLocalDate(), rules.today(base)) + ", "
                + formatter.timeRange(booking.start(), booking.end()) + ".";
        List<List<Button>> keyboard = List.of(
                List.of(new Button(BOOK_BUTTON, BOOK_CALLBACK + room.id() + ":"
                        + booking.start().format(callbackFormat))),
                List.of(new Button(SKIP_BUTTON, SKIP_CALLBACK)));
        for (long userId : subscriptionRepository.findUserIdsByBase(base.id())) {
            try {
                bot.sendMessage(new Message(text, null, keyboard), userId);
            } catch (RuntimeException exception) {
                logger.warn("Не удалось отправить уведомление о горячем слоте пользователю {}", userId,
                        exception);
            }
        }
    }
}

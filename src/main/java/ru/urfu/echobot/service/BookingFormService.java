package ru.urfu.echobot.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Booking;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.BookingRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Обрабатывает ввод контактных данных и создаёт бронирование.
public final class BookingFormService {
    /// Текст запроса имени клиента.
    private static final String ENTER_NAME = "Введите имя для связи.";
    /// Текст запроса номера телефона.
    private static final String ENTER_PHONE = "Введите номер телефона для связи.";

    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Хранилище залов.
    private final RoomRepository roomRepository;
    /// Хранилище бронирований.
    private final BookingRepository bookingRepository;
    /// Правила проверки бронирования.
    private final BookingRules rules;
    /// Автомат состояний диалога.
    private final BookingStateMachine stateMachine;
    /// Сервис комментария и подтверждения.
    private final BookingConfirmationService confirmation;
    /// Нормализатор российских номеров телефонов.
    private final PhoneNormalizer phoneNormalizer = new PhoneNormalizer();
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards = new BookingKeyboardFactory();

    /// Создаёт обработчик формы бронирования.
    ///
    /// @param baseRepository    хранилище баз
    /// @param roomRepository    хранилище залов
    /// @param bookingRepository хранилище броней
    /// @param rules             правила бронирования
    /// @param stateMachine      автомат состояний диалога
    /// @param confirmation      сервис комментария и подтверждения
    public BookingFormService(BaseRepository baseRepository, RoomRepository roomRepository,
                              BookingRepository bookingRepository, BookingRules rules,
                              BookingStateMachine stateMachine, BookingConfirmationService confirmation) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.rules = rules;
        this.stateMachine = stateMachine;
        this.confirmation = confirmation;
    }

    /// Просит клиента ввести имя.
    ///
    /// @param chatId идентификатор чата
    /// @param bot    бот для отправки сообщения
    public void promptName(long chatId, Bot bot) {
        send(bot, chatId, ENTER_NAME, keyboards.backKeyboard());
    }

    /// Проверяет имя и переводит форму к вводу телефона.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param text   введённый текст
    /// @param bot    бот для отправки сообщения
    public void handleNameText(long chatId, BookingFlow flow, String text, Bot bot) {
        if (text.isEmpty()) {
            promptName(chatId, bot);
        } else {
            flow.name = text;
            if (stateMachine.transition(flow, BookingStateMachine.Event.ENTER_NAME)) {
                promptPhone(chatId, bot);
            }
        }
    }

    /// Просит клиента ввести номер телефона.
    ///
    /// @param chatId идентификатор чата
    /// @param bot    бот для отправки сообщения
    public void promptPhone(long chatId, Bot bot) {
        send(bot, chatId, ENTER_PHONE, keyboards.backKeyboard());
    }

    /// Проверяет телефон и переводит форму к вводу комментария.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param text   введённый номер
    /// @param bot    бот для отправки сообщения
    public void handlePhoneText(long chatId, BookingFlow flow, String text, Bot bot) {
        Optional<String> normalizedPhone = phoneNormalizer.normalize(text);
        if (normalizedPhone.isEmpty()) {
            send(bot, chatId, "Не удалось распознать номер. Введите российский номер, например\n"
                    + "+7 999 123-45-67 или 8 999 123-45-67.", keyboards.backKeyboard());
        } else {
            flow.phone = normalizedPhone.get();
            if (stateMachine.transition(flow, BookingStateMachine.Event.VALID_PHONE)) {
                confirmation.promptComment(chatId, flow, bot);
            }
        }
    }

    /// Создаёт бронь, если время всё ещё свободно и соответствует правилам базы.
    ///
    /// @param userId идентификатор пользователя
    /// @param flow   заполненное состояние диалога
    /// @return true, если бронь сохранена
    public boolean confirmBooking(long userId, BookingFlow flow) {
        LocalDateTime start = flow.bookingDate.atTime(flow.startHour, 0);
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        Room room = roomRepository.findById(flow.roomId).orElse(null);
        return base != null && room != null && room.active() && room.baseId() == base.id()
                && rules.canBook(base, start, flow.hours)
                && bookingRepository.addIfFree(new Booking(flow.roomId, userId, start, flow.hours,
                flow.name, flow.phone, flow.comment == null ? "" : flow.comment));
    }

    /// Удаляет все введённые клиентом сведения.
    ///
    /// @param flow состояние диалога
    public void clearCustomerDetails(BookingFlow flow) {
        flow.name = null;
        flow.phone = null;
        flow.comment = null;
    }

    /// Очищает только номер телефона при возврате к вводу имени.
    ///
    /// @param flow состояние диалога
    public void clearPhone(BookingFlow flow) {
        flow.phone = null;
    }

    /// Отправляет текстовое сообщение с клавиатурой.
    ///
    /// @param bot      бот для отправки
    /// @param chatId   идентификатор чата
    /// @param text     текст сообщения
    /// @param keyboard строки кнопок
    private void send(Bot bot, long chatId, String text, List<List<Button>> keyboard) {
        bot.sendMessage(new Message(text, null, keyboard), chatId);
    }
}

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

/// Обрабатывает ввод данных клиента, подтверждение формы и создание бронирования.
public final class BookingFormService {
    /// Текст запроса имени клиента.
    private static final String ENTER_NAME = "Введите имя для связи.";
    /// Текст запроса номера телефона.
    private static final String ENTER_PHONE = "Введите номер телефона для связи.";
    /// Подпись кнопки возврата.
    private static final String BACK = "Назад";

    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Хранилище залов.
    private final RoomRepository roomRepository;
    /// Хранилище бронирований.
    private final BookingRepository bookingRepository;
    /// Правила проверки бронирования.
    private final BookingRules rules;
    /// Нормализатор российских номеров телефонов.
    private final PhoneNormalizer phoneNormalizer;
    /// Форматер дат и времени.
    private final RussianDateFormatter formatter;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards;
    /// Сервис выбора базы, зала, даты и времени.
    private final BookingSelectionService selectionService;

    /// Создаёт обработчик данных формы бронирования.
    ///
    /// @param baseRepository хранилище баз
    /// @param roomRepository хранилище залов
    /// @param bookingRepository хранилище броней
    /// @param rules правила бронирования
    /// @param phoneNormalizer нормализатор телефонных номеров
    /// @param formatter форматер дат и времени
    /// @param keyboards фабрика клавиатур
    /// @param selectionService сервис выбора слота
    public BookingFormService(BaseRepository baseRepository, RoomRepository roomRepository,
                              BookingRepository bookingRepository, BookingRules rules,
                              PhoneNormalizer phoneNormalizer, RussianDateFormatter formatter,
                              BookingKeyboardFactory keyboards, BookingSelectionService selectionService) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.rules = rules;
        this.phoneNormalizer = phoneNormalizer;
        this.formatter = formatter;
        this.keyboards = keyboards;
        this.selectionService = selectionService;
    }

    /// Открывает шаг ввода контактных данных после выбора длительности брони.
    ///
    /// @param chatId идентификатор Telegram-чата
    /// @param flow состояние диалога
    /// @param bot бот для отправки сообщения
    public void beginCustomerDetails(long chatId, BookingFlow flow, Bot bot) {
        flow.step = BookingFlow.Step.BOOK_NAME;
        send(bot, chatId, ENTER_NAME, keyboards.backKeyboard());
    }

    /// Проверяет имя клиента и переводит форму к вводу номера телефона.
    ///
    /// @param chatId идентификатор Telegram-чата
    /// @param flow состояние диалога
    /// @param text введённый текст
    /// @param bot бот для отправки сообщения
    public void handleNameText(long chatId, BookingFlow flow, String text, Bot bot) {
        if (text.isEmpty()) {
            send(bot, chatId, ENTER_NAME, keyboards.backKeyboard());
            return;
        }
        flow.name = text;
        flow.step = BookingFlow.Step.BOOK_PHONE;
        send(bot, chatId, ENTER_PHONE, keyboards.backKeyboard());
    }

    /// Проверяет и сохраняет номер телефона клиента.
    ///
    /// @param chatId идентификатор Telegram-чата
    /// @param flow состояние диалога
    /// @param text введённый номер
    /// @param bot бот для отправки сообщения
    public void handlePhoneText(long chatId, BookingFlow flow, String text, Bot bot) {
        Optional<String> normalizedPhone = phoneNormalizer.normalize(text);
        if (normalizedPhone.isEmpty()) {
            send(bot, chatId, "Не удалось распознать номер. Введите российский номер, например\n"
                    + "+7 999 123-45-67 или 8 999 123-45-67.", keyboards.backKeyboard());
            return;
        }
        flow.phone = normalizedPhone.get();
        askBookingComment(chatId, flow, bot);
    }

    /// Проверяет комментарий и открывает страницу подтверждения бронирования.
    ///
    /// @param chatId идентификатор Telegram-чата
    /// @param flow состояние диалога
    /// @param text комментарий клиента
    /// @param bot бот для отправки сообщения
    public void handleCommentText(long chatId, BookingFlow flow, String text, Bot bot) {
        Room selectedRoom = roomRepository.findById(flow.roomId).orElse(null);
        if (text.isEmpty() || (selectedRoom != null && selectedRoom.commentRequired() && "-".equals(text))) {
            send(bot, chatId, "Комментарий не может быть пустым. Опишите, что будете делать.",
                    keyboards.commentKeyboard());
            return;
        }
        flow.comment = text;
        showBookingConfirmation(chatId, flow, bot);
    }

    /// Разрешает пропустить комментарий только для зала, где он необязателен.
    ///
    /// @param chatId идентификатор Telegram-чата
    /// @param flow состояние диалога
    /// @param bot бот для отправки сообщения
    public void skipBookingComment(long chatId, BookingFlow flow, Bot bot) {
        Room room = roomRepository.findById(flow.roomId).orElse(null);
        if (flow.step == BookingFlow.Step.BOOK_COMMENT && room != null && !room.commentRequired()) {
            flow.comment = "";
            showBookingConfirmation(chatId, flow, bot);
        }
    }

    /// Создаёт бронь, если время всё ещё свободно и соответствует правилам базы.
    ///
    /// @param userId идентификатор пользователя, которому принадлежит бронь
    /// @param flow заполненное состояние диалога
    /// @return true, если бронь сохранена; false, если слот недоступен
    public boolean confirmBooking(long userId, BookingFlow flow) {
        LocalDateTime start = flow.bookingDate.atTime(flow.startHour, 0);
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        return base != null && rules.canBook(base, start, flow.hours)
                && bookingRepository.addIfFree(new Booking(flow.roomId, userId, start, flow.hours,
                flow.name, flow.phone, flow.comment == null ? "" : flow.comment));
    }

    /// Возвращает пользователя к выбору длительности и очищает введённые данные клиента.
    ///
    /// @param chatId идентификатор Telegram-чата
    /// @param flow состояние диалога
    /// @param bot бот для отправки сообщения
    public void backFromName(long chatId, BookingFlow flow, Bot bot) {
        clearCustomerDetails(flow);
        selectionService.showDurationChoices(chatId, flow, bot);
    }

    /// Возвращает пользователя к вводу имени и очищает все введённые данные клиента.
    ///
    /// @param chatId идентификатор Telegram-чата
    /// @param flow состояние диалога
    /// @param bot бот для отправки сообщения
    public void backToName(long chatId, BookingFlow flow, Bot bot) {
        clearCustomerDetails(flow);
        flow.step = BookingFlow.Step.BOOK_NAME;
        send(bot, chatId, ENTER_NAME, keyboards.backKeyboard());
    }

    /// Запрашивает комментарий с учётом обязательности для выбранного зала.
    ///
    /// @param chatId идентификатор Telegram-чата
    /// @param flow состояние диалога
    /// @param bot бот для отправки сообщения
    private void askBookingComment(long chatId, BookingFlow flow, Bot bot) {
        Room room = roomRepository.findById(flow.roomId).orElse(null);
        if (room == null) {
            selectionService.beginBooking(chatId, flow, bot);
            return;
        }
        flow.step = BookingFlow.Step.BOOK_COMMENT;
        String prompt = room.commentRequired()
                ? "Опишите, что будете делать в зале. Например, запись барабанов и баса."
                : "Оставьте комментарий к записи (необязательно). Например, что нужно подключить.";
        send(bot, chatId, prompt, room.commentRequired() ? keyboards.backKeyboard() : keyboards.commentKeyboard());
    }

    /// Показывает детали бронирования перед подтверждением.
    ///
    /// @param chatId идентификатор Telegram-чата
    /// @param flow состояние диалога
    /// @param bot бот для отправки сообщения
    private void showBookingConfirmation(long chatId, BookingFlow flow, Bot bot) {
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        Room room = roomRepository.findById(flow.roomId).orElse(null);
        if (base == null || room == null
                || (room.commentRequired() && (flow.comment == null || flow.comment.isEmpty()))) {
            send(bot, chatId, "В этом зале комментарий обязателен. Опишите, что будете делать.",
                    keyboards.backKeyboard());
            flow.step = BookingFlow.Step.BOOK_COMMENT;
            return;
        }
        LocalDateTime start = flow.bookingDate.atTime(flow.startHour, 0);
        LocalDateTime end = start.plusHours(flow.hours);
        flow.step = BookingFlow.Step.BOOK_CONFIRM;
        String text = "Проверьте:\nБаза «" + base.name() + "», " + room.name() + ", "
                + formatter.dateTime(start) + "-" + formatter.endTime(start, end) + ".\n"
                + "Имя: " + flow.name + "\nТелефон: " + flow.phone + "\nКомментарий: "
                + (flow.comment == null || flow.comment.isEmpty() ? "—" : flow.comment);
        send(bot, chatId, text, keyboards.choices(keyboards.button("Подтвердить", "book:confirm"),
                keyboards.button(BACK, "back"), keyboards.button("Отмена", "book:cancel")));
    }

    /// Удаляет все введённые клиентом сведения, оставляя выбранный слот без изменений.
    ///
    /// @param flow состояние диалога
    private void clearCustomerDetails(BookingFlow flow) {
        flow.name = null;
        flow.phone = null;
        flow.comment = null;
    }

    /// Отправляет текстовое сообщение с клавиатурой.
    ///
    /// @param bot транспорт для отправки сообщения
    /// @param chatId идентификатор Telegram-чата
    /// @param text текст сообщения
    /// @param keyboard строки кнопок
    private void send(Bot bot, long chatId, String text, List<List<Button>> keyboard) {
        bot.sendMessage(new Message(text, null, keyboard), chatId);
    }
}

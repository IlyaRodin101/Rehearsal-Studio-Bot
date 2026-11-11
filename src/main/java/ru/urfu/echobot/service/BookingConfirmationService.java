package ru.urfu.echobot.service;

import java.time.LocalDateTime;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Запрашивает комментарий и показывает страницу подтверждения бронирования.
public final class BookingConfirmationService {
    /// Подпись кнопки возврата.
    private static final String BACK = "Назад";
    /// Текст ошибки для обязательного комментария.
    private static final String COMMENT_REQUIRED = "В этом зале комментарий обязателен. Опишите, что будете делать.";

    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Хранилище залов.
    private final RoomRepository roomRepository;
    /// Автомат состояний диалога.
    private final BookingStateMachine stateMachine;
    /// Форматер дат и времени.
    private final RussianDateFormatter formatter = new RussianDateFormatter();
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards = new BookingKeyboardFactory();

    /// Создаёт сервис комментария и подтверждения.
    ///
    /// @param baseRepository хранилище баз
    /// @param roomRepository хранилище залов
    /// @param stateMachine   автомат состояний диалога
    public BookingConfirmationService(BaseRepository baseRepository, RoomRepository roomRepository,
                                      BookingStateMachine stateMachine) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.stateMachine = stateMachine;
    }

    /// Просит комментарий с учётом обязательности для выбранного зала.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщения
    public void promptComment(long chatId, BookingFlow flow, Bot bot) {
        Room room = roomRepository.findById(flow.roomId).orElse(null);
        if (room == null) {
            send(bot, chatId, "Зал больше недоступен. Начните бронирование заново.",
                    keyboards.choices(keyboards.button(BACK, "menu:home")));
        } else {
            String prompt = room.commentRequired()
                    ? "Опишите, что будете делать в зале. Например, запись барабанов и баса."
                    : "Оставьте комментарий к записи (необязательно). Например, что нужно подключить.";
            send(bot, chatId, prompt,
                    room.commentRequired() ? keyboards.backKeyboard() : keyboards.commentKeyboard());
        }
    }

    /// Проверяет комментарий и открывает страницу подтверждения.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param text   комментарий клиента
    /// @param bot    бот для отправки сообщения
    public void handleCommentText(long chatId, BookingFlow flow, String text, Bot bot) {
        Room room = roomRepository.findById(flow.roomId).orElse(null);
        if (text.isEmpty() || (room != null && room.commentRequired() && "-".equals(text))) {
            send(bot, chatId, "Комментарий не может быть пустым. Опишите, что будете делать.",
                    keyboards.commentKeyboard());
        } else {
            flow.comment = text;
            if (stateMachine.transition(flow, BookingStateMachine.Event.ENTER_COMMENT)) {
                showConfirmation(chatId, flow, bot);
            }
        }
    }

    /// Разрешает пропустить комментарий только для зала, где он необязателен.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщения
    public void skipBookingComment(long chatId, BookingFlow flow, Bot bot) {
        Room room = roomRepository.findById(flow.roomId).orElse(null);
        if (flow.step == BookingFlow.Step.BOOK_COMMENT && room != null && !room.commentRequired()
                && stateMachine.transition(flow, BookingStateMachine.Event.SKIP_COMMENT)) {
            flow.comment = "";
            showConfirmation(chatId, flow, bot);
        }
    }

    /// Показывает детали бронирования перед подтверждением.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщения
    public void showConfirmation(long chatId, BookingFlow flow, Bot bot) {
        Base base = baseRepository.findById(flow.baseId).orElse(null);
        Room room = roomRepository.findById(flow.roomId).orElse(null);
        if (base == null || room == null
                || (room.commentRequired() && (flow.comment == null || flow.comment.isEmpty()))) {
            send(bot, chatId, COMMENT_REQUIRED, keyboards.backKeyboard());
            stateMachine.transition(flow, BookingStateMachine.Event.BACK);
        } else {
            LocalDateTime start = flow.bookingDate.atTime(flow.startHour, 0);
            LocalDateTime end = start.plusHours(flow.hours);
            String text = "Проверьте:\nБаза «" + base.name() + "», " + room.name() + ", "
                    + formatter.dateTime(start) + "-" + formatter.endTime(start, end) + ".\n"
                    + "Имя: " + flow.name + "\nТелефон: " + flow.phone + "\nКомментарий: "
                    + (flow.comment == null || flow.comment.isEmpty() ? "—" : flow.comment);
            send(bot, chatId, text, keyboards.choices(keyboards.button("Подтвердить", "book:confirm"),
                    keyboards.button(BACK, "back"), keyboards.button("Отмена", "book:cancel")));
        }
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

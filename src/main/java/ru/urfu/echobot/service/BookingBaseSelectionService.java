package ru.urfu.echobot.service;

import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Показывает кнопки выбора базы и зала на первых шагах бронирования.
public final class BookingBaseSelectionService {
    /// Подпись кнопки возврата.
    private static final String BACK = "Назад";
    /// Callback главного меню для начала бронирования.
    private static final String MENU_BOOK = "menu:book";
    /// Callback главного меню.
    private static final String MENU_HOME = "menu:home";
    /// Callback-префикс выбора базы.
    private static final String BOOK_BASE = "book:base:";
    /// Callback-префикс выбора зала.
    private static final String BOOK_ROOM = "book:room:";
    /// Текст приглашения выбрать базу.
    private static final String SELECT_BASE = "Выберите базу:";
    /// Текст приглашения выбрать зал.
    private static final String SELECT_ROOM = "Выберите зал:";

    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Хранилище залов.
    private final RoomRepository roomRepository;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards;

    /// Создаёт сервис выбора базы и зала.
    ///
    /// @param baseRepository хранилище баз
    /// @param roomRepository хранилище залов
    /// @param keyboards      фабрика клавиатур
    public BookingBaseSelectionService(BaseRepository baseRepository, RoomRepository roomRepository,
                                       BookingKeyboardFactory keyboards) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.keyboards = keyboards;
    }

    /// Показывает доступные для бронирования базы.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщения
    public void showBaseChoices(long chatId, BookingFlow flow, Bot bot) {
        List<Base> bases = baseRepository.findAll();
        if (bases.isEmpty()) {
            send(bot, chatId, "Пока нет доступных баз для бронирования.",
                    keyboards.choices(keyboards.button(BACK, MENU_HOME)));
            return;
        }
        List<List<Button>> rows = new ArrayList<>();
        for (Base base : bases) {
            keyboards.addButton(rows, keyboards.button(base.name(), BOOK_BASE + base.id()), 2);
        }
        rows.add(keyboards.row(keyboards.button(BACK, MENU_HOME)));
        send(bot, chatId, SELECT_BASE, keyboards.markup(rows));
    }

    /// Показывает активные залы выбранной базы.
    ///
    /// @param chatId идентификатор чата
    /// @param flow   состояние диалога
    /// @param bot    бот для отправки сообщения
    public void showRoomChoices(long chatId, BookingFlow flow, Bot bot) {
        List<Room> rooms = roomRepository.findByBaseId(flow.baseId);
        List<List<Button>> rows = new ArrayList<>();
        for (Room room : rooms) {
            keyboards.addButton(rows, keyboards.button(room.name(), BOOK_ROOM + room.id()), 2);
        }
        rows.add(keyboards.row(keyboards.button(BACK, MENU_BOOK)));
        String text = rooms.isEmpty() ? "В этой базе пока нет доступных залов." : SELECT_ROOM;
        send(bot, chatId, text, keyboards.markup(rows));
    }

    /// Проверяет, что зал принадлежит выбранной базе и доступен для новых броней.
    ///
    /// @param baseId идентификатор базы
    /// @param roomId идентификатор зала
    /// @return true, если зал можно бронировать
    public boolean isAvailableRoom(long baseId, long roomId) {
        return roomRepository.findById(roomId)
                .filter(room -> room.baseId() == baseId && room.active())
                .isPresent();
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

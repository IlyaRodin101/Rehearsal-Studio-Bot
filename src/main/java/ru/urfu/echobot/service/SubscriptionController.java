package ru.urfu.echobot.service;

import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.repository.BaseRepository;

/// Показывает список баз и переключает подписку клиента на «горячие» слоты.
public final class SubscriptionController {
    /// Позиция аргумента действия в callback-данных.
    private static final int ARGUMENT_PART = 2;
    /// Действие показа списка подписок.
    private static final String LIST = "list";
    /// Действие переключения подписки.
    private static final String TOGGLE = "toggle";
    /// Callback возврата в главное меню.
    private static final String BACK_CALLBACK = "sub:back";
    /// Подпись кнопки возврата.
    private static final String BACK = "Назад";
    /// Заголовок списка подписок.
    private static final String SELECT_BASES = "Выберите базы, о «горячих» слотах которых вам писать:";
    /// Отметка включённой подписки.
    private static final String CHECKED = "✓ ";
    /// Количество кнопок баз в строке.
    private static final int BASES_PER_ROW = 2;

    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Сервис подписок.
    private final SubscriptionService subscriptionService;
    /// Контроллер главного меню.
    private final BookingMenuController menuController;
    /// Фабрика клавиатур.
    private final BookingKeyboardFactory keyboards;

    /// Создаёт контроллер подписок.
    ///
    /// @param baseRepository      хранилище баз
    /// @param subscriptionService сервис подписок
    /// @param menuController      главное меню
    /// @param keyboards           фабрика клавиатур
    public SubscriptionController(BaseRepository baseRepository, SubscriptionService subscriptionService,
                                  BookingMenuController menuController, BookingKeyboardFactory keyboards) {
        this.baseRepository = baseRepository;
        this.subscriptionService = subscriptionService;
        this.menuController = menuController;
        this.keyboards = keyboards;
    }

    /// Показывает базы с отметками текущих подписок.
    ///
    /// @param chatId идентификатор чата
    /// @param userId идентификатор клиента
    /// @param bot    бот для отправки сообщений
    public void showList(long chatId, long userId, Bot bot) {
        List<Base> bases = baseRepository.findAll();
        if (bases.isEmpty()) {
            send(bot, chatId, "Пока нет доступных баз.", keyboards.choices(keyboards.button(BACK, BACK_CALLBACK)));
            return;
        }
        List<List<Button>> rows = new ArrayList<>();
        for (Base base : bases) {
            String mark = subscriptionService.isSubscribed(userId, base.id()) ? CHECKED : "";
            keyboards.addButton(rows, keyboards.button(mark + base.name(), "sub:toggle:" + base.id()),
                    BASES_PER_ROW);
        }
        rows.add(keyboards.row(keyboards.button(BACK, BACK_CALLBACK)));
        send(bot, chatId, SELECT_BASES, keyboards.markup(rows));
    }

    /// Обрабатывает callback-команду раздела «Подписка».
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    public void handle(BookingCallback context, Bot bot) {
        switch (context.action()) {
            case LIST:
                showList(context.chatId(), context.userId(), bot);
                break;
            case TOGGLE:
                toggle(context, bot);
                break;
            default:
                menuController.returnToMenu(context.chatId(), context.flow(), bot);
                break;
        }
    }

    /// Включает или отключает подписку и показывает обновлённый список.
    ///
    /// @param context callback-команда
    /// @param bot     бот для отправки сообщений
    private void toggle(BookingCallback context, Bot bot) {
        try {
            long baseId = Long.parseLong(context.part(ARGUMENT_PART));
            Base base = baseRepository.findById(baseId).orElse(null);
            if (base == null) {
                showList(context.chatId(), context.userId(), bot);
                return;
            }
            boolean enabled = subscriptionService.toggle(context.userId(), baseId);
            String action = enabled ? "включена" : "отключена";
            send(bot, context.chatId(), "Подписка на «" + base.name() + "» " + action + ".", List.of());
            showList(context.chatId(), context.userId(), bot);
        } catch (NumberFormatException exception) {
            showList(context.chatId(), context.userId(), bot);
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

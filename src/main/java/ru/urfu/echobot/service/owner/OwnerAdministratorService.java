package ru.urfu.echobot.service.owner;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.TelegramUser;
import ru.urfu.echobot.service.Button;
import ru.urfu.echobot.service.GenericStateMachine;
import ru.urfu.echobot.service.UserDirectory;

/// Показывает список администраторов и добавляет или удаляет их по имени Telegram.
final class OwnerAdministratorService {
    /// Текст кнопки добавления.
    private static final String ADD_TEXT = "Добавить";
    /// Текст кнопки удаления.
    private static final String REMOVE_TEXT = "Удалить";
    /// Callback возврата в главное меню.
    private static final String MENU_HOME = "menu:home";
    /// Callback списка администраторов.
    private static final String ADMINS_CALLBACK = "owner:admins";
    /// Callback удаления администратора.
    private static final String REMOVE_CALLBACK = "owner:adminremove:";
    /// Приглашение ввести имя нового администратора.
    private static final String ADD_PROMPT = "Введите @имя пользователя в Telegram. "
            + "Человек должен хотя бы раз написать боту.";
    /// Начало уведомления об администраторе.
    private static final String ADMIN_PREFIX = "Администратор ";
    /// Сообщение об отсутствии администраторов.
    private static final String NO_ADMINISTRATORS = "Администраторов пока нет.";
    /// Число кнопок администраторов в строке.
    private static final int ADMINS_PER_ROW = 2;

    /// Справочник известных пользователей и администраторов.
    private final UserDirectory directory;
    /// Сессии владельцев.
    private final OwnerSessions sessions;
    /// Проверка прав владельца.
    private final OwnerAccess access;
    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Автомат состояний владельца.
    private final GenericStateMachine<OwnerState, OwnerEvent> stateMachine;

    /// Создаёт сервис управления администраторами.
    ///
    /// @param directory    справочник известных пользователей и администраторов
    /// @param sessions     сессии владельцев
    /// @param access       проверка прав владельца
    /// @param messaging    сообщения и клавиатуры
    /// @param stateMachine автомат состояний владельца
    OwnerAdministratorService(UserDirectory directory, OwnerSessions sessions, OwnerAccess access,
                              OwnerMessaging messaging,
                              GenericStateMachine<OwnerState, OwnerEvent> stateMachine) {
        this.directory = directory;
        this.sessions = sessions;
        this.access = access;
        this.messaging = messaging;
        this.stateMachine = stateMachine;
    }

    /// Показывает список администраторов.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void show(long userId, Bot bot) {
        if (access.requireOwner(userId, bot)) {
            sendList(userId, bot, null);
        }
    }

    /// Начинает добавление администратора.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void beginAdd(long userId, Bot bot) {
        if (access.requireOwner(userId, bot)) {
            OwnerSession session = sessions.get(userId);
            stateMachine.reset(session, OwnerState.IDLE);
            if (stateMachine.transition(session, OwnerEvent.ADD_ADMIN)) {
                messaging.send(bot, userId, ADD_PROMPT, messaging.choices(messaging.button(
                        OwnerMessaging.BACK_TEXT, ADMINS_CALLBACK)));
            }
        }
    }

    /// Добавляет администратора по введённому имени Telegram.
    ///
    /// @param userId идентификатор владельца
    /// @param text   введённое имя пользователя
    /// @param bot    бот для отправки
    void handleAddText(long userId, String text, Bot bot) {
        if (!access.requireOwner(userId, bot)) {
            sessions.get(userId).reset();
        } else if (text.isEmpty()) {
            messaging.send(bot, userId, ADD_PROMPT, messaging.choices(messaging.button(
                    OwnerMessaging.BACK_TEXT, ADMINS_CALLBACK)));
        } else {
            addByUsername(userId, text, bot);
        }
    }

    /// Показывает кнопки удаления администраторов.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void beginRemove(long userId, Bot bot) {
        if (access.requireOwner(userId, bot)) {
            List<TelegramUser> administrators = directory.administrators();
            if (administrators.isEmpty()) {
                sendList(userId, bot, NO_ADMINISTRATORS);
            } else {
                sendRemoveChoices(userId, bot, administrators);
            }
        }
    }

    /// Удаляет выбранного администратора.
    ///
    /// @param userId  идентификатор владельца
    /// @param adminId Telegram ID администратора
    /// @param bot     бот для отправки
    void remove(long userId, long adminId, Bot bot) {
        if (access.requireOwner(userId, bot)) {
            Optional<TelegramUser> administrator = directory.administrators().stream()
                    .filter(user -> user.id() == adminId)
                    .findFirst();
            if (administrator.isEmpty()) {
                sendList(userId, bot, "Администратор не найден.");
            } else {
                directory.removeAdministrator(adminId);
                sendList(userId, bot, ADMIN_PREFIX + administrator.get().mention() + " удалён.");
            }
        }
    }

    /// Добавляет найденного пользователя в администраторы.
    ///
    /// @param userId идентификатор владельца
    /// @param text   введённое имя пользователя
    /// @param bot    бот для отправки
    private void addByUsername(long userId, String text, Bot bot) {
        Optional<TelegramUser> user = directory.findByUsername(text);
        if (user.isEmpty()) {
            messaging.send(bot, userId, notFoundText(text), messaging.choices(messaging.button(
                    OwnerMessaging.BACK_TEXT, ADMINS_CALLBACK)));
        } else {
            directory.addAdministrator(user.get());
            stateMachine.reset(sessions.get(userId), OwnerState.IDLE);
            sendList(userId, bot, ADMIN_PREFIX + user.get().mention() + " добавлен.");
        }
    }

    /// Показывает кнопки со всеми администраторами.
    ///
    /// @param userId         идентификатор владельца
    /// @param bot            бот для отправки
    /// @param administrators администраторы
    private void sendRemoveChoices(long userId, Bot bot, List<TelegramUser> administrators) {
        List<List<Button>> rows = new ArrayList<>();
        for (TelegramUser administrator : administrators) {
            messaging.addButton(rows, messaging.button(administrator.mention(),
                    REMOVE_CALLBACK + administrator.id()), ADMINS_PER_ROW);
        }
        rows.add(messaging.row(messaging.button(OwnerMessaging.BACK_TEXT, ADMINS_CALLBACK)));
        messaging.send(bot, userId, "Выберите администратора для удаления:", rows);
    }

    /// Отправляет список администраторов с необязательным уведомлением.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    /// @param notice уведомление перед списком или null
    private void sendList(long userId, Bot bot, String notice) {
        stateMachine.reset(sessions.get(userId), OwnerState.IDLE);
        String list = administratorsText(directory.administrators());
        String text = notice == null ? list : notice + "\n\n" + list;
        messaging.send(bot, userId, text, List.of(messaging.row(
                messaging.button(ADD_TEXT, "owner:adminadd"),
                messaging.button(REMOVE_TEXT, "owner:adminremove"),
                messaging.button(OwnerMessaging.BACK_TEXT, MENU_HOME))));
    }

    /// Собирает текст списка администраторов.
    ///
    /// @param administrators администраторы
    /// @return текст со списком или сообщение об их отсутствии
    private String administratorsText(List<TelegramUser> administrators) {
        String text = NO_ADMINISTRATORS;
        if (!administrators.isEmpty()) {
            String names = administrators.stream()
                    .map(TelegramUser::mention)
                    .reduce((first, second) -> first + ", " + second)
                    .orElse("");
            text = "Администраторы: " + names + ".";
        }
        return text;
    }

    /// Собирает сообщение о ненайденном пользователе.
    ///
    /// @param text введённое имя пользователя
    /// @return текст ошибки
    private String notFoundText(String text) {
        String mention = text.startsWith("@") ? text : "@" + text;
        return "Не удалось найти " + mention + " среди тех, кто писал боту.\n"
                + "Попросите его написать боту /start и попробуйте снова.";
    }
}

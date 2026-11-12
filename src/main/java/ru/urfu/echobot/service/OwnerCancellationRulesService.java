package ru.urfu.echobot.service;

import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;

/// Настраивает срок бесплатной отмены и размер неустойки выбранной базы.
final class OwnerCancellationRulesService {
    /// Callback открытия правил отмены.
    private static final String RULES_CALLBACK = "owner:cancelrules";
    /// Callback выбора срока бесплатной отмены.
    private static final String HOURS_CALLBACK = "owner:cancelhours";
    /// Начало callback установки срока бесплатной отмены.
    private static final String HOURS_SET_CALLBACK = "owner:cancelhoursset:";
    /// Callback выбора размера неустойки.
    private static final String PENALTY_CALLBACK = "owner:cancelpenalty";
    /// Начало callback установки размера неустойки.
    private static final String PENALTY_SET_CALLBACK = "owner:cancelpenaltyset:";
    /// Callback возврата в меню базы.
    private static final String BACK_CALLBACK = "owner:setting";
    /// Максимальный срок бесплатной отмены в часах.
    private static final int MAX_FREE_CANCELLATION_HOURS = 72;
    /// Максимальный размер неустойки в процентах.
    private static final int MAX_PENALTY_PERCENT = 100;
    /// Число кнопок в строке выбора значений.
    private static final int BUTTONS_PER_ROW = 4;

    /// Сессии владельцев.
    private final OwnerSessions sessions;
    /// Проверка прав и принадлежности базы.
    private final OwnerAccess access;
    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Основные операции с базами.
    private final OwnerBaseService baseService;

    /// Создаёт сервис правил отмены.
    ///
    /// @param sessions    сессии владельцев
    /// @param access      проверка прав и принадлежности
    /// @param messaging   сообщения и клавиатуры
    /// @param baseService основные операции с базами
    OwnerCancellationRulesService(OwnerSessions sessions, OwnerAccess access, OwnerMessaging messaging,
                                  OwnerBaseService baseService) {
        this.sessions = sessions;
        this.access = access;
        this.messaging = messaging;
        this.baseService = baseService;
    }

    /// Показывает текущие правила отмены выбранной базы.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void showRules(long userId, Bot bot) {
        Base base = selectedBase(userId, bot);
        if (base != null) {
            String text = "Сейчас: бесплатная отмена не позднее чем за " + base.freeCancellationHours()
                    + " часов, неустойка " + base.penaltyPercent() + "%.\nЧто изменить?";
            messaging.send(bot, userId, text, messaging.choices(
                    messaging.button("Изменить срок", HOURS_CALLBACK),
                    messaging.button("Изменить неустойку", PENALTY_CALLBACK),
                    messaging.button(OwnerMessaging.BACK_TEXT, BACK_CALLBACK)));
        }
    }

    /// Показывает варианты срока бесплатной отмены.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void showHours(long userId, Bot bot) {
        if (selectedBase(userId, bot) != null) {
            List<List<Button>> rows = new ArrayList<>();
            int[] hours = {0, 1, 2, 6, 12, 24, 48, 72};
            for (int value : hours) {
                messaging.addButton(rows,
                        messaging.button(value + " ч", HOURS_SET_CALLBACK + value), BUTTONS_PER_ROW);
            }
            rows.add(messaging.row(messaging.button(OwnerMessaging.BACK_TEXT, RULES_CALLBACK)));
            messaging.send(bot, userId, "За сколько часов до начала отмена будет бесплатной?", rows);
        }
    }

    /// Показывает варианты размера неустойки.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void showPenalty(long userId, Bot bot) {
        if (selectedBase(userId, bot) != null) {
            List<List<Button>> rows = new ArrayList<>();
            int[] percents = {0, 10, 25, 50, 75, 100};
            for (int value : percents) {
                messaging.addButton(rows,
                        messaging.button(value + "%", PENALTY_SET_CALLBACK + value), BUTTONS_PER_ROW);
            }
            rows.add(messaging.row(messaging.button(OwnerMessaging.BACK_TEXT, RULES_CALLBACK)));
            messaging.send(bot, userId, "Какой размер неустойки при поздней отмене?", rows);
        }
    }

    /// Устанавливает срок бесплатной отмены.
    ///
    /// @param userId идентификатор владельца
    /// @param hours  срок в часах
    /// @param bot    бот для отправки
    void selectHours(long userId, int hours, Bot bot) {
        Base base = selectedBase(userId, bot);
        if (base == null) {
            return;
        }
        if (hours < 0 || hours > MAX_FREE_CANCELLATION_HOURS) {
            baseService.unavailable(userId, bot);
        } else {
            save(userId, base.withCancellationRules(hours, base.penaltyPercent()), bot);
        }
    }

    /// Устанавливает размер неустойки.
    ///
    /// @param userId  идентификатор владельца
    /// @param percent размер неустойки в процентах
    /// @param bot     бот для отправки
    void selectPenalty(long userId, int percent, Bot bot) {
        Base base = selectedBase(userId, bot);
        if (base == null) {
            return;
        }
        if (percent < 0 || percent > MAX_PENALTY_PERCENT) {
            baseService.unavailable(userId, bot);
        } else {
            save(userId, base.withCancellationRules(base.freeCancellationHours(), percent), bot);
        }
    }

    /// Сохраняет правила и снова показывает их владельцу.
    ///
    /// @param userId идентификатор владельца
    /// @param base   база с новыми правилами
    /// @param bot    бот для отправки
    private void save(long userId, Base base, Bot bot) {
        baseService.updateBase(userId, base);
        showRules(userId, bot);
    }

    /// Возвращает выбранную базу владельца.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    /// @return база или null, если она не выбрана
    private Base selectedBase(long userId, Bot bot) {
        return access.selectedBase(userId, sessions.get(userId), bot);
    }
}

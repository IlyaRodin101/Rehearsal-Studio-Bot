package ru.urfu.echobot.service;

import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;

/// Выбирает срок бронирования новой или существующей базы.
final class OwnerLeadService {
    /// Минимальный срок бронирования.
    private static final int MIN_LEAD_HOURS = 1;
    /// Максимальный срок бронирования.
    private static final int MAX_LEAD_HOURS = 48;
    /// Текст запроса оборудования.
    private static final String ASK_EQUIPMENT = "Введите список оборудования (можно оставить пустым).";

    /// Сессии владельцев.
    private final OwnerSessions sessions;
    /// Проверка прав и принадлежности.
    private final OwnerAccess access;
    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Автомат состояний.
    private final OwnerStateMachine stateMachine;
    /// Основные операции с базами.
    private final OwnerBaseService baseService;

    /// Создаёт сервис срока бронирования.
    ///
    /// @param sessions     сессии владельцев
    /// @param access       проверка прав
    /// @param messaging    сообщения и клавиатуры
    /// @param stateMachine автомат состояний
    /// @param baseService  основные операции с базами
    OwnerLeadService(OwnerSessions sessions, OwnerAccess access, OwnerMessaging messaging,
                     OwnerStateMachine stateMachine, OwnerBaseService baseService) {
        this.sessions = sessions;
        this.access = access;
        this.messaging = messaging;
        this.stateMachine = stateMachine;
        this.baseService = baseService;
    }

    /// Выбирает срок бронирования для новой или существующей базы.
    ///
    /// @param userId идентификатор владельца
    /// @param hours  срок в часах
    /// @param bot    бот для отправки
    void selectLeadHours(long userId, int hours, Bot bot) {
        OwnerSession session = sessions.get(userId);
        boolean valid = hours >= MIN_LEAD_HOURS && hours <= MAX_LEAD_HOURS;
        if (session.step == OwnerStep.CREATE_LEAD && valid
                && stateMachine.transition(session, OwnerStateMachine.Event.ENTER_LEAD)) {
            session.leadHours = hours;
            messaging.send(bot, userId, ASK_EQUIPMENT, messaging.backKeyboard());
        } else if (valid) {
            updateExistingBase(userId, session, hours, bot);
        } else {
            baseService.unavailable(userId, bot);
        }
    }

    /// Сохраняет новый срок бронирования существующей базы.
    ///
    /// @param userId  идентификатор владельца
    /// @param session сессия владельца
    /// @param hours   срок в часах
    /// @param bot     бот для отправки
    private void updateExistingBase(long userId, OwnerSession session, int hours, Bot bot) {
        Base base = access.selectedBase(userId, session, bot);
        if (base != null) {
            baseService.updateBase(userId, baseService.copy(base, base.name(), base.address(), base.timezone(),
                    hours, base.equipment(), base.openingHours()));
            baseService.showBaseMenu(userId, bot);
        }
    }
}

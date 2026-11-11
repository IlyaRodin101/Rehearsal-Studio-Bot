package ru.urfu.echobot.service;

import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.repository.BaseRepository;

/// Подтверждает и выполняет удаление базы владельца.
final class OwnerBaseDeletionService {
    /// Начало названия базы в сообщениях.
    private static final String BASE_PREFIX = "База «";
    /// Подпись кнопки удаления базы.
    private static final String DELETE_BASE_TEXT = "Удалить базу";

    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Сессии владельцев.
    private final OwnerSessions sessions;
    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Автомат состояний владельца.
    private final OwnerStateMachine stateMachine;
    /// Клавиатуры списка и меню баз.
    private final OwnerBaseButtons buttons;

    /// Создаёт сервис удаления баз.
    ///
    /// @param baseRepository хранилище баз
    /// @param sessions       сессии владельцев
    /// @param messaging      сообщения и клавиатуры
    /// @param stateMachine   автомат состояний
    /// @param buttons        клавиатуры баз
    OwnerBaseDeletionService(BaseRepository baseRepository, OwnerSessions sessions, OwnerMessaging messaging,
                             OwnerStateMachine stateMachine, OwnerBaseButtons buttons) {
        this.baseRepository = baseRepository;
        this.sessions = sessions;
        this.messaging = messaging;
        this.stateMachine = stateMachine;
        this.buttons = buttons;
    }

    /// Просит подтвердить удаление базы.
    ///
    /// @param userId идентификатор владельца
    /// @param baseId идентификатор базы
    /// @param bot    бот для отправки
    void confirmBaseDelete(long userId, long baseId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        Base base = baseRepository.findById(baseId).orElse(null);
        if (base == null || base.ownerId() != userId || session.selectedBaseId != baseId) {
            unavailable(userId, bot);
        } else if (stateMachine.transition(session, OwnerStateMachine.Event.DELETE_BASE)) {
            messaging.send(bot, userId, "Удалить базу «" + base.name()
                    + "» вместе со всеми залами и записями? Это действие нельзя отменить.",
                    messaging.choices(messaging.button(DELETE_BASE_TEXT, "owner:deletebaseyes:" + base.id()),
                            messaging.button("Отмена", "owner:setting")));
        }
    }

    /// Удаляет базу после подтверждения.
    ///
    /// @param userId идентификатор владельца
    /// @param baseId идентификатор базы
    /// @param bot    бот для отправки
    void deleteBase(long userId, long baseId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        Base base = baseRepository.findById(baseId).orElse(null);
        boolean deleted = session.step == OwnerStep.DELETE_BASE && base != null && base.ownerId() == userId
                && session.selectedBaseId == baseId && baseRepository.deleteByIdAndOwnerId(baseId, userId);
        if (!deleted || !stateMachine.transition(session, OwnerStateMachine.Event.DELETE_BASE_CONFIRMED)) {
            unavailable(userId, bot);
        } else {
            session.reset();
            messaging.send(bot, userId, BASE_PREFIX + base.name() + "» удалена.", buttons.baseListButtons(userId));
        }
    }

    /// Сообщает о недоступном действии.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    private void unavailable(long userId, Bot bot) {
        messaging.send(bot, userId, OwnerMessaging.UNAVAILABLE, messaging.backKeyboard());
    }
}

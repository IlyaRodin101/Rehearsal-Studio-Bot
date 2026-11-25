package ru.urfu.echobot.service.owner;

import ru.urfu.echobot.service.Button;
import ru.urfu.echobot.service.GenericStateMachine;
import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.repository.BaseRepository;

/// Показывает список и меню баз владельца, обновляет и удаляет базы.
final class OwnerBaseService {
    /// Начало названия базы в сообщениях.
    private static final String BASE_PREFIX = "База «";
    /// Подпись кнопки удаления базы.
    private static final String DELETE_BASE_TEXT = "Удалить базу";

    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Сессии владельцев.
    private final OwnerSessions sessions;
    /// Проверка прав и принадлежности.
    private final OwnerAccess access;
    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Автомат состояний владельца.
    private final GenericStateMachine<OwnerState, OwnerEvent> stateMachine;
    /// Клавиатуры списка и меню баз и залов.
    private final OwnerButtons buttons;

    /// Создаёт сервис баз владельца.
    ///
    /// @param baseRepository хранилище баз
    /// @param sessions       сессии владельцев
    /// @param access         проверка прав
    /// @param messaging      сообщения и клавиатуры
    /// @param stateMachine   автомат состояний
    /// @param buttons        клавиатуры баз и залов
    OwnerBaseService(BaseRepository baseRepository, OwnerSessions sessions, OwnerAccess access,
                     OwnerMessaging messaging, GenericStateMachine<OwnerState, OwnerEvent> stateMachine,
                     OwnerButtons buttons) {
        this.baseRepository = baseRepository;
        this.sessions = sessions;
        this.access = access;
        this.messaging = messaging;
        this.stateMachine = stateMachine;
        this.buttons = buttons;
    }

    /// Показывает базы владельца.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void showBaseList(long userId, Bot bot) {
        if (access.requireOwner(userId, bot)) {
            OwnerSession session = sessions.get(userId);
            session.reset();
            List<Base> bases = baseRepository.findByOwnerId(userId);
            String text = bases.isEmpty() ? "У вас пока нет баз:" : "Ваши базы:";
            messaging.send(bot, userId, text, buttons.baseListButtons(userId));
        }
    }

    /// Возвращает владельца к меню базы или к списку баз.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void back(long userId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        boolean selected = session.selectedBaseId != 0 && baseRepository.findById(session.selectedBaseId)
                .filter(base -> base.ownerId() == userId).isPresent();
        if (selected) {
            showBaseMenu(userId, bot);
        } else {
            showBaseList(userId, bot);
        }
    }

    /// Выбирает базу и открывает её меню.
    ///
    /// @param userId идентификатор владельца
    /// @param baseId идентификатор базы
    /// @param bot    бот для отправки
    void selectBase(long userId, long baseId, Bot bot) {
        if (access.ownedBase(userId, baseId, bot)) {
            sessions.get(userId).selectedBaseId = baseId;
            showBaseMenu(userId, bot);
        }
    }

    /// Показывает меню выбранной базы.
    ///
    /// @param userId идентификатор владельца
    /// @param bot    бот для отправки
    void showBaseMenu(long userId, Bot bot) {
        OwnerSession session = sessions.get(userId);
        Base base = access.selectedBase(userId, session, bot);
        if (base != null) {
            stateMachine.reset(session, OwnerState.IDLE);
            messaging.send(bot, userId, BASE_PREFIX + base.name() + "». Что изменить?", buttons.baseMenuButtons(base));
        }
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
        } else if (stateMachine.transition(session, OwnerEvent.DELETE_BASE)) {
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
        boolean deleted = session.step == OwnerState.DELETE_BASE && base != null && base.ownerId() == userId
                && session.selectedBaseId == baseId && baseRepository.deleteByIdAndOwnerId(baseId, userId);
        if (!deleted || !stateMachine.transition(session, OwnerEvent.DELETE_BASE_CONFIRMED)) {
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
    void unavailable(long userId, Bot bot) {
        messaging.send(bot, userId, OwnerMessaging.UNAVAILABLE, messaging.backKeyboard());
    }

    /// Обновляет базу владельца.
    ///
    /// @param userId идентификатор владельца
    /// @param base   база с новыми значениями
    void updateBase(long userId, Base base) {
        if (base.ownerId() != userId) {
            throw new IllegalStateException("Attempt to update a base owned by another user");
        }
        baseRepository.update(base);
    }

    /// Создаёт копию базы с новыми значениями.
    ///
    /// @param base      исходная база
    /// @param name      новое название
    /// @param address   новый адрес
    /// @param timezone  новый часовой пояс
    /// @param leadHours новый срок бронирования
    /// @param equipment новое оборудование
    /// @param schedule  новый график
    /// @return копия базы
    Base copy(Base base, String name, String address, String timezone, int leadHours, String equipment,
              Map<DayOfWeek, OpeningHours> schedule) {
        return new Base(base.id(), base.ownerId(), name, address, timezone, leadHours, equipment, schedule,
                base.freeCancellationHours(), base.penaltyPercent());
    }

    /// Копирует график базы для изменения.
    ///
    /// @param base база с графиком
    /// @return изменяемая копия графика
    Map<DayOfWeek, OpeningHours> copySchedule(Base base) {
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        schedule.putAll(base.openingHours());
        return schedule;
    }

    /// Создаёт кнопки списка баз владельца.
    ///
    /// @param userId идентификатор владельца
    /// @return строки кнопок
    List<List<Button>> baseListButtons(long userId) {
        return buttons.baseListButtons(userId);
    }
}

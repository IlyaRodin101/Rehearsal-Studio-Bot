package ru.urfu.echobot.service;

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

    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Сессии владельцев.
    private final OwnerSessions sessions;
    /// Проверка прав и принадлежности.
    private final OwnerAccess access;
    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Автомат состояний владельца.
    private final OwnerStateMachine stateMachine;
    /// Клавиатуры списка и меню баз.
    private final OwnerBaseButtons buttons;
    /// Удаление баз.
    private final OwnerBaseDeletionService deletion;

    /// Создаёт сервис баз владельца.
    ///
    /// @param baseRepository хранилище баз
    /// @param sessions       сессии владельцев
    /// @param access         проверка прав
    /// @param messaging      сообщения и клавиатуры
    /// @param stateMachine   автомат состояний
    OwnerBaseService(BaseRepository baseRepository, OwnerSessions sessions, OwnerAccess access,
                     OwnerMessaging messaging, OwnerStateMachine stateMachine) {
        this.baseRepository = baseRepository;
        this.sessions = sessions;
        this.access = access;
        this.messaging = messaging;
        this.stateMachine = stateMachine;
        buttons = new OwnerBaseButtons(messaging, baseRepository);
        deletion = new OwnerBaseDeletionService(baseRepository, sessions, messaging, stateMachine, buttons);
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
            stateMachine.reset(session);
            messaging.send(bot, userId, BASE_PREFIX + base.name() + "». Что изменить?", buttons.baseMenuButtons(base));
        }
    }

    /// Просит подтвердить удаление базы.
    ///
    /// @param userId идентификатор владельца
    /// @param baseId идентификатор базы
    /// @param bot    бот для отправки
    void confirmBaseDelete(long userId, long baseId, Bot bot) {
        deletion.confirmBaseDelete(userId, baseId, bot);
    }

    /// Удаляет базу после подтверждения.
    ///
    /// @param userId идентификатор владельца
    /// @param baseId идентификатор базы
    /// @param bot    бот для отправки
    void deleteBase(long userId, long baseId, Bot bot) {
        deletion.deleteBase(userId, baseId, bot);
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
        return new Base(base.id(), base.ownerId(), name, address, timezone, leadHours, equipment, schedule);
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

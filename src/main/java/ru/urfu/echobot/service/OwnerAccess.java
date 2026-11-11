package ru.urfu.echobot.service;

import java.util.Set;
import ru.urfu.echobot.bot.Bot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.BaseRepository;
import ru.urfu.echobot.repository.RoomRepository;

/// Проверяет права владельца и принадлежность баз и залов.
final class OwnerAccess {
    /// Хранилище баз.
    private final BaseRepository baseRepository;
    /// Хранилище залов.
    private final RoomRepository roomRepository;
    /// Telegram ID владельцев.
    private final Set<Long> ownerIds;
    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;

    /// Создаёт проверку прав владельцев.
    ///
    /// @param baseRepository хранилище баз
    /// @param roomRepository хранилище залов
    /// @param ownerIds       Telegram ID владельцев
    /// @param messaging      сообщения и клавиатуры
    OwnerAccess(BaseRepository baseRepository, RoomRepository roomRepository, Set<Long> ownerIds,
                OwnerMessaging messaging) {
        this.baseRepository = baseRepository;
        this.roomRepository = roomRepository;
        this.ownerIds = Set.copyOf(ownerIds);
        this.messaging = messaging;
    }

    /// Проверяет, является ли пользователь владельцем.
    ///
    /// @param userId идентификатор пользователя
    /// @return true, если пользователь владелец
    boolean isOwner(long userId) {
        return ownerIds.contains(userId);
    }

    /// Проверяет права владельца и сообщает об отказе.
    ///
    /// @param userId идентификатор пользователя
    /// @param bot    бот для отправки
    /// @return true, если действие разрешено
    boolean requireOwner(long userId, Bot bot) {
        if (!isOwner(userId)) {
            messaging.send(bot, userId, OwnerMessaging.UNAVAILABLE);
            return false;
        }
        return true;
    }

    /// Возвращает выбранную базу владельца или сообщает об ошибке.
    ///
    /// @param userId  идентификатор владельца
    /// @param session сессия владельца
    /// @param bot     бот для отправки
    /// @return база или null
    Base selectedBase(long userId, OwnerSession session, Bot bot) {
        long baseId = session.selectedBaseId;
        if (baseId == 0 || !ownedBase(userId, baseId, bot)) {
            return null;
        }
        return baseRepository.findById(baseId).orElse(null);
    }

    /// Проверяет, что база существует и принадлежит владельцу.
    ///
    /// @param userId идентификатор владельца
    /// @param baseId идентификатор базы
    /// @param bot    бот для отправки
    /// @return true, если база принадлежит владельцу
    boolean ownedBase(long userId, long baseId, Bot bot) {
        Base base = baseRepository.findById(baseId).orElse(null);
        if (base == null || base.ownerId() != userId) {
            messaging.send(bot, userId, OwnerMessaging.UNAVAILABLE, messaging.backKeyboard());
            return false;
        }
        return true;
    }

    /// Возвращает активный зал выбранной базы владельца.
    ///
    /// @param userId  идентификатор владельца
    /// @param session сессия владельца
    /// @param roomId  идентификатор зала
    /// @param bot     бот для отправки
    /// @return зал или null
    Room activeOwnedRoom(long userId, OwnerSession session, long roomId, Bot bot) {
        Room room = roomRepository.findById(roomId).orElse(null);
        Base base = room == null ? null : baseRepository.findById(room.baseId()).orElse(null);
        if (room == null || base == null || !room.active() || base.ownerId() != userId
                || base.id() != session.selectedBaseId) {
            messaging.send(bot, userId, OwnerMessaging.UNAVAILABLE, messaging.backKeyboard());
            return null;
        }
        return room;
    }

    /// Возвращает хранилище залов.
    ///
    /// @return хранилище залов
    RoomRepository roomRepository() {
        return roomRepository;
    }
}

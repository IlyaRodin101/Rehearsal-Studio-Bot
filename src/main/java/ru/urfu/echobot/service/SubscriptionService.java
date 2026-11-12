package ru.urfu.echobot.service;

import java.util.List;
import ru.urfu.echobot.repository.SubscriptionRepository;

/// Включает и отключает подписки клиентов на «горячие» слоты баз.
public final class SubscriptionService {
    /// Хранилище подписок.
    private final SubscriptionRepository subscriptionRepository;

    /// Создаёт сервис подписок.
    ///
    /// @param subscriptionRepository хранилище подписок
    public SubscriptionService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /// Переключает подписку клиента на базу.
    ///
    /// @param userId идентификатор клиента
    /// @param baseId идентификатор базы
    /// @return true, если подписка включена; false, если отключена
    public boolean toggle(long userId, long baseId) {
        if (subscriptionRepository.isSubscribed(userId, baseId)) {
            subscriptionRepository.unsubscribe(userId, baseId);
            return false;
        }
        subscriptionRepository.subscribe(userId, baseId);
        return true;
    }

    /// Проверяет, подписан ли клиент на базу.
    ///
    /// @param userId идентификатор клиента
    /// @param baseId идентификатор базы
    /// @return true, если подписка включена
    public boolean isSubscribed(long userId, long baseId) {
        return subscriptionRepository.isSubscribed(userId, baseId);
    }

    /// Возвращает базы, на которые подписан клиент.
    ///
    /// @param userId идентификатор клиента
    /// @return идентификаторы баз
    public List<Long> subscribedBaseIds(long userId) {
        return subscriptionRepository.findBaseIdsByUser(userId);
    }
}

package ru.urfu.echobot.repository;

import java.util.List;

/// Хранилище подписок клиентов на «горячие» слоты баз.
public interface SubscriptionRepository {

    /// Возвращает базы, на которые подписан клиент.
    ///
    /// @param userId идентификатор клиента
    /// @return идентификаторы баз в порядке возрастания
    List<Long> findBaseIdsByUser(long userId);

    /// Возвращает подписчиков базы.
    ///
    /// @param baseId идентификатор базы
    /// @return идентификаторы клиентов в порядке возрастания
    List<Long> findUserIdsByBase(long baseId);

    /// Проверяет, подписан ли клиент на базу.
    ///
    /// @param userId идентификатор клиента
    /// @param baseId идентификатор базы
    /// @return true, если подписка существует
    boolean isSubscribed(long userId, long baseId);

    /// Добавляет подписку клиента на базу. Повторная подписка не создаёт дубль.
    ///
    /// @param userId идентификатор клиента
    /// @param baseId идентификатор базы
    void subscribe(long userId, long baseId);

    /// Удаляет подписку клиента на базу.
    ///
    /// @param userId идентификатор клиента
    /// @param baseId идентификатор базы
    void unsubscribe(long userId, long baseId);
}

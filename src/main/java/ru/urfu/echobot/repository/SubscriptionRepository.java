package ru.urfu.echobot.repository;

import java.util.List;

/// Хранилище подписок клиентов на «горячие» слоты баз.
public interface SubscriptionRepository {

    /// Возвращает базы, на которые подписан клиент.
    ///
    /// @param userId идентификатор клиента
    /// @return идентификаторы баз в порядке подписки
    List<Long> findBaseIdsByUser(long userId);

    /// Возвращает клиентов, подписанных на базу.
    ///
    /// @param baseId идентификатор базы
    /// @return идентификаторы клиентов
    List<Long> findUserIdsByBase(long baseId);

    /// Проверяет подписку клиента на базу.
    ///
    /// @param userId идентификатор клиента
    /// @param baseId идентификатор базы
    /// @return true, если клиент подписан на базу
    boolean isSubscribed(long userId, long baseId);

    /// Подписывает клиента на базу. Повторная подписка ничего не меняет.
    ///
    /// @param userId идентификатор клиента
    /// @param baseId идентификатор базы
    void subscribe(long userId, long baseId);

    /// Отписывает клиента от базы.
    ///
    /// @param userId идентификатор клиента
    /// @param baseId идентификатор базы
    void unsubscribe(long userId, long baseId);
}

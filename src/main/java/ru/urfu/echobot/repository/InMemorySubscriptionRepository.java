package ru.urfu.echobot.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// Хранит подписки в памяти. Используется, когда постоянное хранилище не передано.
public final class InMemorySubscriptionRepository implements SubscriptionRepository {
    /// Подписки клиентов: идентификатор клиента и идентификаторы баз.
    private final Map<Long, Set<Long>> basesByUser = new ConcurrentHashMap<>();

    /// Возвращает базы, на которые подписан клиент.
    ///
    /// @param userId идентификатор клиента
    /// @return идентификаторы баз в порядке возрастания
    @Override
    public List<Long> findBaseIdsByUser(long userId) {
        return basesByUser.getOrDefault(userId, Set.of()).stream().sorted().toList();
    }

    /// Возвращает подписчиков базы.
    ///
    /// @param baseId идентификатор базы
    /// @return идентификаторы клиентов в порядке возрастания
    @Override
    public List<Long> findUserIdsByBase(long baseId) {
        List<Long> userIds = new ArrayList<>();
        for (Map.Entry<Long, Set<Long>> entry : basesByUser.entrySet()) {
            if (entry.getValue().contains(baseId)) {
                userIds.add(entry.getKey());
            }
        }
        return userIds.stream().sorted().toList();
    }

    /// Проверяет, подписан ли клиент на базу.
    ///
    /// @param userId идентификатор клиента
    /// @param baseId идентификатор базы
    /// @return true, если подписка существует
    @Override
    public boolean isSubscribed(long userId, long baseId) {
        return basesByUser.getOrDefault(userId, Set.of()).contains(baseId);
    }

    /// Добавляет подписку клиента на базу.
    ///
    /// @param userId идентификатор клиента
    /// @param baseId идентификатор базы
    @Override
    public void subscribe(long userId, long baseId) {
        basesByUser.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(baseId);
    }

    /// Удаляет подписку клиента на базу.
    ///
    /// @param userId идентификатор клиента
    /// @param baseId идентификатор базы
    @Override
    public void unsubscribe(long userId, long baseId) {
        basesByUser.computeIfPresent(userId, (ignored, bases) -> {
            bases.remove(baseId);
            return bases.isEmpty() ? null : bases;
        });
    }
}

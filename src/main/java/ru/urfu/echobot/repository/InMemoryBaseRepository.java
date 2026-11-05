package ru.urfu.echobot.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import ru.urfu.echobot.model.Base;

/// Хранилище баз в памяти. Данные задаются при создании и не меняются.
public final class InMemoryBaseRepository implements BaseRepository {
    /// Все базы в порядке добавления.
    private final List<Base> bases;
    private final AtomicLong ids = new AtomicLong();

    /// Создаёт хранилище с заданным набором баз.
    ///
    /// @param bases начальные базы
    public InMemoryBaseRepository(List<Base> bases) {
        this.bases = new ArrayList<>(bases);
        bases.stream().mapToLong(Base::id).max().ifPresent(ids::set);
    }

    @Override
    public synchronized List<Base> findAll() {
        return List.copyOf(bases);
    }

    @Override
    public synchronized Optional<Base> findById(long id) {
        return bases.stream().filter(base -> base.id() == id).findFirst();
    }

    @Override
    public synchronized List<Base> findByOwnerId(long ownerId) {
        return bases.stream().filter(base -> base.ownerId() == ownerId).toList();
    }

    @Override
    public synchronized Base save(Base base) {
        Base stored = new Base(ids.incrementAndGet(), base.ownerId(), base.name(), base.address(), base.timezone(),
                base.bookingLeadHours(), base.equipment(), base.openingHours());
        bases.add(stored);
        return stored;
    }

    @Override
    public synchronized void update(Base base) {
        for (int index = 0; index < bases.size(); index++) {
            if (bases.get(index).id() == base.id()) {
                bases.set(index, base);
                return;
            }
        }
        throw new IllegalArgumentException("Base does not exist: " + base.id());
    }
}

package ru.urfu.echobot.repository;

import ru.urfu.echobot.model.Base;

import java.util.List;
import java.util.Optional;

/// Хранилище баз в памяти. Данные задаются при создании и не меняются.
public final class InMemoryBaseRepository implements BaseRepository {
    /// Все базы в порядке добавления.
    private final List<Base> bases;

    /// Создаёт хранилище с заданным набором баз.
    ///
    /// @param bases начальные базы
    public InMemoryBaseRepository(List<Base> bases) {
        this.bases = List.copyOf(bases);
    }

    @Override
    public List<Base> findAll() {
        return bases;
    }

    @Override
    public Optional<Base> findById(long id) {
        return bases.stream().filter(base -> base.id() == id).findFirst();
    }
}

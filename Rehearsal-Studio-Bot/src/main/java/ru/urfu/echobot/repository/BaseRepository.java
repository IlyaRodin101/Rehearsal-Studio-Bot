package ru.urfu.echobot.repository;

import java.util.List;
import java.util.Optional;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.Room;

/// Хранилище репетиционных баз. Сервис не знает, где именно лежат данные.
public interface BaseRepository {

    /// Возвращает все базы.
    ///
    /// @return список баз в порядке добавления
    List<Base> findAll();

    /// Ищет базу по идентификатору.
    ///
    /// @param id идентификатор базы
    /// @return база или пустой результат, если такой нет
    Optional<Base> findById(long id);

    /// Возвращает базы владельца.
    List<Base> findByOwnerId(long ownerId);

    /// Добавляет новую базу.
    Base save(Base base);

    /// Atomically saves a base, its weekly schedule, and its initial rooms.
    ///
    /// @param base base to create
    /// @param rooms rooms to create with the base
    /// @return saved base with its generated ID
    Base save(Base base, List<Room> rooms);

    /// Обновляет базу с тем же идентификатором.
    void update(Base base);
}

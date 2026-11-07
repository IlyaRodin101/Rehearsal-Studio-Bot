package ru.urfu.echobot.repository;

import java.util.List;
import java.util.Optional;
import ru.urfu.echobot.model.Base;

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

    /// Обновляет базу с тем же идентификатором.
    void update(Base base);

    /// Удаляет базу, если она принадлежит указанному владельцу.
    ///
    /// @param id      идентификатор базы
    /// @param ownerId идентификатор владельца
    /// @return true, если база удалена
    boolean deleteByIdAndOwnerId(long id, long ownerId);
}

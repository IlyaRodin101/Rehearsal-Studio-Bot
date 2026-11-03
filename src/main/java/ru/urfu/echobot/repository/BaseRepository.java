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
}

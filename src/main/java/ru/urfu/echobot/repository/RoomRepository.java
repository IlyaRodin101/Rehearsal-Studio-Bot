package ru.urfu.echobot.repository;

import java.util.List;
import java.util.Optional;
import ru.urfu.echobot.model.Room;

/// Хранилище залов.
public interface RoomRepository {

    /// Возвращает залы базы.
    ///
    /// @param baseId идентификатор базы
    /// @return список залов в порядке добавления
    List<Room> findByBaseId(long baseId);

    /// Ищет зал по идентификатору.
    ///
    /// @param roomId идентификатор зала
    /// @return зал или пустой результат, если такого нет
    Optional<Room> findById(long roomId);
}

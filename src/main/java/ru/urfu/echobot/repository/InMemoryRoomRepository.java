package ru.urfu.echobot.repository;

import java.util.List;
import java.util.Optional;
import ru.urfu.echobot.model.Room;

/// Хранилище залов в памяти. Данные задаются при создании и не меняются.
public final class InMemoryRoomRepository implements RoomRepository {
    /// Все залы в порядке добавления.
    private final List<Room> rooms;

    /// Создаёт хранилище с заданным набором залов.
    ///
    /// @param rooms начальные залы
    public InMemoryRoomRepository(List<Room> rooms) {
        this.rooms = List.copyOf(rooms);
    }

    @Override
    public List<Room> findByBaseId(long baseId) {
        return rooms.stream().filter(room -> room.baseId() == baseId).toList();
    }

    @Override
    public Optional<Room> findById(long roomId) {
        return rooms.stream().filter(room -> room.id() == roomId).findFirst();
    }
}

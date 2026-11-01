package ru.urfu.echobot.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import ru.urfu.echobot.model.Room;

/// Хранилище залов в памяти. Данные задаются при создании и не меняются.
public final class InMemoryRoomRepository implements RoomRepository {
    /// Все залы в порядке добавления.
    private final List<Room> rooms;
    private final AtomicLong ids = new AtomicLong();

    /// Создаёт хранилище с заданным набором залов.
    ///
    /// @param rooms начальные залы
    public InMemoryRoomRepository(List<Room> rooms) {
        this.rooms = new ArrayList<>(rooms);
        rooms.stream().mapToLong(Room::id).max().ifPresent(ids::set);
    }

    @Override
    public synchronized List<Room> findByBaseId(long baseId) {
        return rooms.stream().filter(room -> room.baseId() == baseId && room.active()).toList();
    }

    @Override
    public synchronized Optional<Room> findById(long roomId) {
        return rooms.stream().filter(room -> room.id() == roomId).findFirst();
    }

    @Override
    public synchronized Room save(Room room) {
        Room stored = new Room(ids.incrementAndGet(), room.baseId(), room.name(), room.commentRequired());
        rooms.add(stored);
        return stored;
    }

    @Override
    public synchronized void update(Room room) {
        for (int index = 0; index < rooms.size(); index++) {
            if (rooms.get(index).id() == room.id()) {
                rooms.set(index, room);
                return;
            }
        }
        throw new IllegalArgumentException("Room does not exist: " + room.id());
    }

    @Override
    public synchronized void deactivate(long roomId) {
        for (int index = 0; index < rooms.size(); index++) {
            Room room = rooms.get(index);
            if (room.id() == roomId) {
                rooms.set(index, new Room(room.id(), room.baseId(), room.name(), room.commentRequired(), false));
                return;
            }
        }
    }
}

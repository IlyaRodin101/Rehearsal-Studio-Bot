package ru.urfu.echobot.repository;

import java.util.List;
import java.util.Optional;
import ru.urfu.echobot.model.Base;

/// Хранит репетиционные базы и их недельный график в SQLite.
public final class SqliteBaseRepository implements BaseRepository {
    /// Читатель баз.
    private final SqliteBaseReader reader;
    /// Хранилище записи баз.
    private final SqliteBaseWriter writer;
    /// Удаление баз.
    private final SqliteBaseDeletion deletion;

    /// Создаёт хранилище баз.
    ///
    /// @param database база данных SQLite
    public SqliteBaseRepository(SqliteDatabase database) {
        SqliteBaseSchedule schedule = new SqliteBaseSchedule();
        reader = new SqliteBaseReader(database, schedule);
        writer = new SqliteBaseWriter(database, schedule);
        deletion = new SqliteBaseDeletion(database, schedule);
    }

    /// Возвращает все базы.
    ///
    /// @return список баз в порядке добавления
    @Override
    public List<Base> findAll() {
        return reader.findAll();
    }

    /// Ищет базу по идентификатору.
    ///
    /// @param id идентификатор базы
    /// @return база или пустой результат, если такой нет
    @Override
    public Optional<Base> findById(long id) {
        return reader.findById(id);
    }

    /// Возвращает базы владельца.
    ///
    /// @param ownerId идентификатор владельца
    /// @return базы владельца в порядке добавления
    @Override
    public List<Base> findByOwnerId(long ownerId) {
        return reader.findByOwnerId(ownerId);
    }

    /// Добавляет новую базу вместе с графиком.
    ///
    /// @param base сохраняемая база
    /// @return сохранённая база с присвоенным идентификатором
    @Override
    public Base save(Base base) {
        return writer.save(base);
    }

    /// Обновляет базу с тем же идентификатором и заменяет её график.
    ///
    /// @param base обновлённая база
    @Override
    public void update(Base base) {
        writer.update(base);
    }

    /// Удаляет базу вместе с залами, графиком и бронями.
    ///
    /// @param id      идентификатор базы
    /// @param ownerId идентификатор владельца
    /// @return true, если база удалена
    @Override
    public boolean deleteByIdAndOwnerId(long id, long ownerId) {
        return deletion.deleteByIdAndOwnerId(id, ownerId);
    }
}

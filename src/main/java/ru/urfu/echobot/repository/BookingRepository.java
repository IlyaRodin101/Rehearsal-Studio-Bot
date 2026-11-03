package ru.urfu.echobot.repository;

import java.time.LocalDateTime;
import java.util.List;
import ru.urfu.echobot.model.Booking;

/// Хранилище броней.
public interface BookingRepository {

    /// Добавляет бронь, если зал в это время свободен. Проверка и добавление выполняются как одна
    /// неделимая операция, поэтому из двух одновременных попыток на одно время успешна только одна.
    ///
    /// @param booking новая бронь
    /// @return true, если бронь добавлена; false, если время пересекается с другой бронью
    boolean addIfFree(Booking booking);

    /// Проверяет, пересекается ли промежуток времени с существующими бронями зала.
    ///
    /// @param roomId идентификатор зала
    /// @param start  начало промежутка
    /// @param end    конец промежутка
    /// @return true, если зал в этот промежуток занят хотя бы частично
    boolean hasConflict(long roomId, LocalDateTime start, LocalDateTime end);

    /// Возвращает все брони клиента, в том числе уже прошедшие.
    ///
    /// @param userId идентификатор клиента
    /// @return брони клиента, отсортированные по времени начала
    List<Booking> findByUser(long userId);
}

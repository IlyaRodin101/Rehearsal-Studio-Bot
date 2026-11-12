package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.urfu.echobot.bot.DummyBot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.SqliteBaseRepository;
import ru.urfu.echobot.repository.SqliteBookingRepository;
import ru.urfu.echobot.repository.SqliteDatabase;
import ru.urfu.echobot.repository.SqliteRoomRepository;

/// Проверяет, что один сервис обслуживает несколько одновременных диалогов бронирования.
final class BookingConcurrencyTest {
    /// Количество одновременно бронирующих клиентов.
    private static final int CLIENTS = 6;
    /// Идентификатор первого клиента.
    private static final long FIRST_CLIENT = 500L;
    /// Час начала первой брони.
    private static final int FIRST_HOUR = 10;
    /// Дата, на которую бронируют клиенты.
    private static final String DATE = "2026-10-24";
    /// Идентификатор базы, на которую бронируют клиенты.
    private static final int BASE_ID = 1;
    /// Идентификатор зала, который бронируют клиенты.
    private static final int ROOM_ID = 1;
    /// Время ожидания запуска и завершения диалогов.
    private static final long TIMEOUT_SECONDS = 30;

    /// Временная директория для файла базы данных.
    @TempDir
    private Path temporaryDirectory;
    /// Общий для всех диалогов сервис.
    private BookingMessageProcessingService service;
    /// Хранилище броней, общее для всех диалогов.
    private SqliteBookingRepository bookingRepository;
    /// Пул потоков, каждый из которых изображает отдельного клиента.
    private ExecutorService executor;

    /// Создаёт общую базу с одним залом и общий сервис обработки сообщений.
    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-20T07:00:00Z"), ZoneOffset.UTC);
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("concurrent.db").toString());
        SqliteBaseRepository baseRepository = new SqliteBaseRepository(database);
        SqliteRoomRepository roomRepository = new SqliteRoomRepository(database);
        bookingRepository = new SqliteBookingRepository(database);
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day != DayOfWeek.SUNDAY) {
                schedule.put(day, new OpeningHours(9, 24));
            }
        }
        Base center = baseRepository.save(new Base(0, 0, "Центр", "ул. Ленина, 1", "Europe/Moscow",
                2, "Барабаны", schedule));
        roomRepository.save(new Room(0, center.id(), "Зал 1", false));
        service = new BookingMessageProcessingService(baseRepository, roomRepository, bookingRepository,
                new BookingRules(clock));
        executor = Executors.newFixedThreadPool(CLIENTS);
    }

    /// Останавливает пул потоков после каждого теста.
    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    /// Given: шесть клиентов. When: одновременно бронируют разные часы. Then: каждая бронь создана.
    @Test
    void clientsBookDifferentSlotsSimultaneously() throws Exception {
        List<DummyBot> bots = runTogether(
                (index, bot) -> book(FIRST_CLIENT + index, FIRST_HOUR + index, 1, bot));

        for (DummyBot bot : bots) {
            assertTrue(hasText(bot, "Готово, запись создана."));
        }
        for (int index = 0; index < CLIENTS; index++) {
            assertEquals(1, bookingRepository.findByUser(FIRST_CLIENT + index).size());
        }
    }

    /// Given: шесть клиентов. When: одновременно бронируют один и тот же час. Then: успешен только один.
    @Test
    void clientsRacingForSameSlotCreateSingleBooking() throws Exception {
        List<DummyBot> bots = runTogether(
                (index, bot) -> book(FIRST_CLIENT + index, FIRST_HOUR, 1, bot));

        long created = bots.stream().filter(bot -> hasText(bot, "Готово, запись создана.")).count();
        long rejected = bots.stream()
                .filter(bot -> lastText(bot).startsWith("Это время больше недоступно"))
                .count();

        assertEquals(1, created);
        assertEquals(CLIENTS - 1, rejected);
    }

    /// Запускает всех клиентов одновременно и возвращает их ботов.
    ///
    /// @param action действие клиента, зависящее от его порядкового номера
    /// @return боты в порядке номеров клиентов
    /// @throws Exception если поток не завершился за отведённое время
    private List<DummyBot> runTogether(ClientAction action) throws Exception {
        CountDownLatch ready = new CountDownLatch(CLIENTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<DummyBot>> futures = new ArrayList<>();
        for (int index = 0; index < CLIENTS; index++) {
            int position = index;
            futures.add(executor.submit(() -> {
                DummyBot bot = new DummyBot();
                ready.countDown();
                start.await();
                action.perform(position, bot);
                return bot;
            }));
        }
        assertTrue(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        start.countDown();
        List<DummyBot> bots = new ArrayList<>();
        for (Future<DummyBot> future : futures) {
            bots.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        }
        return bots;
    }

    /// Проводит клиента по всему диалогу бронирования и подтверждает запись.
    ///
    /// @param user  идентификатор клиента
    /// @param hour  час начала
    /// @param hours длительность в часах
    /// @param bot   бот клиента
    private void book(long user, int hour, int hours, DummyBot bot) {
        press(user, "menu:book", bot);
        press(user, "book:base:" + BASE_ID, bot);
        press(user, "book:room:" + ROOM_ID, bot);
        press(user, "book:day:" + DATE, bot);
        press(user, "book:time:" + DATE + ":" + hour, bot);
        press(user, "book:hours:" + hours, bot);
        service.processMessage(new Message("Клиент " + user, null, List.of()), user, bot);
        service.processMessage(new Message(String.format("+7999000%04d", user), null, List.of()), user, bot);
        press(user, "book:skip", bot);
        press(user, "book:confirm", bot);
    }

    /// Отправляет сервису нажатие кнопки от имени клиента.
    ///
    /// @param user клиент
    /// @param data данные кнопки
    /// @param bot  бот клиента
    private void press(long user, String data, DummyBot bot) {
        service.processMessage(new Message(null, data, List.of()), user, bot);
    }

    /// Проверяет, отправлял ли бот сообщение с указанным текстом.
    ///
    /// @param bot  бот клиента
    /// @param text искомый текст
    /// @return true, если сообщение найдено
    private boolean hasText(DummyBot bot, String text) {
        return bot.messages().stream().anyMatch(message -> text.equals(message.text()));
    }

    /// Возвращает текст последнего сообщения бота.
    ///
    /// @param bot бот клиента
    /// @return текст последнего сообщения
    private String lastText(DummyBot bot) {
        String text = bot.lastMessage().text();
        return text == null ? "" : text;
    }

    /// Действие клиента внутри общего запуска.
    @FunctionalInterface
    private interface ClientAction {
        /// Выполняет диалог клиента.
        ///
        /// @param index порядковый номер клиента
        /// @param bot   бот клиента
        void perform(int index, DummyBot bot);
    }
}

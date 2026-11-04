package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.urfu.echobot.bot.DummyBot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.SqliteBaseRepository;
import ru.urfu.echobot.repository.SqliteDatabase;
import ru.urfu.echobot.repository.SqliteRoomRepository;

/// Tests owner access controls and configuration flows.
final class OwnerManagementServiceTest {
    /// Authorized owner used by the configuration tests.
    private static final long OWNER = 101;
    /// Different owner used for access-control assertions.
    private static final long OTHER_OWNER = 102;

    /// Temporary directory that isolates the SQLite database for each test.
    @TempDir
    private Path temporaryDirectory;
    /// SQLite repository for bases in the test database.
    private SqliteBaseRepository bases;
    /// SQLite repository for rooms in the test database.
    private SqliteRoomRepository rooms;
    /// Owner conversation service under test.
    private OwnerManagementService service;
    /// Bot double that captures responses.
    private DummyBot bot;

    /// Creates an isolated SQLite database and a base owned by {@link #OWNER}.
    @BeforeEach
    void setUp() {
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        schedule.put(DayOfWeek.MONDAY, new OpeningHours(9, 24));
        SqliteDatabase database = new SqliteDatabase(temporaryDirectory.resolve("owner-test.db").toString());
        bases = new SqliteBaseRepository(database);
        rooms = new SqliteRoomRepository(database);
        Base base = bases.save(new Base(0, OWNER, "Центр", "Ленина, 1",
                "Europe/Moscow", 2, "Оборудование", schedule));
        rooms.save(new Room(0, base.id(), "Зал 1", false));
        service = new OwnerManagementService(bases, rooms, Set.of(OWNER, OTHER_OWNER));
        bot = new DummyBot();
    }

    /// Verifies that a base can be created with a weekly schedule and initial rooms.
    @Test
    void ownerCanCreateBaseWithScheduleAndRooms() {
        press(OWNER, "owner:add");
        type(OWNER, "Север");
        type(OWNER, "Ленина, 15");
        type(OWNER, "  Europe/Moscow  ");
        for (int day = 0; day < 6; day++) {
            press(OWNER, "owner:setopen:9");
            press(OWNER, "owner:setclose:24");
        }
        press(OWNER, "owner:off");
        type(OWNER, "Студия");
        press(OWNER, "owner:newrequired:yes");
        press(OWNER, "owner:newroom:done");
        press(OWNER, "owner:lead:2");
        type(OWNER, "Барабаны");

        Base created = bases.findByOwnerId(OWNER).stream()
                .filter(base -> "Север".equals(base.name())).findFirst().orElseThrow();
        assertEquals(2, created.bookingLeadHours());
        assertEquals("Europe/Moscow", created.timezone());
        assertEquals(6, created.openingHours().size());
        assertFalse(created.openingHours().containsKey(DayOfWeek.SUNDAY));
        assertEquals(1, rooms.findByBaseId(created.id()).size());
        assertTrue(rooms.findByBaseId(created.id()).getFirst().commentRequired());
        assertEquals("База «Север» создана.", bot.lastMessage().text());
    }

    /// Verifies that editing a base and managing its rooms updates SQLite.
    @Test
    void ownerCanEditBaseAndAddToggleAndDeleteRooms() {
        press(OWNER, "owner:base:1");
        press(OWNER, "owner:edit:name");
        type(OWNER, "Центр обновлён");
        assertEquals("Центр обновлён", bases.findById(1).orElseThrow().name());
        press(OWNER, "owner:edit:timezone");
        type(OWNER, "  Europe/Moscow  ");
        assertEquals("Europe/Moscow", bases.findById(1).orElseThrow().timezone());

        press(OWNER, "owner:rooms");
        press(OWNER, "owner:addroom");
        type(OWNER, "Студия");
        press(OWNER, "owner:newrequired:yes");
        Room room = rooms.findByBaseId(1).stream()
                .filter(item -> "Студия".equals(item.name())).findFirst().orElseThrow();
        assertTrue(room.commentRequired());

        press(OWNER, "owner:room:" + room.id());
        press(OWNER, "owner:toggle:" + room.id());
        assertFalse(rooms.findById(room.id()).orElseThrow().commentRequired());
        press(OWNER, "owner:delete:" + room.id());
        assertTrue(rooms.findById(room.id()).orElseThrow().active());
        press(OWNER, "owner:deleteyes:" + room.id());
        assertFalse(rooms.findById(room.id()).orElseThrow().active());
        assertEquals(1, rooms.findByBaseId(1).size());
    }

    /// Ensures a command-like base name is accepted while the owner is entering it.
    @Test
    void commandTextCanBeUsedAsBaseNameDuringCreation() {
        press(OWNER, "owner:add");
        type(OWNER, "Назад");

        assertEquals("Введите адрес базы.", bot.lastMessage().text());
    }

    /// Verifies that a day off cannot expose a closing-hour selector or change the schedule.
    @Test
    void closingHourCannotBeSetBeforeOpeningOnDayOff() {
        press(OWNER, "owner:base:1");
        press(OWNER, "owner:schedule");
        press(OWNER, "owner:day:7");
        press(OWNER, "owner:closing:7");

        assertEquals("Сначала задайте час открытия.", bot.lastMessage().text());
        press(OWNER, "owner:setclose:12");
        assertEquals("Сначала задайте час открытия.", bot.lastMessage().text());
        assertFalse(bases.findById(1).orElseThrow().openingHours().containsKey(DayOfWeek.SUNDAY));
    }

    /// Ensures a callback with a missing argument is rejected without indexing past its bounds.
    @Test
    void malformedCallbackIsRejected() {
        service.handleCallback(OWNER, new String[] {"owner", "setopen"}, bot);

        assertEquals("Действие недоступно.", bot.lastMessage().text());
    }

    /// Ensures a user cannot modify a base belonging to someone else.
    @Test
    void ownerCannotModifyAnotherOwnersBase() {
        service.handleCallback(OTHER_OWNER, "owner:base:1".split(":"), bot);

        assertEquals("Действие недоступно.", bot.lastMessage().text());
        assertEquals("Центр", bases.findById(1).orElseThrow().name());
    }

    /// Sends one callback through the owner service.
    ///
    /// @param userId sender ID
    /// @param data callback data
    private void press(long userId, String data) {
        service.handleCallback(userId, data.split(":"), bot);
    }

    /// Sends one text update through the owner service.
    ///
    /// @param userId sender ID
    /// @param value text content
    private void type(long userId, String value) {
        service.handleText(userId, value, bot);
    }
}

package ru.urfu.echobot.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.urfu.echobot.bot.DummyBot;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.model.OpeningHours;
import ru.urfu.echobot.model.Room;
import ru.urfu.echobot.repository.InMemoryBaseRepository;
import ru.urfu.echobot.repository.InMemoryRoomRepository;

/// Tests owner access controls and configuration flows.
final class OwnerManagementServiceTest {
    private static final long OWNER = 101;
    private static final long OTHER_OWNER = 102;

    private InMemoryBaseRepository bases;
    private InMemoryRoomRepository rooms;
    private OwnerManagementService service;
    private DummyBot bot;

    @BeforeEach
    void setUp() {
        Map<DayOfWeek, OpeningHours> schedule = new EnumMap<>(DayOfWeek.class);
        schedule.put(DayOfWeek.MONDAY, new OpeningHours(9, 24));
        bases = new InMemoryBaseRepository(List.of(new Base(1, OWNER, "Центр", "Ленина, 1",
                "Europe/Moscow", 2, "Оборудование", schedule)));
        rooms = new InMemoryRoomRepository(List.of(new Room(1, 1, "Зал 1", false)));
        service = new OwnerManagementService(bases, rooms, Set.of(OWNER, OTHER_OWNER));
        bot = new DummyBot();
    }

    @Test
    void ownerCanCreateBaseWithScheduleAndRooms() {
        press(OWNER, "owner:add");
        type(OWNER, "Север");
        type(OWNER, "Ленина, 15");
        type(OWNER, "Europe/Moscow");
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

    @Test
    void ownerCanEditBaseAndAddToggleAndDeleteRooms() {
        press(OWNER, "owner:base:1");
        press(OWNER, "owner:edit:name");
        type(OWNER, "Центр обновлён");
        assertEquals("Центр обновлён", bases.findById(1).orElseThrow().name());

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

    @Test
    void ownerCannotModifyAnotherOwnersBase() {
        service.handleCallback(OTHER_OWNER, "owner:base:1".split(":"), bot);

        assertEquals("Действие недоступно.", bot.lastMessage().text());
        assertEquals("Центр", bases.findById(1).orElseThrow().name());
    }

    @Test
    void ownerCanDeleteOwnBaseAfterConfirmation() {
        press(OWNER, "owner:base:1");
        assertTrue(bot.lastMessage().keyboard().stream()
                .flatMap(List::stream)
                .anyMatch(button -> "Удалить базу".equals(button.text())));

        press(OWNER, "owner:deletebase:1");
        assertTrue(bot.lastMessage().text().contains("вместе со всеми залами и записями"));
        press(OWNER, "owner:deletebaseyes:1");

        assertTrue(bases.findById(1).isEmpty());
        assertEquals("База «Центр» удалена.", bot.lastMessage().text());
    }

    @Test
    void ownerCannotDeleteAnotherOwnersBase() {
        press(OWNER, "owner:base:1");

        service.handleCallback(OTHER_OWNER, "owner:deletebase:1".split(":"), bot);

        assertEquals("Действие недоступно.", bot.lastMessage().text());
        assertTrue(bases.findById(1).isPresent());
    }

    private void press(long userId, String data) {
        service.handleCallback(userId, data.split(":"), bot);
    }

    private void type(long userId, String value) {
        service.handleText(userId, value, bot);
    }
}

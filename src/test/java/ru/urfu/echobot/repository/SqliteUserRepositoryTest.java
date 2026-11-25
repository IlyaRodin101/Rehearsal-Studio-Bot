package ru.urfu.echobot.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.urfu.echobot.model.TelegramUser;

/// Проверяет сохранение известных пользователей и администраторов в SQLite.
final class SqliteUserRepositoryTest {
    @TempDir
    private Path temporaryDirectory;
    private SqliteDatabase database;

    /// Создаёт отдельную базу данных для каждого теста.
    @BeforeEach
    void setUp() {
        database = new SqliteDatabase(temporaryDirectory.resolve("bookings.db").toString());
    }

    /// Имя пользователя ищется без учёта регистра и символа `@`, а последнее имя заменяет прежнее.
    @Test
    void userIsFoundByNormalizedUsernameAndNameIsUpdated() {
        SqliteUserRepository users = new SqliteUserRepository(database);

        users.remember(201, "@Ivan_Admin");
        users.remember(201, "ivan_new");

        TelegramUser loaded = users.findByUsername("@IVAN_NEW").orElseThrow();
        assertEquals(201, loaded.id());
        assertEquals("ivan_new", loaded.username());
        assertTrue(users.findByUsername("ivan_admin").isEmpty());
    }

    /// Администраторы сохраняются между пересозданием хранилища и удаляются по идентификатору.
    @Test
    void administratorsSurviveRepositoryRecreation() {
        SqliteUserRepository users = new SqliteUserRepository(database);
        SqliteAdministratorRepository administrators = new SqliteAdministratorRepository(database);
        users.remember(201, "anna_admin");
        users.remember(202, "oleg_admin");
        administrators.add(users.findByUsername("anna_admin").orElseThrow());

        SqliteDatabase reopened = new SqliteDatabase(temporaryDirectory.resolve("bookings.db").toString());
        SqliteAdministratorRepository reopenedAdmins = new SqliteAdministratorRepository(reopened);

        assertTrue(reopenedAdmins.isAdministrator(201));
        assertEquals(1, reopenedAdmins.findAll().size());
        assertEquals("anna_admin", reopenedAdmins.findAll().getFirst().username());

        reopenedAdmins.remove(201);
        assertFalse(reopenedAdmins.isAdministrator(201));
    }
}

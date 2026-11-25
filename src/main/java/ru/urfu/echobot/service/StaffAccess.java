package ru.urfu.echobot.service;

import java.util.Set;

/// Проверяет, относится ли пользователь к персоналу: владельцам или администраторам.
public final class StaffAccess {
    /// Telegram ID владельцев.
    private final Set<Long> ownerIds;
    /// Справочник известных пользователей и администраторов.
    private final UserDirectory users;

    /// Создаёт проверку прав персонала.
    ///
    /// @param ownerIds Telegram ID владельцев
    /// @param users    справочник известных пользователей и администраторов
    public StaffAccess(Set<Long> ownerIds, UserDirectory users) {
        this.ownerIds = Set.copyOf(ownerIds);
        this.users = users;
    }

    /// Проверяет, является ли пользователь владельцем.
    ///
    /// @param userId Telegram ID пользователя
    /// @return true, если пользователь владелец
    public boolean isOwner(long userId) {
        return ownerIds.contains(userId);
    }

    /// Проверяет, является ли пользователь администратором.
    ///
    /// @param userId Telegram ID пользователя
    /// @return true, если пользователь администратор
    public boolean isAdministrator(long userId) {
        return users.isAdministrator(userId);
    }

    /// Проверяет, является ли пользователь владельцем или администратором.
    ///
    /// @param userId Telegram ID пользователя
    /// @return true, если пользователь относится к персоналу
    public boolean isStaff(long userId) {
        return isOwner(userId) || isAdministrator(userId);
    }
}

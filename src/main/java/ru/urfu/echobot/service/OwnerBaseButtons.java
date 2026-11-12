package ru.urfu.echobot.service;

import java.util.ArrayList;
import java.util.List;
import ru.urfu.echobot.model.Base;
import ru.urfu.echobot.repository.BaseRepository;

/// Создаёт клавиатуры списка баз и меню выбранной базы.
final class OwnerBaseButtons {
    /// Подпись кнопки удаления базы.
    private static final String DELETE_BASE_TEXT = "Удалить базу";
    /// Начало callback выбора базы.
    private static final String BASE_CALLBACK = "owner:base:";
    /// Начало callback удаления базы.
    private static final String DELETE_CALLBACK = "owner:deletebase:";

    /// Сообщения и клавиатуры.
    private final OwnerMessaging messaging;
    /// Хранилище баз.
    private final BaseRepository baseRepository;

    /// Создаёт фабрику клавиатур баз.
    ///
    /// @param messaging      сообщения и клавиатуры
    /// @param baseRepository хранилище баз
    OwnerBaseButtons(OwnerMessaging messaging, BaseRepository baseRepository) {
        this.messaging = messaging;
        this.baseRepository = baseRepository;
    }

    /// Создаёт кнопки списка баз владельца.
    ///
    /// @param userId идентификатор владельца
    /// @return строки кнопок
    List<List<Button>> baseListButtons(long userId) {
        List<List<Button>> rows = new ArrayList<>();
        for (Base base : baseRepository.findByOwnerId(userId)) {
            messaging.addButton(rows, messaging.button(base.name(), BASE_CALLBACK + base.id()), 2);
        }
        rows.add(messaging.row(messaging.button("Добавить базу", "owner:add")));
        rows.add(messaging.row(messaging.button(OwnerMessaging.BACK_TEXT, "menu:home")));
        return rows;
    }

    /// Создаёт кнопки меню базы.
    ///
    /// @param base выбранная база
    /// @return строки кнопок
    List<List<Button>> baseMenuButtons(Base base) {
        List<List<Button>> rows = new ArrayList<>();
        rows.add(messaging.row(messaging.button("Название", "owner:edit:name"),
                messaging.button("Адрес", "owner:edit:address")));
        rows.add(messaging.row(messaging.button("Часовой пояс", "owner:edit:timezone"),
                messaging.button("График работы", "owner:schedule")));
        rows.add(messaging.row(messaging.button("Залы", "owner:rooms"),
                messaging.button("Срок бронирования", "owner:edit:lead")));
        rows.add(messaging.row(messaging.button("Оборудование", "owner:edit:equipment"),
                messaging.button("Правила отмены", "owner:cancelrules")));
        rows.add(messaging.row(messaging.button(DELETE_BASE_TEXT, DELETE_CALLBACK + base.id())));
        rows.add(messaging.row(messaging.button(OwnerMessaging.BACK_TEXT, "owner:list")));
        return rows;
    }
}

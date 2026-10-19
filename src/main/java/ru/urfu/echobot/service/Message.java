package ru.urfu.echobot.service;

import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// Сообщение, которыми обмениваются боты и сервисы.
///
/// @param text       текст сообщения
/// @param buttonData данные нажатой кнопки (заполняется только во входящих сообщениях)
/// @param keyboard   строки кнопок под сообщением (заполняется только в исходящих)
public record Message(@Nullable String text, @Nullable String buttonData,
                      @NotNull List<List<Button>> keyboard) {

    /// Копирует клавиатуру, чтобы сообщение нельзя было изменить снаружи.
    public Message {
        keyboard = List.copyOf(keyboard);
    }

    /// Создаёт объект билдера для этого класса.
    ///
    /// @return билдер для класса
    public static Builder builder() {
        return new Builder();
    }

    /// Билдер для класса.
    public static final class Builder {
        private String text;
        private String buttonData;
        private List<List<Button>> keyboard;

        /// Конструктор, устанавливающий значения по умолчанию.
        public Builder() {
            text = null;
            buttonData = null;
            keyboard = List.of();
        }

        /// Устанавливает текст будущего сообщения.
        ///
        /// @param text текст сообщения
        /// @return себя же
        public Builder text(@Nullable String text) {
            this.text = text;
            return this;
        }

        /// Устанавливает данные нажатой кнопки.
        ///
        /// @param buttonData данные кнопки
        /// @return себя же
        public Builder buttonData(@Nullable String buttonData) {
            this.buttonData = buttonData;
            return this;
        }

        /// Устанавливает клавиатуру будущего сообщения.
        ///
        /// @param keyboard строки кнопок
        /// @return себя же
        public Builder keyboard(@NotNull List<List<Button>> keyboard) {
            this.keyboard = keyboard;
            return this;
        }

        /// Создаёт объект Message.
        ///
        /// @return созданный объект
        public Message build() {
            return new Message(text, buttonData, keyboard);
        }
    }
}
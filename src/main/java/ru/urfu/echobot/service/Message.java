package ru.urfu.echobot.service;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


public record Message(@Nullable String text, @Nullable String buttonData, @NotNull List<List<Button>> keyboard) {
    public Message {
        keyboard = List.copyOf(keyboard);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private @Nullable String text;
        private @Nullable String buttonData;
        private List<List<Button>> keyboard;

        public Builder() {
            text = null;
            buttonData = null;
            keyboard = List.of();
        }

        public Builder text(@Nullable String text) {
            this.text = text;
            return this;
        }

        public Builder buttonData(@Nullable String buttonData) {
            this.buttonData = buttonData;
            return this;
        }

        public Builder keyboard(@NotNull List<List<Button>> keyboard) {
            this.keyboard = keyboard;
            return this;
        }

        public Message build() {
            return new Message(text, buttonData, keyboard);
        }
    }
}
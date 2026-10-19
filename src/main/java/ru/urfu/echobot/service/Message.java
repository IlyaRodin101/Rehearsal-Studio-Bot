package ru.urfu.echobot.service;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;

public final class Message {
    private final String text;
    private final ReplyKeyboard keyboard;

    public Message(String text, ReplyKeyboard keyboard) {
        this.text = text;
        this.keyboard = keyboard;
    }

    public String text() {
        return text;
    }

    public ReplyKeyboard keyboard() {
        return keyboard;
    }
}

package ru.urfu.echobot.bot;

import org.jetbrains.annotations.NotNull;

public interface Bot {
    void sendMessage(@NotNull msg, long id);
}

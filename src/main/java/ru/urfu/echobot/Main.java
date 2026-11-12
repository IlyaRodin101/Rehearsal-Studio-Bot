package ru.urfu.echobot;

/// Точка входа в приложение.
public final class Main {
    /// Приватный конструктор: объекты этого класса не создаются.
    private Main() {
    }

    /// Запускает Telegram-бота.
    ///
    /// @param args аргументы командной строки, не используются
    public static void main(String[] args) {
        new Application().run();
    }
}

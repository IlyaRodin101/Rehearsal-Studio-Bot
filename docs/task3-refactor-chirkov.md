# Задача 3. Техническое задание на перенос функционала Chirkov в рефакторенную структуру

Исполнитель: Chirkov. Ветка подготовлена заранее: `task3/refactor-Chirkov`, создана от
`task3/refactor` (`7e25f60`). **Сейчас в ветке лежит только этот документ** — код
переносить начинаете вы.

## Роль и границы работы

Реализуете **только** «горячие» слоты, подписки на базы и уведомления подписчикам.

Не меняйте без необходимости функционал Rodin: правила бесплатной/платной отмены,
тексты подтверждения и уведомление владельцу базы. Контракт и карту сохранённого
поведения смотрите в `docs/task3-refactor-Rodin.md`.

Пользовательские сценарии, тексты сообщений и callback-имена описаны в
`docs/task3-chirkov.md` — они остаются обязательными.

## Источник переноса и что НЕ переносить

Источник реализации: `origin/task3/Chirkov2`, коммит `b2b1038`
(`feat: hot slots, base subscriptions and subscriber notifications`).
Ветка `task3/Chirkov` (`6a6f631`) содержит только общую базу Задачи 3.

Следующее **уже есть в `task3/refactor` и в `main` — переносить и переделывать запрещено**:

| Готовый элемент | Где находится |
| --- | --- |
| `Base.freeCancellationHours()`, `penaltyPercent()`, значения по умолчанию 48 и 100 | `model/Base.java` |
| Таблица `subscriptions(user_id, base_id)`, индекс `subscriptions_base` | `repository/SqliteDatabase.java` |
| Удаление подписок при удалении базы | `repository/SqliteBaseDeletion.java` |
| Контракт `SubscriptionRepository` | `repository/SubscriptionRepository.java` |
| `BookingRules.isHotSlot`, `freeCancellationDeadline`, `today`, `canBook` | `service/booking/BookingRules.java` |
| `BookingRepository.findByRoom(long)` | `repository/BookingRepository.java` |

Единственное отличие `b2b1038` от рефакторинга в общей части — косметическое
переименование констант в `SqliteBookingRepository`; игнорируйте его.
При переносе скопируйте строку вызова `callbackRouter.registerHotSlots(...)`, а не
старую схему с `BookingInformationService`, `BookingBackController` и
`BookingSessionRegistry` — этих классов в рефакторинге больше нет.

## Структура после рефакторинга

Новые классы размещайте по тем же правилам, что и существующие:

- `model` — `HotSlot`;
- `repository` — `SqliteSubscriptionRepository` (и `InMemorySubscriptionRepository`
  только если нужен для тестов);
- `service/booking` — всё остальное: сервисы, контроллеры и рассылка уведомлений.

Классы `BookingStep`, `BookingCallback`, `BookingMenuController`,
`BookingMessageProcessingService`, `BookingCancellationService`, `BookingRules`,
`RussianDateFormatter`, `BookingTimeSelectionService`, `BookingKeyboardFactory`
находятся в пакете `ru.urfu.echobot.service.booking`. `BookingStep` и
`BookingCallback` имеют package-private доступ, поэтому `HotSlotController` и
`SubscriptionController` обязательно должны лежать в этом же пакете.
`Button` и `Message` остаются в `ru.urfu.echobot.service` — добавьте импорты.

## Карта файлов переноса

| Было в `b2b1038` | Стало в `task3/refactor-Chirkov` | Действие |
| --- | --- | --- |
| `model/HotSlot.java` | `model/HotSlot.java` | Скопировать как есть (record с `end()`) |
| `repository/SqliteSubscriptionRepository.java` | тот же путь | Скопировать как есть; `SqliteDatabase.open()` для своего пакета доступен |
| `repository/InMemorySubscriptionRepository.java` | тот же путь | Необязательно; только для тестовых конструкторов |
| `service/HotSlotService.java` | `service/booking/HotSlotService.java` | Сменить пакет и импорты; `SLOT_HOURS = 1` оставить |
| `service/HotSlotController.java` | `service/booking/HotSlotController.java` | `BookingFlow` → `BookingStep`; `flow.step = BookingStep.Step.MENU`; остальной код совместим |
| `service/HotSlotNotifier.java` | `service/booking/HotSlotNotifier.java` | Интерфейс, один метод `notifySubscribers` |
| `service/SubscriptionService.java` | `service/booking/SubscriptionService.java` | Сменить только пакет и импорт репозитория |
| `service/SubscriptionController.java` | `service/booking/SubscriptionController.java` | Сменить пакет; использует существующие `BookingMenuController` и `BookingCallback` |
| `service/SubscriptionHotSlotNotifier.java` | `service/booking/SubscriptionHotSlotNotifier.java` | Добавить импорты `Button`, `Message`, `BookingRules`, `RussianDateFormatter`; оставить обработку ошибок рассылки |
| `Application.java` | тот же путь | Передать `new SqliteSubscriptionRepository(database)` в новый конструктор сервиса |
| `RussianDateFormatter.java` | `service/booking/RussianDateFormatter.java` | Добавить методы, не новый класс |
| `BookingFlow.java` | `service/booking/BookingStep.java` | Добавить метод, не новый шаг |
| `BookingCallbackRouter.java` | `service/booking/BookingCallbackRouter.java` | Добавить пространства имён и метод регистрации |
| `BookingMenuController.java` | `service/booking/BookingMenuController.java` | Добавить две кнопки |
| `BookingMessageProcessingService.java` | `service/booking/BookingMessageProcessingService.java` | Добавить конструктор и сборку зависимостей |
| `BookingCancellationService.java` | `service/booking/BookingCancellationService.java` | Добавить вызов рассылки после уведомления владельцу |
| `DummyBot.java` | тот же путь | Добавить получателей, сохранить старые методы |

Никаких новых «классов-оркестраторов» создавать не нужно: composition root уже
существует в `BookingMessageProcessingService`.

## Точки интеграции по шагам

### 1. Модель, хранилище и формат

- Скопируйте `HotSlot`, `SqliteSubscriptionRepository`, при необходимости
  `InMemorySubscriptionRepository`.
- Добавьте в `RussianDateFormatter` методы из `b2b1038`:
  `relativeDate(LocalDate date, LocalDate today)` и
  `timeRange(LocalDateTime start, LocalDateTime end)`. Используйте уже имеющиеся
  `shortDate`, `hour` и `endTime`; константу `HOURS_PER_DAY` дублировать не надо.

### 2. Сервисы

- `HotSlotService`: метод `findHotSlots(Base)` считает слоты от сегодняшнего дня до
  `rules.freeCancellationDeadline(base).toLocalDate()`, проверяйте `rules.canBook`,
  `rules.isHotSlot`, занятость через `bookingRepository.findByRoom(room.id())`
  и сортируйте слоты по времени начала. Метод `isAvailable` обязателен для проверки
  слота в callback.
- `SubscriptionService` и `HotSlotNotifier` — по контрактам из `b2b1038`.
- `SubscriptionHotSlotNotifier`: текст ««Горячий» слот: база «…», зал, сегодня,
  20:00-22:00.», кнопки `[Забронировать]` (`hot:book:<roomId>:<uuuuMMddHHmm>`) и
  `[Пропустить]` (`hot:skip`). Ошибка отправки одному подписчику логируется и не
  прерывает рассылку. Владелец получает сообщение только если он подписан.

### 3. Состояние диалога

В `BookingStep` добавьте ровно один метод из `b2b1038`:

```java
void startHotSlot(long baseId, long roomId, LocalDateTime start) {
    clearBooking();
    this.baseId = baseId;
    this.roomId = roomId;
    bookingDate = start.toLocalDate();
    startHour = start.getHour();
    selectedMonth = YearMonth.from(start);
    selectedWeek = start.toLocalDate();
    step = Step.BOOK_DURATION;
}
```

Новый шаг или событие в `enum Step`/`enum Event` добавлять не надо: используется
уже существующий `BOOK_DURATION`. После вызова `HotSlotController` показывает
`timeSelection.showDurationChoices(chatId, flow, bot)` — база, зал, дата и время
не теряются.

### 4. Контроллеры и маршрутизация

- `BookingMenuController.showMainMenu`: константы `MENU_HOT = "menu:hot"` и
  `MENU_SUB = "menu:sub"`; строка `[Горячие слоты] [Подписка]` добавляется после
  `[О базе]` и перед кнопкой владельца `[Базы]`.
- `BookingCallbackRouter`: константы `HOT = "hot"`, `SUB = "sub"`; package-private
  метод `registerHotSlots(HotSlotController, SubscriptionController)`, который
  регистрирует `namespaces.put(HOT, hotController::handle)`, `namespaces.put(SUB,
  subscriptionController::handle)` и действия `menuActions` для `hot` и `sub`.
  Конструктор маршрутизатора не расширяйте — у него уже 5 параметров.
- `hot:skip` попадает в `default` ветку `HotSlotController.handle` и возвращает
  пользователя в меню — отдельный callback-кейс не нужен.

### 5. Composition root и Application

В `BookingMessageProcessingService` сохраните существующие конструкторы (4 и
5 параметров) как делегирующие и добавьте конструктор с шестым параметром
`SubscriptionRepository`:

```java
BookingMessageProcessingService(BaseRepository, RoomRepository, BookingRepository,
        BookingRules, Set<Long> ownerIds, SubscriptionRepository subscriptions)
```

В нём создайте `HotSlotService`, `SubscriptionService`, `SubscriptionHotSlotNotifier`,
`HotSlotController`, `SubscriptionController`, передайте notifier в
`BookingCancellationService` и вызовите `callbackRouter.registerHotSlots(...)`.
Важно: не добавляйте локальный `RussianDateFormatter` и не передавайте его
в конструкторы — в рефакторинге каждый класс создаёт форматер сам.

В `Application.createService` добавьте импорт `SqliteSubscriptionRepository` и
шестой аргумент `new SqliteSubscriptionRepository(database)`.
`Application` уже имеет 136 непустых строк — лимит 300 соблюдён.

### 6. Отмена брони

У `BookingCancellationService` сейчас есть конструкторы на 2 и 3 параметра, и поле
`BookingRules rules`. Добавьте четвёртый параметр `HotSlotNotifier notifier`,
а старые конструкторы оставьте делегирующими с no-op реализацией интерфейса
(лямбда `(base, room, booking, bot) -> { }`, новый класс не создавайте).

В `cancelBooking` после успешного удаления и после `notifyOwner(booking, free, bot)`
добавьте:

```java
if (rules.isHotSlot(booking.base(), booking.booking().start())) {
    notifier.notifySubscribers(booking.base(), booking.room(), booking.booking(), bot);
}
```

Порядок важен: уведомление владельцу Rodin должно отправляться всегда, даже если
рассылка подписчикам не нужна или не удалась.

### 7. Тестовая инфраструктура

В `DummyBot` добавьте `List<Long> recipients`, `recipient(int index)` и
`messagesTo(long id)`, **не удаляя** `lastMessage()`, `messages()` и `sentCount()` —
ими пользуются существующие тесты. Для одновременных клиентов создавайте отдельный
`DummyBot` на клиента.

## План коммитов

Каждый коммит должен компилироваться и проходить `mvn test` + `mvn checkstyle:check`.

1. `feat(task3): add hot slot model, subscription storage and date helpers` —
   `HotSlot`, `SqliteSubscriptionRepository` (опционально in-memory),
   `RussianDateFormatter.relativeDate/timeRange`, `BookingStep.startHotSlot`,
   `SqliteSubscriptionRepositoryTest`.
2. `feat(task3): add hot slot and subscription services` — `HotSlotService`,
   `SubscriptionService`, `HotSlotNotifier`, `SubscriptionHotSlotNotifier`,
   `HotSlotServiceTest`, `SubscriptionHotSlotNotifierTest`.
3. `feat(task3): add hot slot and subscription dialogs` — `HotSlotController`,
   `SubscriptionController` (в `service/booking`).
4. `feat(task3): wire hot slots into the booking composition root` — меню, router,
   `BookingMessageProcessingService`, `Application`, `DummyBot`, обновление двух
   проверок кнопок меню в `BookingMessageProcessingServiceTest`, `HotSlotFlowTest`,
   `SubscriptionFlowTest`.
5. `feat(task3): notify subscribers after successful cancellation` — вызов в
   `BookingCancellationService` и проверка уведомления после отмены в
   `SubscriptionFlowTest`.

## Тесты

Перенесите и адаптируйте под новые пакеты тесты из `b2b1038`:

| Тест из `b2b1038` | Куда положить | Что проверяет |
| --- | --- | --- |
| `HotSlotServiceTest` | `service/booking/` | слот до срока отмены — горячий; после — нет; занятый и вне графика — нет |
| `HotSlotFlowTest` | `service/booking/` | выбор базы и переход в обычный диалог бронирования без потери базы и зала |
| `SubscriptionFlowTest` | `service/booking/` | переключение подписок кнопками, уведомление только подписчику базы, два одновременных клиента |
| `SqliteSubscriptionRepositoryTest` | `repository/` | повторная подписка не создаёт дубль, отписка удаляет запись, подписки живут после пересоздания репозитория |
| `SubscriptionHotSlotNotifierTest` | `service/booking/` | получатель и кнопки уведомления |

Кроме того, обновите `BookingMessageProcessingServiceTest`: в двух проверках меню
ожидаемый список клиентских кнопок становится
`[Забронировать, Мои записи, О базе, Горячие слоты, Подписка]`.

## Регламент

- Java 25, Maven, без Lombok.
- JavaDoc у классов, полей и методов.
- `static` только для `String` и примитивных констант; константы — `ИМЕНУЮТСЯ_ВОТ_ТАК`.
- Не более 7 параметров конструктора или метода, длина строки не более 120,
  без повторяющихся строковых литералов (`MultipleStringLiterals`).
- Каждый класс — не больше 300 непустых строк. Самый большой класс на базе —
  `OwnerRouter` (236), запас есть.
- `mvn test` и `mvn checkstyle:check` проходят после каждого коммита.

## Договор слияния

- Ветка `task3/refactor-Chirkov` создана от `task3/refactor` (`7e25f60`). Дерево
  этой вершины совпадает с деревом `main` (`1b3bdcf`) — рефакторинг уже влит.
- Сейчас ветка добавляет только `docs/task3-refactor-chirkov.md`; файл не пересекается
  с `docs/task3-refactor-Rodin.md` из ветки `task3/refactor-Rodin`. Обе ветки сливаются
  в `main` и друг с другом без конфликтов.
- Когда код будет дописан, конфликтов быть не должно: ветка Rodin — только аудит и
  не трогает Java-файлы. Если оба изменения окажутся в `BookingCancellationService`,
  сохраняйте **обе** части: `notifyOwner(...)` Rodin и вызов `notifier` Chirkov.
- Перед PR проверьте: `git merge-tree --write-tree main task3/refactor-Chirkov`,
  `mvn test`, `mvn checkstyle:check`.

## Критерии приёмки

- В меню клиента есть `[Горячие слоты]` и `[Подписка]`, callback `menu:hot`, `menu:sub`.
- Список слотов считается по правилам базы и не показывает занятое время.
- Подписка включается/отключается кнопками и сохраняется в SQLite.
- Подписчики получают уведомление только о слотах выбранных баз.
- После отмены брони подписчики получают уведомление, а владелец — своё уведомление Rodin.
- Новые классы не больше 300 строк, тесты и checkstyle проходят, слияние в `main`
  без конфликтов.

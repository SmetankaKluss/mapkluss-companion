package art.mapkluss.companion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class CompanionI18n {
    private static final Map<String, String> EN = new LinkedHashMap<>();
    private static volatile String cachedLanguage;

    static {
        put("Готово", "Ready");
        put("Библиотека MapKluss", "MapKluss Library");
        put("Сохраненные арты, избранное и файлы схем", "Saved arts, favorites and schematic files");
        put("Мои арты", "My arts");
        put("Избранное", "Favorites");
        put("Недавние", "Recent");
        put("Поиск артов", "Search arts");
        put("Поиск коллекций", "Search collections");
        put("Поиск материалов", "Search materials");
        put("Поиск", "Search");
        put("Найти", "Find");
        put("Очистить", "Clear");
        put("Сброс", "Reset");
        put("Обновить", "Refresh");
        put("Синхронизация", "Sync");
        put("Вход", "Login");
        put("Войти", "Login");
        put("Выйти", "Logout");
        put("Выйти из аккаунта MapKluss", "Sign out of MapKluss");
        put("Войти в MapKluss", "Sign in to MapKluss");
        put("Вы вышли. Установленные файлы остались на диске.", "You are signed out. Installed files remain on disk.");
        put("Локальный выход.", "Signed out locally.");
        put("Показан локальный кеш.", "Showing local cache.");
        put("Не удалось завершить удалённую сессию.", "The remote session could not be closed.");
        put("Открыть сайт", "Open site");
        put("Сайт облака", "Cloud site");
        put("Облако", "Cloud");
        put("Коллекции", "Collections");
        put("Скан карты", "Scan map");
        put("Трекер", "Tracker");
        put("Закрыть", "Close");
        put("Позже", "Later");
        put("Telegram", "Telegram");
        put("Скачать мод", "Download mod");
        put("Доступна новая версия", "New version available");
        put(" уже доступен.", " is now available.");
        put("Распознавание карт отменено", "Map identification cancelled");
        put("Откройте инвентарь или хранилище с картами", "Open an inventory or container with maps");
        put("Загружаю и распознаю карты…", "Loading and identifying maps…");
        put("В открытых слотах нет заполненных карт", "There are no filled maps in the open slots");
        put("Не все карты загрузились. Повторите распознавание", "Some maps did not load. Try identification again");
        put("Распознано карт: ", "Maps identified: ");
        put("Сайт", "Site");
        put("Редактор", "Editor");
        put("Назад", "Back");
        put("Пред.", "Prev.");
        put("Арт", "Art");
        put("Арты", "Arts");
        put("Аккаунт", "Account");
        put("Инструменты", "Tools");
        put("Список", "List");
        put("Управление", "Controls");
        put("Открыть", "Open");
        put("Код Lens", "Lens code");
        put("Войти по коду", "Join by code");
        put("Живое превью на настоящих рамках, без изменения мира", "Live preview on real frames without changing the world");
        put("Сессии", "Sessions");
        put("Нет активных сессий", "No active sessions");
        put("Видимость и якорь", "Visibility and anchor");
        put("Личное", "Personal");
        put("Группа", "Group");
        put("рамки", "frames");
        put("Закрепить по левой нижней рамке", "Anchor from left-bottom frame");
        put("Размещения рядом", "Nearby placements");
        put("Размещений пока нет", "No placements yet");
        put("Откройте Lens в редакторе или войдите по коду группы", "Open Lens in the editor or join with a group code");
        put("Выберите личную сессию и закрепите её по рамке", "Select a personal session and anchor it to a frame");
        put("Сначала выберите размещение Lens", "Select a Lens placement first");
        put("Скрыть размещение локально", "Hide this placement locally");
        put("Скрыть все размещения этого автора локально", "Hide all placements from this owner locally");
        put("Отправить жалобу и скрыть размещение", "Report and hide this placement");
        put("Удалить своё размещение Lens", "Delete your Lens placement");
        put("Выйти из группы Lens", "Leave the Lens group");
        put("Личную сессию закрывают в редакторе", "Close personal sessions in the editor");
        put("Блок автора", "Block owner");
        put("Скрыть автора", "Hide owner");
        put("Жалоба", "Report");
        put("Скрыть", "Hide");
        put("Lens готов", "Lens ready");
        put("Загрузка сессий Lens...", "Loading Lens sessions...");
        put("Сессии Lens обновлены", "Lens sessions updated");
        put("Lens отключён сервером MapKluss", "Lens is disabled by MapKluss");
        put("Не удалось загрузить сессии Lens", "Could not load Lens sessions");
        put("Введите код группы Lens", "Enter a Lens group code");
        put("Вход в группу Lens...", "Joining Lens group...");
        put("Группа подключена", "Joined group");
        put("Не удалось войти в группу Lens", "Could not join Lens group");
        put("Вы вышли из группы Lens", "Left Lens group");
        put("Не удалось выйти из группы Lens", "Could not leave Lens group");
        put("Сначала выберите сессию Lens", "Select a Lens session first");
        put("В одиночной игре размещение Lens может быть только личным", "Singleplayer Lens placements must be personal");
        put("Создание размещения Lens...", "Creating Lens placement...");
        put("Lens закреплён", "Lens anchored");
        put("Не удалось создать размещение Lens", "Could not create Lens placement");
        put("Размещение Lens удалено", "Lens placement removed");
        put("Не удалось удалить размещение Lens", "Could not remove Lens placement");
        put("Размещение Lens скрыто локально", "Lens placement hidden locally");
        put("Не удалось сохранить настройки Lens", "Could not save Lens preferences");
        put("Автор Lens заблокирован локально", "Lens owner blocked locally");
        put("Жалоба отправлена, размещение скрыто", "Report sent and placement hidden");
        put("Размещение скрыто, но жалобу отправить не удалось", "Placement hidden, but report failed");
        put("Не удалось обновить Lens", "Could not refresh Lens");
        put("Ревизия Lens", "Lens revision");
        put("Не удалось загрузить настройки Lens", "Could not load Lens preferences");
        put("Войдите в MapKluss, чтобы использовать Lens", "Sign in to MapKluss to use Lens");

        put("Арт MapKluss", "MapKluss Art");
        put("Название арта", "Art title");
        put("Сохранить", "Save");
        put("Удалить", "Delete");
        put("Удалить коллекцию", "Delete collection");
        put("Точно?", "Sure?");
        put("+ Целиком", "+ Whole");
        put("+ По картам", "+ Per map");
        put("- Схема", "- Schematic");
        put("Схема", "Schematic");
        put("Схемы", "Schematics");
        put("Сайт арта", "Art page");
        put("Файлы", "Files");
        put("Папка файлов", "Files folder");
        put("Папка схем", "Schematics folder");
        put("PNG превью", "PNG preview");
        put("Материалы", "Materials");
        put("Команды", "Commands");
        put("Датапак", "Datapack");
        put("Скачать MapDat", "Download MapDat");
        put("Скачать проект", "Download project");
        put("Импорт MapDat", "Import MapDat");
        put("Библиотека", "Library");
        put("Архивы", "Archives");
        put("Переходы", "Links");
        put("Ссылки", "Links");
        put("Ещё", "More");
        put("Экспорт", "Export");
        put("приватный", "private");
        put("по ссылке", "unlisted");
        put("публичный", "public");
        put("доступ ?", "privacy ?");
        put("избранное", "favorite");
        put("не избранное", "not favorite");
        put("коллекций", "collections");
        put("файлов", "files");
        put("схема не установлена", "schematic not installed");
        put("схема установлена", "schematic installed");
        put("Убрать", "Remove");
        put("В избранное", "Favorite");

        put("Вход MapKluss", "MapKluss Login");
        put("Связь Minecraft с аккаунтом на сайте", "Connect Minecraft with your website account");
        put("Получить код", "Get code");
        put("Нажмите Получить код, чтобы начать вход.", "Get a code to start sign-in.");
        put("Создаю код входа...", "Creating a login code...");
        put("Код готов. Откройте сайт и подтвердите вход.", "Code ready. Open the website and approve sign-in.");
        put("Жду подтверждение на сайте...", "Waiting for approval on the website...");
        put("Проверяю подтверждение...", "Checking approval...");
        put("Автопроверка включена.", "Automatic checks enabled.");
        put("Автопроверка выключена.", "Automatic checks disabled.");
        put("Код истек. Нажмите Получить код еще раз.", "The code expired. Get a new code.");
        put("Вход подтвержден. Возвращаю в библиотеку...", "Sign-in approved. Returning to the library...");
        put("Авто: вкл", "Auto: on");
        put("Авто: выкл", "Auto: off");
        put("Проверить", "Check");
        put("Копировать", "Copy");
        put("Копировать код", "Copy code");
        put("Проверка", "Check");
        put("Код", "Code");
        put("Код входа", "Login code");
        put("Статус", "Status");
        put("Ожидание подтверждения", "Waiting for approval");
        put("Вход отклонён", "Sign-in denied");
        put("Код истёк", "Code expired");
        put("Истекает через", "Expires in");
        put("Проверка каждые", "Checks every");
        put("автоматически", "automatically");
        put("вручную", "manually");
        put("Код скопирован", "Code copied");
        put("Вход подтвержден", "Sign-in approved");
        put("Сначала нажмите «Получить код»", "Click \"Get code\" first");

        put("Коллекции MapKluss", "MapKluss Collections");
        put("Коллекции арта", "Art collections");
        put("Название коллекции", "Collection name");
        put("Новая коллекция", "New collection");
        put("Создать", "Create");
        put("Создать +", "Create +");
        put("Сайт коллекции", "Collection page");
        put("Открыть коллекцию", "Open collection");
        put("Группы сохраненных артов для мода и сайта", "Saved art groups for the mod and website");

        put("Скан MapKluss", "MapKluss Scan");
        put("Название скана", "Scan title");
        put("ОК", "OK");
        put("Применить", "Apply");
        put("Применить название скана", "Apply the scan title");
        put("Рука", "Hand");
        put("Из руки", "From hand");
        put("Рамка", "Frame");
        put("Одна рамка", "One frame");
        put("Стена", "Wall");
        put("Вся стена", "Whole wall");
        put("Вручную", "Manual");
        put("По углам", "By corners");
        put("Угол A", "Corner A");
        put("Угол B", "Corner B");
        put("В облако", "To cloud");
        put("Проверить импорт", "Check import");
        put("Загрузить", "Load");
        put("След.", "Next");
        put("Папка", "Folder");
        put("Состояние скана", "Scan state");
        put("Копирование карт из руки, рамки или стены", "Copy maps from hand, frame or wall");
        put("Скан", "Scan");
        put("Результат", "Result");
        put("История", "History");
        put("Сканировать карту в руке", "Scan the map in your hand");
        put("Сканировать рамку под прицелом", "Scan the item frame under the crosshair");
        put("Найти всю стену карт автоматически", "Find the complete map wall automatically");
        put("Сканировать область между углами A и B", "Scan the area between corners A and B");
        put("Запомнить первый угол стены", "Capture the first wall corner");
        put("Запомнить второй угол стены", "Capture the second wall corner");
        put("Сохранить PNG скана", "Save the scan as PNG");
        put("Загрузить скан в облако", "Upload the scan to Cloud");
        put("Проверить состояние импорта", "Check the import status");
        put("Предыдущий скан", "Previous scan");
        put("Загрузить выбранный скан", "Load the selected scan");
        put("Следующий скан", "Next scan");
        put("Удалить скан из локальной истории", "Delete the scan from local history");
        put("Открыть папку скана", "Open the scan folder");

        put("Трекер сборки MapKluss", "MapKluss Build Tracker");
        put("Трекер MapKluss", "MapKluss Tracker");
        put("ID сессии", "Session ID");
        put("UUID сборки", "Build UUID");
        put("Вставьте UUID сборки из MapKluss.", "Paste a MapKluss build UUID.");
        put("Сначала вставьте UUID трекера.", "Paste a tracker UUID first.");
        put("Повторить", "Retry");
        put("Изменить UUID", "Change UUID");
        put("Сессия не найдена", "Session not found");
        put("Проверьте UUID и повторите", "Check the UUID and try again");
        put("Синхронизированный прогресс материалов", "Synced material progress");
        put("Недавние сессии трекера", "Recent tracker sessions");
        put("Открыть связанный арт", "Open the linked art");
        put("Сессия", "Session");
        put("Сбор", "Gathering");
        put("Стройка", "Building");
        put("Отмена", "Undo");
        put("Количество", "Amount");
        put("Все", "All");
        put("Прогресс", "Progress");
        put("Материал", "Material");
        put("Всего", "Total");
        put("Свое", "Custom");
        put("Показ.", "Show");
        put("Скрыть", "Hide");
        put("Материалы и синхронизация прогресса", "Materials and progress sync");

        put("Сессия: вход не выполнен", "Session: signed out");
        put("Сессия: недоступна", "Session: unavailable");
        put("Сначала войдите через код входа.", "Sign in with a device code first.");
        put("Список пуст", "The list is empty");
        put("Недавних артов пока нет", "No recent arts yet");
        put("Сохрани арт на сайте или обнови библиотеку", "Save an art on the website or refresh the library");
        put("Войди через код, чтобы открыть облако", "Sign in with a code to open the cloud");
        put("Коллекций пока нет", "No collections yet");
        put("Создай первую коллекцию или добавь арт позже", "Create your first collection or add an art later");
        put("В коллекции пока нет артов", "This collection has no arts yet");
        put("Добавь арт через экран арта или сайт", "Add an art from the art screen or the website");
        put("Создай коллекцию и добавь в неё этот арт", "Create a collection and add this art to it");
        put("Недавних сессий пока нет", "No recent sessions yet");
        put("Открой трекер из арта или вставь UUID сборки", "Open the tracker from an art or paste a build UUID");
        put("Нажмите «Получить код»", "Click \"Get code\"");
        put("Затем подтвердите вход на mapkluss.art/device", "Then approve the login on mapkluss.art/device");
        put("Превью недоступно", "Preview unavailable");
        put("Загрузка превью", "Loading preview");
        put("Изображение появится здесь", "The image will appear here");
        put("Откройте арт на сайте или обновите файлы", "Open the art on the website or refresh files");
        put("Установить полную схему", "Install the complete schematic");
        put("Установить схемы по картам", "Install per-map schematics");
        put("Удалить установленную схему", "Remove the installed schematic");

        put("Авторамки", "AutoFrame");
        put("Карты арта в инвентаре и рамки на стене", "Art maps in inventory and frames on the wall");
        put("Шаблоны", "Templates");
        put("Шаблонов пока нет", "No templates yet");
        put("Остановить", "Stop");
        put("В рамках", "In frames");
        put("В инвентаре", "In inventory");
        put("AutoFrame готов", "AutoFrame ready");
        put("AutoFrame подготовлен: ", "AutoFrame prepared: ");
        put("Не удалось прочитать шаблоны AutoFrame", "Could not read AutoFrame templates");
        put("Выбран арт: ", "Selected art: ");
        put("Размещение AutoFrame остановлено", "AutoFrame placement stopped");
        put("Дождитесь завершения установки карты", "Wait for the current map placement");
        put("AutoFrame закреплён по левой нижней рамке", "AutoFrame anchored at the left-bottom frame");
        put("Эта рамка уже заполнена правильной картой", "This frame already has the correct map");
        put("Рамка занята другим предметом", "This frame is occupied by another item");
        put("Нужной части арта нет в инвентаре", "The required art tile is not in your inventory");
        put("Ставлю часть ", "Placing tile ");
        put("AutoFrame закреплён. Теперь нажимайте ПКМ по пустым рамкам", "AutoFrame anchored. Right-click empty frames");
        put("Сервер не подтвердил установку карты", "The server did not confirm the map placement");
        put("Рамка больше недоступна", "The frame is no longer available");
        put("Установлена часть ", "Placed tile ");
        put("ПКМ по пустой рамке", "right-click an empty frame");
        put("установка...", "placing...");
        put("Для AutoFrame нужен архив MAP.DAT.", "AutoFrame requires a MAP.DAT archive.");
        put("Подготовка AutoFrame...", "Preparing AutoFrame...");
        put("Ошибка AutoFrame: ", "AutoFrame error: ");
        put("AutoFrame готов: ", "AutoFrame ready: ");
        put("Для рамок", "For frames");
        put("Откройте мир и смотрите на левую нижнюю рамку", "Open a world and look at the left-bottom frame");
        put("Смотрите на левую нижнюю рамку и нажмите клавишу AutoFrame", "Look at the left-bottom frame and press the AutoFrame key");
        put("AutoFrame закреплён. Нажимайте ПКМ по рамкам в любом порядке", "AutoFrame anchored. Right-click frames in any order");
        put("В инвентаре или сундуке нет загруженных карт", "No loaded maps were found in the inventory or chest");
        put("Возьмите в основную руку часть нужного арта", "Hold a tile of the required art in your main hand");
        put("Подождите, пока изображение карты в руке загрузится", "Wait for the held map image to load");
        put("Эта карта подходит к нескольким артам. Оставьте в инвентаре карты только одного арта", "This map matches several arts. Keep only one art's maps in the inventory");
        put("Прогрузка карт остановлена: содержимое инвентаря изменилось", "Map loading stopped because the inventory changed");
        put("Не удалось прогрузить изображение одной из карт", "Could not load one map preview");
        put("Не удалось восстановить сетку карт", "Could not reconstruct the map grid");
        put("Не удалось надёжно восстановить сетку. Оставьте вместе только карты одного арта", "The grid could not be reconstructed reliably. Keep only one art's maps together");
        put("Сетка распознана: ", "Grid recognized: ");
        put("Не удалось сохранить локальный шаблон карт", "Could not save the local map template");
        put("Локальный арт ", "Local art ");
        put("Рамка была заполнена, установка отменена", "The frame was filled; placement cancelled");
        put("Не удалось подготовить AutoFrame.", "Could not prepare AutoFrame.");

        put("Two-layer", "Two-layer");
        put("ВЫБЕРИТЕ КАРТУ", "SELECT A MAP");
        put("Строка ", "Row ");
        put(", столбец ", ", column ");
        put(" карт", " maps");
        put("Подготовка карты ", "Preparing map ");
        put("Подготовка схемы…", "Preparing schematic…");
        put("В архиве нет карт Two-layer.", "This archive contains no Two-layer maps.");
        put("Не удалось подготовить карту: ", "Could not prepare the map: ");
        put("Не удалось подготовить план: ", "Could not prepare the plan: ");
        put("Импорт Two-layer", "Import Two-layer");
        put("Из облака", "From Cloud");
        put("Выбрать ZIP", "Choose ZIP");
        put("Импорт ZIP", "Import ZIP");
        put("Импорт ZIP MapKluss", "Import MapKluss ZIP");
        put("Выберите источник", "Choose a source");
        put("Облачный план", "Cloud plan");
        put("Требуется арт с Two-layer файлами", "An art with Two-layer files is required");
        put("Подтвердить выход", "Confirm logout");
        put("Подтвердить удаление", "Confirm delete");
        put("Нажмите ещё раз для подтверждения", "Press again to confirm");
        put("Подготовьте незаблокированную карту масштаба 0.", "Prepare an unlocked scale-0 map.");
        put("Загрузка плана…", "Loading plan…");
        put("Выберите ZIP из MapKluss.", "Choose a MapKluss ZIP.");
        put("Выбор отменён.", "Selection cancelled.");
        put("Не удалось загрузить план: ", "Could not load plan: ");
        put("План повреждён или не поддерживается", "The plan is damaged or unsupported");
        put("Не удалось запустить: ", "Could not start: ");
        put("Не удалось начать: ", "Could not begin: ");
        put("Сначала войдите в MapKluss", "Sign in to MapKluss first");
        put("Экспериментальный режим для одного 128x128 арта. Мод ничего не ломает и не ставит автоматически.",
            "Experimental mode for one 128x128 art. The mod never breaks or places blocks automatically.");
        put("Пошаговая постройка арта 128×128.", "Guided 128×128 map-art building.");
        put("Мод показывает этапы и подсветку. Блоки меняет игрок.",
            "The mod shows each step and highlight. You change the blocks.");
        put("Облако · версия ", "Cloud · version ");
        put("Облако недоступно · выберите ZIP", "Cloud unavailable · choose a ZIP");
        put("Cloud-план привязан к версии ", "The Cloud plan is pinned to version ");
        put("Cloud-план недоступен; можно импортировать локальный ZIP.",
            "The Cloud plan is unavailable; you can import a local ZIP.");
        put("Текущий шаг", "Current step");
        put("Дальше", "Next");
        put("Завершить", "Finish");
        put("Схема построена", "Schematic built");
        put("Задать опору · J", "Set anchor · J");
        put("Подтвердить · J", "Confirm · J");
        put("Продолжить", "Continue");
        put("Остановить", "Stop");
        put("Подтвердить", "Confirm");
        put("Прогресс будет удалён. Нажмите ещё раз.", "Progress will be deleted. Press again.");
        put("Постройка остановлена.", "Build stopped.");
        put("Не удалось остановить Two-layer: ", "Could not stop Two-layer: ");
        put("Сначала остановите текущую стройку Two-layer — её прогресс не был заменён",
            "Stop the current Two-layer build first — its progress was not replaced");
        put("Наведитесь на якорный блок под северо-западным краем арта и нажмите J.",
            "Aim at the anchor block below the art's north-west edge and press J.");
        put("Запоминать позицию Two-layer можно только находясь в Overworld",
            "The Two-layer position can only be captured in the Overworld");
        put("Нет активной сессии Two-layer.", "There is no active Two-layer session.");
        put("Ошибка Two-layer: ", "Two-layer error: ");
        put("Постройте оба слоя · J когда готово", "Build both layers · press J when ready");
        put("Постройте оба слоя · J: готово", "Build both layers · J: ready");
        put("Готово · заблокируйте карту панелью", "Complete · lock the map with a glass pane");
        put("Готово · заблокируйте карту", "Complete · lock the map");
        put("Этап ", "Stage ");
        put("Осталось снять: ", "Blocks left to remove: ");
        put("Осталось: ", "Left: ");
        put("Точка: ", "Point: ");
        put("Точка ", "Point ");
        put("Сессия Two-layer восстановлена локально.", "Two-layer session restored locally.");
        put("Сохранённая сессия Two-layer повреждена и не загружена.",
            "The saved Two-layer session is damaged and was not loaded.");
        put("Наведитесь на точный северо-западный блок", "Aim at the exact north-west block");
        put("Опора изменилась. Наведитесь снова и нажмите J.", "The anchor changed. Aim again and press J.");
        put("Часть этапа вне загруженных чанков.", "Part of this stage is outside loaded chunks.");
        put("Вы на точке. Теперь возьмите привязанную карту в любую руку.",
            "You are on the point. Now equip the bound map in either hand.");
        put("На точке возьмите привязанную незаблокированную карту масштаба 0.",
            "Equip the bound unlocked scale-0 map while standing on the point.");
        put("Не сходите с точки и держите карту до завершения двух циклов обновления.",
            "Stay on the point and hold the map for two full update cycles.");
        put("Точка записана. Уберите карту из обеих рук, прежде чем двигаться.",
            "Point captured. Stow the map from both hands before moving.");
        put("Карта убрана. Идите к следующей точке.", "Map stowed. Move to the next point.");
        put("Карта убрана. Проверяю её байты без повторного обновления...",
            "Map stowed. Checking its bytes without another update...");
        put("Вернитесь на точку с убранной картой.", "Return to the point with the map stowed.");
        put("Вы сошли с точки с картой. Немедленно уберите её и вернитесь на точку.",
            "You left the point with the map equipped. Stow it immediately and return to the point.");
        put("Карта убрана слишком рано. Оставайтесь на точке и возьмите её снова.",
            "The map was stowed too early. Stay on the point and equip it again.");
        put("Уберите привязанную карту из обеих рук: вне точки она может записать неверные пиксели.",
            "Stow the bound map from both hands: away from the point it can record incorrect pixels.");
        put("Уберите привязанную карту из обеих рук, прежде чем сходить с точки.",
            "Stow the bound map from both hands before leaving the point.");
        put("Перед записью уберите привязанную карту из обеих рук.",
            "Stow the bound map from both hands before capture.");
        put("На точке возьмите привязанную карту", "Equip the bound map on the point");
        put("Уберите карту из обеих рук", "Stow the map from both hands");
        put("возьмите карту на точке", "equip map on point");
        put("уберите карту", "stow map");
        put("Привязанная карта не найдена в инвентаре", "The bound map was not found in the inventory");
        put("Привязанная карта не найдена", "The bound map was not found");
        put("Уберите копию привязанной карты из второй руки", "Remove the duplicate bound map from the offhand");
        put("Проверяю байты карты...", "Checking map bytes...");
        put("Карта всё ещё отличается. Нажмите J повторно для ручного продолжения.",
            "The map still differs. Press J again to continue manually.");
        put("Карта не совпала. Подождите или нажмите J для ручного предупреждения.",
            "The map does not match. Wait, or press J to arm a manual override.");
        put("Начальная запись не совпала в ", "Initial capture differs in ");
        put(" замороженных пикселях; базовый цвет ", " frozen pixels; base colour ");
        put("; базовый цвет ", "; base colour ");
        put(", оттенок ", ", shade ");
        put(". Нажмите J, чтобы повторить запись из центра.",
            ". Press J to repeat the capture from the centre.");
        put("Повреждены замороженные пиксели: ", "Frozen pixels are damaged: ");
        put(". Они уже не восстановятся на следующих этапах — начните заново с чистой картой.",
            ". Later stages cannot restore them — restart with a clean map.");
        put("Не совпали уже обработанные пиксели: ", "Already processed pixels differ: ");
        put(". J ещё раз — продолжить вручную.", ". Press J again to continue manually.");
        put(". Проверьте снятые блоки; J — открыть ручное продолжение.",
            ". Check the removed blocks; press J to arm manual continuation.");
        put("Повторяем начальную запись. Уберите карту и вернитесь на подсвеченный центр.",
            "Repeating initial capture. Stow the map and return to the highlighted centre.");
        put("Ручное продолжение запрещено: повреждены ",
            "Manual continuation is blocked: damaged frozen pixels: ");
        put(" замороженных пикселей. Нужны чистая карта и новая сессия.",
            " frozen pixels. A clean map and a new session are required.");
        put("ВНИМАНИЕ: не совпали ", "WARNING: mismatching ");
        put(" уже обработанных пикселей. Нажмите J ещё раз только для ручного продолжения.",
            " already processed pixels. Press J again only to continue manually.");
        put("Продолжено вручную; расхождение уже обработанных пикселей принято локально.",
            "Continued manually; the processed-pixel mismatch was accepted locally.");
        put("Начальная карта подтверждена. Снимите подсвеченные блоки.",
            "Initial map confirmed. Remove the highlighted blocks.");
        put("Ломайте только блоки с живой красной подсветкой MapKluss: она исчезает сразу после правильного блока.",
            "Break only blocks with MapKluss's live red highlight: it disappears as soon as the correct block is gone.");
        put("Итоговая карта совпала. Нажмите J для завершения.", "Final map matches. Press J to finish.");
        put("Этап подтверждён. Нажмите J: Дальше.", "Stage confirmed. Press J: Next.");
        put("ВНИМАНИЕ: карта не совпала. Проверьте позицию и блоки. Нажмите J ещё раз только для ручного продолжения.",
            "WARNING: the map does not match. Check your position and blocks. Press J again only to continue manually.");
        put("Продолжено вручную; расхождение записано только в локальной сессии.",
            "Continued manually; the mismatch is recorded only in the local session.");
        put("Следующий этап: снимите подсвеченные блоки.", "Next stage: remove the highlighted blocks.");
        put("Не удалось сохранить прогресс Two-layer.", "Could not save Two-layer progress.");
        put("Следующий этап: ломайте только блоки с живой красной подсветкой MapKluss.",
            "Next stage: break only blocks with MapKluss's live red highlight.");
        put("План создан для Minecraft ", "The plan targets Minecraft ");
        put(", запущена ", ", running ");
        put("Наведитесь на NW-якорь · J: запомнить", "Aim at the NW anchor · J: capture");
        put("J: подтвердить якорь", "J: confirm anchor");
        put("Постройте схему · J: готово", "Build the schematic · J: ready");
        put("J: завершить", "J: finish");
        put("J: дальше", "J: next");
        put("J: ручное продолжение", "J: continue manually");
        put("J: повторить начальную запись", "J: repeat initial capture");
        put("Ожидание карты · J: ручная проверка", "Waiting for map · J: manual check");
        put("Следуйте подсветке Two-layer", "Follow the Two-layer highlights");
        put("снять блоки", "remove blocks");
        put("идите к точке", "move to point");
        put("стойте на точке", "stay on point");
        put("проверка карты", "checking map");
        put("данные карты ✓", "map data ✓");
        put("данные карты …", "map data …");
        put("готово к следующему", "ready for next");
        put("локальный план", "local plan");
        put("Возьмите заполненную карту", "Hold a filled map");
        put("Данные карты ещё не загружены", "Map data is not loaded yet");
        put("Это другая карта", "This is a different map");
        put("Нужна карта масштаба 0", "A scale-0 map is required");
        put("Карта уже заблокирована", "The map is already locked");
        put("Two-layer работает только в Overworld", "Two-layer works only in the Overworld");
        put("Запустите Two-layer внутри загруженного мира", "Start Two-layer inside a loaded world");
        put("Не удалось определить текущий сервер или одиночный мир",
            "Could not identify the current server or singleplayer world");
        put("Неверный размер данных карты", "Invalid map data size");
        put("Выбранный блок не лежит на северо-западной границе карты масштаба 0. Координаты X и Z должны давать остаток 64 при делении на 128",
            "The selected block is not on the north-west boundary of a scale-0 map. X and Z must both have remainder 64 modulo 128");
        put("Старый этап использовал небезопасный перенос карты. Запустите Two-layer заново с исходной схемой и чистой картой.",
            "The legacy stage used unsafe map travel. Restart Two-layer with the original schematic and a clean map.");
        put("Мир не загружен", "The world is not loaded");
        put("Постройте оба слоя, затем нажмите J.", "Build both layers, then press J.");
        put("Нижняя точка всей схемы начинается над платформой. Постройте оба слоя, затем нажмите J.",
            "The lowest point of the whole schematic starts above the platform. Build both layers, then press J.");
        put("Постройте только двухслойный арт по схеме, затем нажмите J.",
            "Build only the two-layer art from the schematic, then press J.");
        put("Размещение схемы исправлено: её нижняя точка находится над платформой.",
            "The schematic placement was corrected: its lowest point is above the platform.");
        put("Не удалось обновить размещение схемы; запустите Two-layer заново.",
            "The schematic placement could not be updated; start Two-layer again.");
        put("Two-layer завершён. Проверьте итог и заблокируйте карту стеклянной панелью.",
            "Two-layer is complete. Check the result and lock the map with a glass pane.");
        put("Two-layer завершён. Заблокируйте карту стеклянной панелью.",
            "Two-layer is complete. Lock the map with a glass pane.");
        put("Опора ", "Anchor ");
        put(". Не двигайте прицел и нажмите J ещё раз для подтверждения.",
            ". Keep aiming at it and press J again to confirm.");
        put("Litematica не найдена — импортируйте схему вручную из папки schematics.",
            "Litematica was not found — import the schematic manually from the schematics folder.");
        put("Litematica не смогла прочитать схему — импортируйте её вручную.",
            "Litematica could not read the schematic — import it manually.");
        put("Существующее размещение Two-layer выбрано в Litematica.",
            "The existing Two-layer placement is selected in Litematica.");
        put("Схема Two-layer установлена и выбрана в Litematica.",
            "The Two-layer schematic is placed and selected in Litematica.");
        put("Схема удаления текущего этапа выбрана в Litematica.",
            "The current phase removal schematic is selected in Litematica.");
        put("Не удалось включить схему удаления; встроенная подсветка MapKluss продолжает работать.",
            "The removal schematic could not be enabled; MapKluss's built-in highlight remains active.");
        put("Автоматическое размещение Litematica недоступно — импортируйте сохранённую схему вручную.",
            "Automatic Litematica placement is unavailable — import the saved schematic manually.");
        put("Размещение MapKluss было перемещено — импортируйте схему вручную.",
            "The MapKluss placement was moved — import the schematic manually.");
        put("Litematica не подтвердила безопасную замену схемы — используйте живую подсветку MapKluss.",
            "Litematica could not confirm a safe schematic replacement — use MapKluss's live highlight.");
        put("Опорная схема текущего этапа выбрана в Litematica.",
            "The current stage reference schematic is selected in Litematica.");
        put("Опорная схема обновлена: снятые блоки скрыты, остальная конструкция видна.",
            "The reference schematic was updated: removed blocks are hidden and the remaining structure is visible.");
        put("Опорная схема текущего этапа восстановлена.",
            "The current stage reference schematic was restored.");
        put("Не удалось включить опорную схему; используйте живую подсветку MapKluss.",
            "The reference schematic could not be enabled; use MapKluss's live highlight.");
        put("Опорную схему этапа создать не удалось; используйте живую подсветку MapKluss.",
            "The stage reference schematic could not be created; use MapKluss's live highlight.");
        put("Этот старый Two-layer план нельзя запускать. Экспортируйте новый ZIP версии 3 на сайте",
            "This legacy Two-layer plan cannot be started. Export a new version 3 ZIP from the website");
        put("Этот старый Two-layer план содержит прежнее полотно или разметку. Экспортируйте новый ZIP версии 3 на сайте",
            "This legacy Two-layer plan contains the old canvas or markers. Export a new version 3 ZIP from the website");
        put("граница до SE ", "boundary to SE ");
        put(" подсвечена. Не двигайте прицел и нажмите J ещё раз.",
            " is highlighted. Keep aiming and press J again.");

        put("Выберите северо-западную опору и нажмите J.", "Aim at the north-west anchor and press J.");
        put("Выберите северо-западную опору · J", "Aim at the north-west anchor · J");
        put("J: подтвердить опору", "J: confirm anchor");
        put("Вернитесь в исходный мир.", "Return to the original world.");
        put("Сессия Two-layer восстановлена.", "Two-layer session restored.");
        put("Опора изменилась. Выберите её снова.", "The anchor changed. Select it again.");
        put("Блоки сняты. Перейдите к точке.", "Blocks removed. Move to the point.");
        put("Встаньте на подсвеченную точку.", "Stand on the highlighted point.");
        put("Возьмите выбранную карту.", "Hold the selected map.");
        put("Остановитесь на точке.", "Stand still on the point.");
        put("Запись карты…", "Capturing map…");
        put("Вернитесь на подсвеченную точку.", "Return to the highlighted point.");
        put("Ожидание обновления карты…", "Waiting for the map update…");
        put("Точка записана. Перейдите к следующей.", "Point captured. Move to the next one.");
        put("Проверка карты…", "Checking map…");
        put("Снимите подсвеченные блоки.", "Remove the highlighted blocks.");
        put("Карта готова · J: завершить", "Map ready · J: finish");
        put("Этап готов · J: дальше", "Stage ready · J: next");
        put("Повторите запись из центра.", "Repeat the capture from the centre.");
        put("Нельзя продолжить: повреждены пиксели прошлых этапов.",
            "Cannot continue: pixels from earlier stages are damaged.");
        put("Переход выполнен с расхождением.", "Continued with a mismatch.");
        put("Готово. Проверьте и заблокируйте карту.", "Complete. Check and lock the map.");
        put("Не удалось обновить схему этапа. Используйте подсветку MapKluss.",
            "Could not update the stage schematic. Use the MapKluss highlight.");
        put("Схема этапа восстановлена.", "Stage schematic restored.");
        put("Схема восстановлена.", "Schematic restored.");
        put("Litematica недоступна. Импортируйте схему вручную.",
            "Litematica is unavailable. Import the schematic manually.");
        put("Не удалось открыть схему в Litematica. Импортируйте её вручную.",
            "Could not open the schematic in Litematica. Import it manually.");
        put("Размещение перемещено. Импортируйте схему вручную.",
            "The placement was moved. Import the schematic manually.");
        put("Не удалось разместить схему. Импортируйте её вручную.",
            "Could not place the schematic. Import it manually.");
        put("Размещение Litematica выбрано.", "Litematica placement selected.");
        put("Схема загружена в Litematica.", "Schematic loaded in Litematica.");
        put("Схема этапа выбрана в Litematica.", "Stage schematic selected in Litematica.");
        put("Схема этапа обновлена.", "Stage schematic updated.");
        put("Возьмите нужную карту в руку", "Hold the map you want to use");
        put("Подготовьте незаблокированную карту масштаба 0", "Prepare an unlocked scale-0 map");
        put("Выбранная карта не найдена", "The selected map was not found");
        put("Перейдите к точке", "Move to the point");
        put("Возьмите выбранную карту", "Hold the selected map");
        put("Не двигайтесь", "Stay still");
        put("Перейдите к следующей точке", "Move to the next point");
        put("J: продолжить с расхождением", "J: continue with mismatch");
        put("Пауза", "Paused");
        put("Следуйте подсветке", "Follow the highlight");
        put("снимите блоки", "remove blocks");
        put("перейдите к точке", "move to point");
        put("возьмите карту", "hold map");
        put("запись карты", "capturing map");
        put("следующая точка", "next point");
        put("этап готов", "stage ready");
        put("пауза", "paused");
        put("Карта не совпадает: ", "Map mismatch: ");
        put(" пикс. J: повторить", " pixels. J: retry");
        put("Повреждены пиксели прошлых этапов: ", "Pixels from earlier stages are damaged: ");
        put(". Начните заново.", ". Restart.");
        put("Пиксели этапа не совпадают: ", "Stage mismatch: ");
        put(". Проверьте блоки.", ". Check the blocks.");
        put("Расхождение: ", "Mismatch: ");
        put(" пикс. J: подтвердить", " pixels. J: confirm");
        put(" пикс. Нажмите J ещё раз.", " pixels. Press J again.");
    }

    static {
        put("Закрепить по угловой рамке", "Anchor from a corner frame");
        put("Откройте мир и смотрите на угловую рамку арта", "Open a world and look at a corner frame of the art");
        put("Смотрите на угловую рамку арта и нажмите клавишу AutoFrame", "Look at a corner frame of the art and press the AutoFrame key");
    }

    private CompanionI18n() {
    }

    static boolean english(MinecraftClient client) {
        return "en".equals(language(client));
    }

    private static String language(MinecraftClient client) {
        String cached = cachedLanguage;
        if (cached != null) return cached;
        try {
            cachedLanguage = CompanionConfig.load(client.runDirectory.toPath()).language();
        } catch (Exception ignored) {
            cachedLanguage = CompanionConfig.DEFAULT_LANGUAGE;
        }
        return cachedLanguage;
    }

    static String toggleLabel(MinecraftClient client) {
        return english(client) ? "RU" : "EN";
    }

    static void toggle(MinecraftClient client) throws IOException {
        CompanionConfig config = CompanionConfig.load(client.runDirectory.toPath());
        String next = "en".equals(config.language()) ? "ru" : "en";
        new CompanionConfig(config.supabaseUrl(), config.supabaseAnonKey(), config.siteUrl(), next, config.gatewayUrl())
            .saveForRunDir(client.runDirectory.toPath());
        cachedLanguage = next;
    }

    static Text text(String value) {
        return Text.literal(translate(value));
    }

    static String translate(String value) {
        return translate(value, english(MinecraftClient.getInstance()));
    }

    static String translate(String value, boolean english) {
        if (value == null || value.isBlank()) return value == null ? "" : value;
        if (!english) return value;
        String exact = EN.get(value);
        if (exact != null) return exact;

        String result = value;
        for (Map.Entry<String, String> entry : EN.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        result = result.replace("Стр ", "Page ");
        result = result.replace("Шаг ", "Step ");
        result = result.replace("Загрузка", "Loading");
        result = result.replace("Загружено", "Loaded");
        result = result.replace("Ошибка", "Error");
        result = result.replace("Сессия", "Session");
        result = result.replace("активна", "active");
        result = result.replace("истекла", "expired");
        result = result.replace("до ", "until ");
        result = result.replace("обновлено", "updated");
        result = result.replace("неизвестно", "unknown");
        result = result.replace("фильтр", "filter");
        result = result.replace("мои арты", "my arts");
        result = result.replace("недавние", "recent");
        result = result.replace("стройка", "building");
        result = result.replace("сбор", "gathering");
        result = result.replace("трекер", "tracker");
        return result;
    }

    static String lower(String value) {
        return translate(value).toLowerCase(Locale.ROOT);
    }

    private static void put(String ru, String en) {
        EN.put(ru, en);
    }
}

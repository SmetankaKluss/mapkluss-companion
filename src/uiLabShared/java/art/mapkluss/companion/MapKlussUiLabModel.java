package art.mapkluss.companion;

final class MapKlussUiLabModel {
    enum Page {
        LIBRARY("Библиотека", "Library"),
        ART("Арт", "Art"),
        COLLECTIONS("Коллекции", "Collections"),
        SCAN("Скан", "Scan"),
        LENS("Lens", "Lens"),
        TRACKER("Трекер", "Tracker"),
        LOGIN("Вход", "Device login"),
        TWO_LAYER("Two-layer", "Two-layer"),
        UPDATE("Обновление", "Update notice");

        private final String ru;
        private final String en;

        Page(String ru, String en) {
            this.ru = ru;
            this.en = en;
        }

        String label(boolean english) {
            return english ? en : ru;
        }
    }

    enum State {
        EMPTY("Пусто", "Empty"),
        LOADING("Загрузка", "Loading"),
        SUCCESS("Готово", "Success"),
        WARNING("Предупреждение", "Warning"),
        ERROR("Ошибка", "Error"),
        POPULATED("Заполнено", "Populated"),
        DISABLED("Недоступно", "Disabled");

        private final String ru;
        private final String en;

        State(String ru, String en) {
            this.ru = ru;
            this.en = en;
        }

        String label(boolean english) {
            return english ? en : ru;
        }
    }

    enum Viewport {
        CURRENT("Auto", 0, 0),
        COMPACT("498×280", 498, 280),
        STANDARD("640×360", 640, 360),
        WIDE("860×480", 860, 480);

        private final String label;
        private final int width;
        private final int height;

        Viewport(String label, int width, int height) {
            this.label = label;
            this.width = width;
            this.height = height;
        }

        String label() {
            return label;
        }

        int fitWidth(int available) {
            return width == 0 ? available : Math.min(available, width);
        }

        int fitHeight(int available) {
            return height == 0 ? available : Math.min(available, height);
        }
    }

    private Page page = Page.LIBRARY;
    private State state = State.POPULATED;
    private Viewport viewport = Viewport.CURRENT;
    private boolean english;
    private boolean longCopy;
    private boolean guides;
    private String notice = "";

    Page page() {
        return page;
    }

    State state() {
        return state;
    }

    Viewport viewport() {
        return viewport;
    }

    boolean english() {
        return english;
    }

    boolean longCopy() {
        return longCopy;
    }

    boolean guides() {
        return guides;
    }

    String notice() {
        return notice;
    }

    void previousPage() {
        page = previous(Page.values(), page);
    }

    void nextPage() {
        page = next(Page.values(), page);
    }

    void previousState() {
        state = previous(State.values(), state);
    }

    void nextState() {
        state = next(State.values(), state);
    }

    void nextViewport() {
        viewport = next(Viewport.values(), viewport);
    }

    void toggleLanguage() {
        english = !english;
    }

    void toggleLongCopy() {
        longCopy = !longCopy;
    }

    void toggleGuides() {
        guides = !guides;
    }

    void notice(String value) {
        notice = value == null ? "" : value;
    }

    String title() {
        return page.label(english);
    }

    String stateLabel() {
        return state.label(english);
    }

    String status() {
        return switch (state) {
            case EMPTY -> english ? "No local fixture data" : "Нет локальных тестовых данных";
            case LOADING -> english ? "Loading local fixture…" : "Загрузка локального примера…";
            case SUCCESS -> english ? "Fixture is ready" : "Пример готов";
            case WARNING -> english ? "Check this state before release" : "Проверьте это состояние перед релизом";
            case ERROR -> english ? "Could not load the local fixture" : "Не удалось загрузить локальный пример";
            case POPULATED -> english ? "Safe local fixture · no backend" : "Безопасный локальный пример · без backend";
            case DISABLED -> english ? "Actions are disabled for this state" : "Действия недоступны в этом состоянии";
        };
    }

    String emptyDetail() {
        if (longCopy) {
            return english
                ? "This deliberately long line checks clipping, wrapping, and narrow GUI layouts without using account data."
                : "Эта намеренно длинная строка проверяет обрезку, перенос и узкие размеры GUI без данных аккаунта.";
        }
        return english ? "Switch state to preview content" : "Смените состояние для просмотра";
    }

    boolean populated() {
        return state == State.POPULATED || state == State.SUCCESS;
    }

    boolean actionsEnabled() {
        return state != State.DISABLED && state != State.LOADING && state != State.ERROR;
    }

    String actionPrimary() {
        return switch (page) {
            case LIBRARY -> english ? "Open art" : "Открыть арт";
            case ART -> english ? "Install schema" : "Установить схему";
            case COLLECTIONS -> english ? "Open collection" : "Открыть коллекцию";
            case SCAN -> english ? "Scan map" : "Сканировать";
            case LENS -> english ? "Join session" : "Войти в сессию";
            case TRACKER -> english ? "Open tracker" : "Открыть трекер";
            case LOGIN -> english ? "Get code" : "Получить код";
            case TWO_LAYER -> english ? "Import ZIP" : "Импорт ZIP";
            case UPDATE -> english ? "Download" : "Скачать";
        };
    }

    String actionSecondary() {
        return switch (page) {
            case LIBRARY -> english ? "Collections" : "Коллекции";
            case ART -> english ? "Export" : "Экспорт";
            case COLLECTIONS -> english ? "Create" : "Создать";
            case SCAN -> english ? "History" : "История";
            case LENS -> english ? "Refresh" : "Обновить";
            case TRACKER -> english ? "Recent" : "Недавние";
            case LOGIN -> english ? "Open site" : "Открыть сайт";
            case TWO_LAYER -> english ? "From Cloud" : "Из облака";
            case UPDATE -> "Telegram";
        };
    }

    private static <T> T next(T[] values, T current) {
        int index = java.util.Arrays.asList(values).indexOf(current);
        return values[(index + 1) % values.length];
    }

    private static <T> T previous(T[] values, T current) {
        int index = java.util.Arrays.asList(values).indexOf(current);
        return values[(index - 1 + values.length) % values.length];
    }
}

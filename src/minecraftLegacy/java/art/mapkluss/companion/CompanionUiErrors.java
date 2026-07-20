package art.mapkluss.companion;

import java.util.Locale;

final class CompanionUiErrors {
    private CompanionUiErrors() {
    }

    static String message(String operation, Throwable error) {
        MapKlussCompanionClient.LOGGER.warn("MapKluss Companion UI action '{}' failed.", operation, error);
        return message(operation, error == null ? null : error.getMessage(), CompanionI18n.english(net.minecraft.client.MinecraftClient.getInstance()));
    }

    static String message(String operation, String detail) {
        return message(operation, detail, CompanionI18n.english(net.minecraft.client.MinecraftClient.getInstance()));
    }

    static String message(String operation, String detail, boolean english) {
        String value = detail == null ? "" : detail.toLowerCase(Locale.ROOT);
        if (value.contains("hold a filled map")) {
            return english ? "Hold a filled map in either hand." : "Возьмите заполненную карту в любую руку.";
        }
        if (value.contains("look at an item frame")) {
            return english ? "Look at an item frame containing a filled map." : "Наведитесь на рамку с заполненной картой.";
        }
        if (value.contains("not_found") || value.contains("http 404") || value.contains("status 404")) {
            return english ? "Session not found. Check the UUID and try again." : "Сессия не найдена. Проверьте UUID и повторите.";
        }
        if (value.contains("unauthorized") || value.contains("forbidden") || value.contains("http 401") || value.contains("http 403")) {
            return english ? "Sign in to MapKluss and try again." : "Войдите в MapKluss и повторите.";
        }
        if (value.contains("unsupported") || value.contains("not supported") || value.contains("corrupt") || value.contains("invalid plan")) {
            return english ? "The file is damaged or unsupported. Export it again from MapKluss." : "Файл повреждён или не поддерживается. Экспортируйте его заново из MapKluss.";
        }
        if (value.contains("timeout") || value.contains("timed out") || value.contains("connection")
            || value.contains("connectexception") || value.contains("unknownhost")) {
            return english ? "Could not reach MapKluss. Check your connection and try again." : "Не удалось связаться с MapKluss. Проверьте соединение и повторите.";
        }
        return switch (operation == null ? "" : operation) {
            case "scan" -> english ? "Could not scan the map. Check the map and frame." : "Не удалось отсканировать карту. Проверьте карту и рамку.";
            case "tracker" -> english ? "Could not load the tracker. Check the UUID and try again." : "Не удалось загрузить трекер. Проверьте UUID и повторите.";
            case "lens" -> english ? "Could not update Lens. Check the selected session and try again." : "Не удалось обновить Lens. Проверьте выбранную сессию и повторите.";
            case "save" -> english ? "Could not save the changes. Try again." : "Не удалось сохранить изменения. Повторите попытку.";
            case "delete" -> english ? "Could not delete the item. Try again." : "Не удалось удалить объект. Повторите попытку.";
            case "download" -> english ? "Could not download the file. Try again." : "Не удалось скачать файл. Повторите попытку.";
            case "site" -> english ? "Could not open the MapKluss website." : "Не удалось открыть сайт MapKluss.";
            case "login" -> english ? "Could not complete sign-in. Request a new code and try again." : "Не удалось выполнить вход. Получите новый код и повторите.";
            case "sync" -> english ? "Could not sync the changes. Try again." : "Не удалось синхронизировать изменения. Повторите попытку.";
            case "two-layer" -> english ? "Could not complete the Two-layer action. Try again." : "Не удалось выполнить действие Two-layer. Повторите попытку.";
            default -> english ? "The action could not be completed. Try again." : "Не удалось выполнить действие. Повторите попытку.";
        };
    }
}

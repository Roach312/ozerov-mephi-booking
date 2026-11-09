package ru.mephi.hotelbooking.gateway.error;

/** Заголовки ответов об ошибках на русском языке по HTTP-статусу. */
public final class ProblemTitles {

    private ProblemTitles() {
    }

    public static String titleFor(int status) {
        return switch (status) {
            case 400 -> "Некорректный запрос";
            case 401 -> "Требуется аутентификация";
            case 403 -> "Доступ запрещён";
            case 404 -> "Не найдено";
            case 405 -> "Метод не поддерживается";
            case 406 -> "Недопустимый формат ответа";
            case 409 -> "Конфликт";
            case 415 -> "Неподдерживаемый тип содержимого";
            case 500 -> "Внутренняя ошибка сервера";
            case 502 -> "Ошибка шлюза";
            case 503 -> "Сервис недоступен";
            case 504 -> "Тайм-аут шлюза";
            default -> "Ошибка (" + status + ")";
        };
    }
}

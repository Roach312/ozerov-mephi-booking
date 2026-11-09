package ru.mephi.hotelbooking.hotel.dto;

import ru.mephi.hotelbooking.hotel.entity.SlotStatus;

/**
 * @param changed true, если этим вызовом действительно снят HELD-слот; false при идемпотентном повторе или
 *                когда была записана только запись-"надгробие"
 */
public record ReleaseResponse(
        String requestId,
        Long roomId,
        SlotStatus status,
        boolean changed) {
}

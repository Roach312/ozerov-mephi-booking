package ru.mephi.hotelbooking.hotel.entity;

public enum SlotStatus {
    /** Слот блокирует номер на период. */
    HELD,
    /** Слот снят (компенсация или отмена); запись сохраняется как журнал идемпотентности. */
    RELEASED
}

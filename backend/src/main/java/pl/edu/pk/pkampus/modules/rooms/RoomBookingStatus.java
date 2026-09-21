package pl.edu.pk.pkampus.modules.rooms;

public enum RoomBookingStatus {
    CONFIRMED,
    KEY_ISSUED,
    COMPLETED,
    CANCELLED_USER,
    AUTO_CANCELLED_15MIN,
    CANCELLED_ROOM_MAINTENANCE
}

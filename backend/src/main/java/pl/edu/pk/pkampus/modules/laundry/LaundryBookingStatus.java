package pl.edu.pk.pkampus.modules.laundry;

public enum LaundryBookingStatus {
    CONFIRMED,
    KEY_ISSUED,
    COMPLETED,
    CANCELLED_USER,
    AUTO_CANCELLED_15MIN,
    CANCELLED_MACHINE_OUT_OF_ORDER
}

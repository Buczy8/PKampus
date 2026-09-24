package pl.edu.pk.pkampus.modules.booking;

/**
 * Domain discriminator of the resource a released booking referred to
 * (a laundry machine or a thematic room). Deliberately carries no
 * presentation details: the notification layer maps it to the
 * resident-facing schedule page (see {@code BookingMailListener}).
 */
public enum ResourceKind {

    LAUNDRY,
    ROOM
}

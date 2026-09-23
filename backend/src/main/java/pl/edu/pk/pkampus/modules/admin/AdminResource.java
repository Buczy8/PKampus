package pl.edu.pk.pkampus.modules.admin;

/**
 * Resources managed by dormitory administrators.
 * Labels feed the "Only dormitory administrators can manage ..." denial messages.
 */
public enum AdminResource {
    ROOMS("rooms"),
    RECEPTIONISTS("receptionists"),
    RESIDENTS("residents"),
    APPLICATIONS("residency applications");

    private final String label;

    AdminResource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}

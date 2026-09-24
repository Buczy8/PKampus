package pl.edu.pk.pkampus.mail;

/**
 * Resident-facing schedule pages linked from notification e-mails.
 * A closed set of relative paths, so no caller can inject an arbitrary URL.
 */
public enum ResourceSchedulePage {

    LAUNDRY("laundry"),
    ROOMS("rooms");

    private final String path;

    ResourceSchedulePage(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}

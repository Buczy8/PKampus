package pl.edu.pk.pkampus.mail;

/**
 * Value object holding the rendered email parts ready for MIME dispatch.
 */
record RenderedEmail(String subject, String htmlBody, String plainTextBody) {
}

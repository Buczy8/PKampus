package pl.edu.pk.pkampus.modules.admin;

import lombok.extern.slf4j.Slf4j;
import pl.edu.pk.pkampus.common.storage.MinioStorageService;
import pl.edu.pk.pkampus.modules.user.User;

import java.util.UUID;

/**
 * Shared avatar helpers for admin services.
 * Replaces the duplicated presigned-URL and quiet-delete blocks
 * previously copied into the resident services.
 */
@Slf4j
public final class AdminAvatarUrls {

    public static final int PRESIGN_MINUTES = 60;

    private AdminAvatarUrls() {
    }

    /**
     * Returns a presigned avatar URL, or {@code null} when the user has no avatar
     * or the URL cannot be generated. Never throws.
     */
    public static String presignedOrNull(MinioStorageService minioStorageService, User user) {
        if (user.getAvatarUrl() == null || user.getAvatarUrl().isBlank()) {
            return null;
        }
        try {
            return minioStorageService.getAvatarPresignedUrl(user.getAvatarUrl(), PRESIGN_MINUTES);
        } catch (Exception e) {
            log.warn("Could not generate avatar URL for user {}: {}", user.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Deletes an avatar object, logging (instead of throwing) on failure.
     * No-op when {@code avatarObject} is blank.
     */
    public static void removeQuietly(MinioStorageService minioStorageService, String avatarObject, UUID userId) {
        if (avatarObject == null || avatarObject.isBlank()) {
            return;
        }
        try {
            minioStorageService.removeAvatar(avatarObject);
        } catch (Exception e) {
            log.error("Failed to remove avatar {} for user {}", avatarObject, userId, e);
        }
    }
}

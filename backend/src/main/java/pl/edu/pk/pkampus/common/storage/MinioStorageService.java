package pl.edu.pk.pkampus.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pl.edu.pk.pkampus.common.exception.FileStorageException;
import pl.edu.pk.pkampus.common.exception.InvalidFileException;

import java.io.InputStream;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * Narrow facade over MinIO object storage for avatars and issue photos (NFR-SEC-03).
 *
 * <p>Contract for callers (blocking I/O):
 * <ul>
 *   <li>All methods below perform blocking MinIO I/O and must NOT be called
 *       inside {@code @Transactional} — upload first, then persist the database
 *       row, and compensate with {@code removeAvatar/removeIssuePhoto} on failure
 *       (see {@code RegistrationService.registerResident}).</li>
 *   <li>Validation opens a short stream for the 16-byte magic-number probe and the
 *       upload opens a second one. This two-step is intentional:
 *       {@code MultipartFile} implementations buffer content, so reopening is safe,
 *       and it keeps validation cheap (fail before any network I/O).</li>
 *   <li>Image validation is a header-only magic-bytes gate, not a full image parse.
 *       Buckets stay private; reads go through short-lived presigned URLs served
 *       with the validated content-type, and stored bytes are never executed.</li>
 * </ul>
 */
@Slf4j
@Service
public class MinioStorageService {

    public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB (NFR-SEC-03)
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final MinioClient minioClient;
    private final String avatarBucket;
    private final String issuesBucket;
    private final ConcurrentHashMap<String, FutureTask<Void>> initGates = new ConcurrentHashMap<>();

    public MinioStorageService(MinioClient minioClient,
            @Value("${minio.bucket-avatars:pkampus-avatars}") String avatarBucket,
            @Value("${minio.bucket-issues:pkampus-issues}") String issuesBucket) {
        this.minioClient = Objects.requireNonNull(minioClient, "MinioClient is required");
        if (avatarBucket == null || avatarBucket.isBlank()
                || issuesBucket == null || issuesBucket.isBlank()) {
            throw new IllegalStateException("MinIO bucket names are required");
        }
        this.avatarBucket = avatarBucket;
        this.issuesBucket = issuesBucket;
    }

    /**
     * Creates the bucket on first write instead of at startup, so a missing or
     * lagging MinIO never breaks application boot nor fails fast in environments
     * without object storage (e.g. slice tests). Misconfiguration still surfaces
     * loudly at the first real operation via {@link FileStorageException}.
     *
     * <p>Memoized per bucket: exactly one thread performs the check-then-create
     * while the rest join it, so no write can slip past initialization on a cold
     * parallel start. A failed attempt evicts the gate so the next call retries.
     */
    private void ensureBucketInitialized(String bucketName) {
        FutureTask<Void> task = new FutureTask<>(() -> {
            ensureBucketExists(bucketName);
            return null;
        });
        FutureTask<Void> effective = initGates.putIfAbsent(bucketName, task);
        if (effective == null) {
            effective = task;
            task.run();
        }
        try {
            effective.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FileStorageException("Interrupted while initializing bucket: " + bucketName, e);
        } catch (ExecutionException e) {
            initGates.remove(bucketName, effective);
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeCause) {
                throw runtimeCause;
            }
            throw new FileStorageException("Error initializing bucket: " + bucketName, cause);
        }
    }

    void ensureBucketExists(String bucketName) {
        if (bucketName == null || bucketName.isBlank()) {
            throw new InvalidFileException("Bucket name is required");
        }
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                try {
                    minioClient.makeBucket(
                            MakeBucketArgs.builder().bucket(bucketName).build()
                    );
                } catch (io.minio.errors.ErrorResponseException e) {
                    if (isBucketAlreadyExists(e)) {
                        // Lost the check-then-create race against another instance starting
                        // concurrently; the bucket is there now, nothing to do.
                        log.info("MinIO bucket already created concurrently: {}", bucketName);
                        return;
                    }
                    throw e;
                }
                // Buckets are created without any anonymous/public policy and stay private;
                // all reads go through short-lived presigned URLs.
                log.info("Created private MinIO bucket: {}", bucketName);
            }
        } catch (FileStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new FileStorageException("Error verifying/creating bucket: " + bucketName, e);
        }
    }

    private static boolean isBucketAlreadyExists(io.minio.errors.ErrorResponseException e) {
        String code = e.errorResponse() != null ? e.errorResponse().code() : null;
        return "BucketAlreadyOwnedByYou".equals(code) || "BucketAlreadyExists".equals(code);
    }

    public String uploadAvatar(MultipartFile file) {
        String detectedMime = validateAndDetectImageType(file);
        return uploadAvatar(file, detectedMime);
    }

    /**
     * Uploads a validated avatar. The file is always re-validated (magic bytes,
     * size, declared type) and the hint must match the detected type, so a
     * caller can never smuggle unvalidated bytes past NFR-SEC-03. The resulting
     * double validation on the registration path (validate, then upload with
     * hint) is intentional: the 16-byte probe is negligible next to the upload,
     * and removing the re-check to "optimize" would reopen the bypass.
     */
    public String uploadAvatar(MultipartFile file, String detectedMime) {
        String trustedMime = validateAndDetectImageType(file);
        if (!trustedMime.equalsIgnoreCase(detectedMime)) {
            throw new InvalidFileException("Untrusted MIME hint");
        }
        String extension = extractExtension(trustedMime);
        String objectName = UUID.randomUUID() + extension;
        ensureBucketInitialized(avatarBucket);
        uploadFile(avatarBucket, objectName, file, trustedMime);
        return objectName;
    }

    public String uploadIssuePhoto(MultipartFile file) {
        String detectedMime = validateAndDetectImageType(file);
        String extension = extractExtension(detectedMime);
        String objectName = UUID.randomUUID() + extension;
        ensureBucketInitialized(issuesBucket);
        uploadFile(issuesBucket, objectName, file, detectedMime);
        return objectName;
    }

    private void uploadFile(String bucketName, String objectName, MultipartFile file, String contentType) {
        requireSafeObjectKey(objectName);
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );
            log.debug("Successfully uploaded file {} to bucket {}", objectName, bucketName);
        } catch (Exception e) {
            throw new FileStorageException("Failed to upload file " + objectName + " to bucket " + bucketName, e);
        }
    }

    public void removeAvatar(String objectName) {
        removeFile(avatarBucket, objectName);
    }

    public void removeIssuePhoto(String objectName) {
        removeFile(issuesBucket, objectName);
    }

    private void removeFile(String bucketName, String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return;
        }
        if (bucketName == null || bucketName.isBlank()) {
            throw new InvalidFileException("Bucket name is required");
        }
        requireSafeObjectKey(objectName);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.debug("Successfully removed file {} from bucket {}", objectName, bucketName);
        } catch (Exception e) {
            throw new FileStorageException("Failed to remove file " + objectName + " from bucket " + bucketName, e);
        }
    }

    private static final int MAX_PRESIGNED_EXPIRY_MINUTES = 10080; // 7 days, MinIO/S3 ceiling

    /**
     * Single choke point for object-key safety. Generated keys are always
     * {@code UUID + extension}, but persisted keys come from the database and a
     * poisoned row must never reach the storage client unchecked.
     */
    private static void requireSafeObjectKey(String objectName) {
        if (objectName == null || objectName.isBlank()
                || objectName.contains("/") || objectName.contains("\\")
                || objectName.contains("..")) {
            throw new InvalidFileException("Invalid file reference");
        }
    }

    private String getPresignedUrl(String bucketName, String objectName, int expiryMinutes) {
        if (bucketName == null || bucketName.isBlank() || objectName == null || objectName.isBlank()) {
            throw new InvalidFileException("Invalid file reference");
        }
        if (expiryMinutes < 1 || expiryMinutes > MAX_PRESIGNED_EXPIRY_MINUTES) {
            throw new InvalidFileException(
                    "Presigned URL expiry must be between 1 and " + MAX_PRESIGNED_EXPIRY_MINUTES + " minutes");
        }
        requireSafeObjectKey(objectName);
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            throw new FileStorageException("Failed to generate presigned URL for file " + objectName, e);
        }
    }

    public String validateAndDetectImageType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File cannot be empty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidFileException("File size exceeds the maximum allowed limit of 5 MB");
        }

        String declaredContentType = file.getContentType();
        if (declaredContentType == null || !ALLOWED_CONTENT_TYPES.contains(declaredContentType.toLowerCase())) {
            throw new InvalidFileException("Unsupported file format. Allowed formats are: JPEG, PNG, WebP");
        }

        String detectedMime = detectImageMimeType(file);

        // Verify that declared content-type matches actual binary content
        if (!declaredContentType.equalsIgnoreCase(detectedMime)) {
            log.warn("MIME type mismatch detected: declared='{}', detected='{}'", declaredContentType, detectedMime);
            throw new InvalidFileException("MIME type mismatch: declared '" + declaredContentType + "' does not match file binary signature");
        }

        return detectedMime;
    }

    private String detectImageMimeType(MultipartFile file) {
        byte[] headerBytes = new byte[16];
        int bytesRead;
        try (InputStream is = file.getInputStream()) {
            bytesRead = is.readNBytes(headerBytes, 0, 16);
        } catch (Exception e) {
            throw new InvalidFileException("Unable to read file content for validation", e);
        }

        String detected = detectImageMimeFromMagicBytes(headerBytes, bytesRead);
        if (detected == null) {
            throw new InvalidFileException("File content signature does not match any allowed image format (JPEG, PNG, WebP)");
        }
        return detected;
    }


    private String detectImageMimeFromMagicBytes(byte[] b, int length) {
        // JPEG: FF D8 FF
        if (length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (length >= 8 &&
                (b[0] & 0xFF) == 0x89 &&
                (b[1] & 0xFF) == 0x50 &&
                (b[2] & 0xFF) == 0x4E &&
                (b[3] & 0xFF) == 0x47 &&
                (b[4] & 0xFF) == 0x0D &&
                (b[5] & 0xFF) == 0x0A &&
                (b[6] & 0xFF) == 0x1A &&
                (b[7] & 0xFF) == 0x0A) {
            return "image/png";
        }
        // WebP: "RIFF" (0x52 0x49 0x46 0x46) at offset 0 and "WEBP"
        // (0x57 0x45 0x42 0x50) at offset 8. Header-only gate, not a full image
        // parse: a matching prefix with trailing garbage still passes. The real
        // defense is serving with the validated content-type and never executing
        // stored bytes.
        if (length >= 12 &&
                (b[0] & 0xFF) == 0x52 && (b[1] & 0xFF) == 0x49 && (b[2] & 0xFF) == 0x46 && (b[3] & 0xFF) == 0x46 &&
                (b[8] & 0xFF) == 0x57 && (b[9] & 0xFF) == 0x45 && (b[10] & 0xFF) == 0x42 && (b[11] & 0xFF) == 0x50) {
            return "image/webp";
        }
        return null;
    }

    private String extractExtension(String contentType) {
        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    /**
     * Returns a presigned avatar URL, or {@code null} when the user has no avatar.
     * Blank is null (documented absence); malformed references throw
     * {@link InvalidFileException} instead of leaking storage errors.
     */
    public String getAvatarPresignedUrl(String objectName, int expiryMinutes) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        return getPresignedUrl(avatarBucket, objectName, expiryMinutes);
    }

    /**
     * Returns a presigned issue-photo URL, or {@code null} when the issue has no photo.
     * Blank is null (documented absence); malformed references throw
     * {@link InvalidFileException} instead of leaking storage errors.
     */
    public String getIssuePresignedUrl(String objectName, int expiryMinutes) {
        if (objectName == null || objectName.isBlank()) {
            return null;
        }
        return getPresignedUrl(issuesBucket, objectName, expiryMinutes);
    }

}

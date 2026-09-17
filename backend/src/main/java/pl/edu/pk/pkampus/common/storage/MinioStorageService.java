package pl.edu.pk.pkampus.common.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pl.edu.pk.pkampus.common.exception.FileStorageException;
import pl.edu.pk.pkampus.common.exception.InvalidFileException;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MinioStorageService {

    public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB (NFR-SEC-03)
    public static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final MinioClient minioClient;

    @Value("${minio.bucket-avatars:pkampus-avatars}")
    private String avatarBucket;

    @Value("${minio.bucket-issues:pkampus-issues}")
    private String issuesBucket;

    @PostConstruct
    public void initBuckets() {
        try {
            ensureBucketExists(avatarBucket);
            ensureBucketExists(issuesBucket);
        } catch (Exception e) {
            log.warn("Could not automatically initialize MinIO buckets on startup: {}", e.getMessage());
        }
    }

    public void ensureBucketExists(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("Created private MinIO bucket: {}", bucketName);
            }
        } catch (Exception e) {
            throw new FileStorageException("Error verifying/creating bucket: " + bucketName, e);
        }
    }

    public String uploadAvatar(MultipartFile file) {
        String detectedMime = validateAndDetectImageType(file);
        return uploadAvatar(file, detectedMime);
    }

    public String uploadAvatar(MultipartFile file, String detectedMime) {
        String extension = extractExtension(detectedMime);
        String objectName = UUID.randomUUID() + extension;
        uploadFile(avatarBucket, objectName, file, detectedMime);
        return objectName;
    }

    public String uploadIssuePhoto(MultipartFile file) {
        String detectedMime = validateAndDetectImageType(file);
        String extension = extractExtension(detectedMime);
        String objectName = UUID.randomUUID() + extension;
        uploadFile(issuesBucket, objectName, file, detectedMime);
        return objectName;
    }

    public void uploadFile(String bucketName, String objectName, MultipartFile file, String contentType) {
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType != null ? contentType : file.getContentType())
                            .build()
            );
            log.debug("Successfully uploaded file {} to bucket {}", objectName, bucketName);
        } catch (Exception e) {
            throw new FileStorageException("Failed to upload file " + objectName + " to bucket " + bucketName, e);
        }
    }

    public InputStream getFile(String bucketName, String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new FileStorageException("Failed to retrieve file " + objectName + " from bucket " + bucketName, e);
        }
    }

    public byte[] getFileBytes(String bucketName, String objectName) {
        try (InputStream is = getFile(bucketName, objectName)) {
            return is.readAllBytes();
        } catch (Exception e) {
            throw new FileStorageException("Failed to read bytes from file " + objectName, e);
        }
    }

    public void removeAvatar(String objectName) {
        removeFile(avatarBucket, objectName);
    }

    public void removeIssuePhoto(String objectName) {
        removeFile(issuesBucket, objectName);
    }

    public void removeFile(String bucketName, String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return;
        }
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

    public String getPresignedUrl(String bucketName, String objectName, int expiryMinutes) {
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

        byte[] headerBytes = new byte[16];
        int bytesRead;
        try (InputStream is = file.getInputStream()) {
            bytesRead = is.readNBytes(headerBytes, 0, 16);
        } catch (Exception e) {
            throw new InvalidFileException("Unable to read file content for validation", e);
        }

        String detectedMime = detectImageMimeFromMagicBytes(headerBytes, bytesRead);
        if (detectedMime == null || !ALLOWED_CONTENT_TYPES.contains(detectedMime)) {
            throw new InvalidFileException("File content signature does not match any allowed image format (JPEG, PNG, WebP)");
        }

        // Verify that declared content-type matches actual binary content
        if (!declaredContentType.equalsIgnoreCase(detectedMime)) {
            log.warn("MIME type mismatch detected: declared='{}', detected='{}'", declaredContentType, detectedMime);
            throw new InvalidFileException("MIME type mismatch: declared '" + declaredContentType + "' does not match file binary signature");
        }

        return detectedMime;
    }

    public void validateImageFile(MultipartFile file) {
        validateAndDetectImageType(file);
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
        // WebP: 52 49 46 46 (RIFF) ... 57 45 42 50 (WEBP) at offset 8
        if (length >= 12 &&
                b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' &&
                b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private String extractExtension(String contentType) {
        if (contentType == null) {
            return ".jpg";
        }
        return switch (contentType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    public String getAvatarBucket() {
        return avatarBucket;
    }

    public String getIssuesBucket() {
        return issuesBucket;
    }
}

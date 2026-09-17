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
        validateImageFile(file);
        String extension = extractExtension(file.getContentType());
        String objectName = UUID.randomUUID() + extension;
        uploadFile(avatarBucket, objectName, file);
        return objectName;
    }

    public String uploadIssuePhoto(MultipartFile file) {
        validateImageFile(file);
        String extension = extractExtension(file.getContentType());
        String objectName = UUID.randomUUID() + extension;
        uploadFile(issuesBucket, objectName, file);
        return objectName;
    }

    public void uploadFile(String bucketName, String objectName, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
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

    public void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Plik nie może być pusty");
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new InvalidFileException("Rozmiar pliku przekracza dopuszczalny limit 5 MB");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidFileException("Niedozwolony format pliku. Akceptowane formaty to: JPEG, PNG, WebP");
        }
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

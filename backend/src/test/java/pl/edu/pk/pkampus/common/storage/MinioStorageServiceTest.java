package pl.edu.pk.pkampus.common.storage;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import pl.edu.pk.pkampus.common.exception.InvalidFileException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MinioStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    private MinioStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new MinioStorageService(minioClient, "pkampus-avatars", "pkampus-issues");
    }

    @Test
    void shouldUploadAvatarSuccessfullyForValidJpeg() throws Exception {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "avatar.jpg",
                "image/jpeg",
                jpegBytes
        );

        String objectName = storageService.uploadAvatar(file);

        assertNotNull(objectName);
        assertTrue(objectName.endsWith(".jpg"));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void shouldUploadAvatarSuccessfullyForValidPng() throws Exception {
        byte[] pngBytes = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 0, 0, 0, 0, 0
        };
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "avatar.png",
                "image/png",
                pngBytes
        );

        String objectName = storageService.uploadAvatar(file);

        assertNotNull(objectName);
        assertTrue(objectName.endsWith(".png"));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void shouldUploadAvatarSuccessfullyForValidWebp() throws Exception {
        byte[] webpBytes = new byte[]{
                'R', 'I', 'F', 'F', 0, 0, 0, 0,
                'W', 'E', 'B', 'P', 0, 0, 0, 0
        };
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "avatar.webp",
                "image/webp",
                webpBytes
        );

        String objectName = storageService.uploadAvatar(file);

        assertNotNull(objectName);
        assertTrue(objectName.endsWith(".webp"));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void shouldRejectSpoofedFileWhenDeclaredJpegButBinaryIsMaliciousTextOrShell() {
        byte[] scriptBytes = "#!/bin/bash\necho hacked".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "malicious.jpg",
                "image/jpeg",
                scriptBytes
        );

        InvalidFileException ex = assertThrows(
                InvalidFileException.class,
                () -> storageService.uploadAvatar(file)
        );
        assertTrue(ex.getMessage().contains("signature does not match"));
    }

    @Test
    void shouldRejectWhenDeclaredContentTypeDoesNotMatchBinaryFormat() {
        // Real PNG bytes, but declared as image/jpeg in HTTP header
        byte[] pngBytes = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 0, 0, 0, 0, 0
        };
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "spoofed.jpg",
                "image/jpeg",
                pngBytes
        );

        InvalidFileException ex = assertThrows(
                InvalidFileException.class,
                () -> storageService.uploadAvatar(file)
        );
        assertTrue(ex.getMessage().contains("MIME type mismatch"));
    }

    @Test
    void shouldRejectFileExceeding5MB() {
        byte[] largeBytes = new byte[(int) (MinioStorageService.MAX_FILE_SIZE_BYTES + 1)];
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "large.jpg",
                "image/jpeg",
                largeBytes
        );

        InvalidFileException ex = assertThrows(
                InvalidFileException.class,
                () -> storageService.uploadAvatar(file)
        );
        assertTrue(ex.getMessage().contains("5 MB"));
    }

    @Test
    void shouldRejectUnsupportedMimeType() {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                new byte[]{1, 2, 3}
        );

        InvalidFileException ex = assertThrows(
                InvalidFileException.class,
                () -> storageService.uploadAvatar(pdfFile)
        );
        assertTrue(ex.getMessage().contains("JPEG, PNG, WebP"));
    }

    @Test
    void shouldRejectEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "photo",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        assertThrows(InvalidFileException.class, () -> storageService.uploadAvatar(emptyFile));
    }

    @Test
    void shouldRemoveAvatarSuccessfully() throws Exception {
        storageService.removeAvatar("test-avatar-uuid.jpg");

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertEquals("pkampus-avatars", captor.getValue().bucket());
        assertEquals("test-avatar-uuid.jpg", captor.getValue().object());
    }

    @Test
    void shouldUploadIssuePhotoSuccessfullyForValidJpeg() throws Exception {
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "broken_radiator.jpg",
                "image/jpeg",
                jpegBytes
        );

        String objectName = storageService.uploadIssuePhoto(file);

        assertNotNull(objectName);
        assertTrue(objectName.endsWith(".jpg"));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void shouldRemoveIssuePhotoSuccessfully() throws Exception {
        storageService.removeIssuePhoto("issue-photo-123.jpg");

        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertEquals("pkampus-issues", captor.getValue().bucket());
        assertEquals("issue-photo-123.jpg", captor.getValue().object());
    }

    @Test
    void shouldDoNothingWhenRemovingNullOrBlankFile() throws Exception {
        storageService.removeAvatar(null);
        storageService.removeAvatar("   ");
        storageService.removeIssuePhoto(null);

        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void shouldCreateBucketIfNotExists() throws Exception {
        when(minioClient.bucketExists(any(io.minio.BucketExistsArgs.class))).thenReturn(false);

        storageService.ensureBucketExists("test-bucket");

        verify(minioClient).makeBucket(any(io.minio.MakeBucketArgs.class));
    }

    @Test
    void shouldNotCreateBucketIfAlreadyExists() throws Exception {
        when(minioClient.bucketExists(any(io.minio.BucketExistsArgs.class))).thenReturn(true);

        storageService.ensureBucketExists("test-bucket");

        verify(minioClient, never()).makeBucket(any(io.minio.MakeBucketArgs.class));
    }

    @Test
    void shouldThrowFileStorageExceptionWhenEnsureBucketExistsFails() throws Exception {
        when(minioClient.bucketExists(any(io.minio.BucketExistsArgs.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThrows(pl.edu.pk.pkampus.common.exception.FileStorageException.class,
                () -> storageService.ensureBucketExists("failing-bucket"));
    }

    @Test
    void shouldReturnNullPresignedUrlWhenObjectNameIsNullOrBlank() {
        assertNull(storageService.getAvatarPresignedUrl(null, 15));
        assertNull(storageService.getAvatarPresignedUrl("   ", 15));
        assertNull(storageService.getIssuePresignedUrl(null, 15));
        assertNull(storageService.getIssuePresignedUrl("   ", 15));
    }

    @Test
    void shouldGeneratePresignedUrlsForValidObjectNames() throws Exception {
        when(minioClient.getPresignedObjectUrl(any(io.minio.GetPresignedObjectUrlArgs.class)))
                .thenReturn("https://minio.pkampus.edu/signed-url");

        String avatarUrl = storageService.getAvatarPresignedUrl("avatar.jpg", 30);
        String issueUrl = storageService.getIssuePresignedUrl("issue.png", 60);

        assertEquals("https://minio.pkampus.edu/signed-url", avatarUrl);
        assertEquals("https://minio.pkampus.edu/signed-url", issueUrl);
    }

    @Test
    void shouldRejectUnsupportedMimeInUploadAvatarOverload() {
        // Arrange
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "avatar.jpg",
                "image/jpeg",
                jpegBytes
        );

        // Act & Assert
        assertThrows(InvalidFileException.class,
                () -> storageService.uploadAvatar(file, "image/gif"));
    }

    @Test
    void shouldRejectMismatchedMimeHintEvenWhenHintIsAllowlisted() throws Exception {
        // Arrange: valid JPEG bytes declared as JPEG, but hint claims PNG
        byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "avatar.jpg",
                "image/jpeg",
                jpegBytes
        );

        // Act & Assert
        InvalidFileException ex = assertThrows(InvalidFileException.class,
                () -> storageService.uploadAvatar(file, "image/png"));
        assertTrue(ex.getMessage().contains("Untrusted MIME hint"));
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void shouldRejectNullFileWithClientErrorInsteadOfServerError() {
        // Act & Assert
        assertThrows(InvalidFileException.class,
                () -> storageService.uploadAvatar(null, "image/png"));
        assertThrows(InvalidFileException.class,
                () -> storageService.uploadIssuePhoto(null));
    }

    @Test
    void shouldRejectPresignedUrlWithInvalidExpiry() {
        // Arrange
        String objectName = java.util.UUID.randomUUID() + ".jpg";

        // Act & Assert
        assertThrows(InvalidFileException.class,
                () -> storageService.getAvatarPresignedUrl(objectName, 0));
        assertThrows(InvalidFileException.class,
                () -> storageService.getAvatarPresignedUrl(objectName, 10081));
    }

    @Test
    void shouldRejectPresignedUrlWithTraversalKey() {
        // Act & Assert
        assertThrows(InvalidFileException.class,
                () -> storageService.getAvatarPresignedUrl("../evil.jpg", 30));
        assertThrows(InvalidFileException.class,
                () -> storageService.getIssuePresignedUrl("a/b.png", 30));
    }

    @Test
    void shouldRejectBlankBucketName() {
        // Act & Assert
        assertThrows(InvalidFileException.class,
                () -> storageService.ensureBucketExists("   "));
        assertThrows(InvalidFileException.class,
                () -> storageService.removeAvatar("../evil.jpg"));
    }

    @Test
    void shouldSwallowConcurrentBucketCreationRace() throws Exception {
        // Arrange
        when(minioClient.bucketExists(any(io.minio.BucketExistsArgs.class))).thenReturn(false);
        io.minio.messages.ErrorResponse errorResponse = new io.minio.messages.ErrorResponse(
                "BucketAlreadyOwnedByYou", null, null, null, null, null, null);
        doThrow(new io.minio.errors.ErrorResponseException(errorResponse, null, ""))
                .when(minioClient).makeBucket(any(io.minio.MakeBucketArgs.class));

        // Act & Assert (no exception expected)
        assertDoesNotThrow(() -> storageService.ensureBucketExists("race-bucket"));
    }

    @Test
    void shouldRejectMissingBucketConfigurationFailFast() {
        // Act & Assert
        assertThrows(NullPointerException.class,
                () -> new MinioStorageService(null, "a", "b"));
        assertThrows(IllegalStateException.class,
                () -> new MinioStorageService(minioClient, "   ", "b"));
        assertThrows(IllegalStateException.class,
                () -> new MinioStorageService(minioClient, "a", null));
    }

    @Test
    void shouldCreateBucketOnlyOnceUnderConcurrentColdStart() throws Exception {
        // Arrange: cold start, bucket missing
        when(minioClient.bucketExists(any(io.minio.BucketExistsArgs.class))).thenReturn(false);

        // Act: 8 threads race the first write
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                byte[] jpegBytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
                MockMultipartFile file = new MockMultipartFile(
                        "photo", "avatar.jpg", "image/jpeg", jpegBytes);
                futures.add(pool.submit(() -> storageService.uploadAvatar(file)));
            }
            for (Future<String> future : futures) {
                assertNotNull(future.get(10, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        // Assert: check-then-create ran exactly once, no write slipped past init
        verify(minioClient, times(1)).bucketExists(any(io.minio.BucketExistsArgs.class));
        verify(minioClient, times(1)).makeBucket(any(io.minio.MakeBucketArgs.class));
        verify(minioClient, times(threads)).putObject(any(PutObjectArgs.class));
    }
}

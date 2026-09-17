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
import org.springframework.test.util.ReflectionTestUtils;
import pl.edu.pk.pkampus.common.exception.InvalidFileException;

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
        storageService = new MinioStorageService(minioClient);
        ReflectionTestUtils.setField(storageService, "avatarBucket", "pkampus-avatars");
        ReflectionTestUtils.setField(storageService, "issuesBucket", "pkampus-issues");
    }

    @Test
    void shouldUploadAvatarSuccessfullyForValidJpeg() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "avatar.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3, 4}
        );

        String objectName = storageService.uploadAvatar(file);

        assertNotNull(objectName);
        assertTrue(objectName.endsWith(".jpg"));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void shouldUploadAvatarSuccessfullyForValidPng() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "avatar.png",
                "image/png",
                new byte[]{1, 2, 3, 4}
        );

        String objectName = storageService.uploadAvatar(file);

        assertNotNull(objectName);
        assertTrue(objectName.endsWith(".png"));
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void shouldUploadAvatarSuccessfullyForValidWebp() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "avatar.webp",
                "image/webp",
                new byte[]{1, 2, 3, 4}
        );

        String objectName = storageService.uploadAvatar(file);

        assertNotNull(objectName);
        assertTrue(objectName.endsWith(".webp"));
        verify(minioClient).putObject(any(PutObjectArgs.class));
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
}

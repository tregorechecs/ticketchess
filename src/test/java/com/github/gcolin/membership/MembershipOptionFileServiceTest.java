package com.github.gcolin.membership;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.gcolin.platform.Config;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MembershipOptionFileServiceTest {
    @TempDir Path temp;

    private MembershipOptionFileService service() {
        Config config = mock(Config.class);
        when(config.getConfigDir()).thenReturn(temp.toString());
        MembershipOptionFileService service = new MembershipOptionFileService();
        service.setConfig(config);
        return service;
    }

    @Test
    void savesFilesAndGuessesAllContentTypes() throws Exception {
        MembershipOptionFileService service = service();
        for (String extension : new String[]{"pdf", "png", "jpg", "webp", "gif", "txt", "csv", "doc",
                "docx", "xls", "xlsx", "odt", "ods"}) {
            MembershipOptionFileService.StoredFile stored = service.save(3, "dir\\name." + extension,
                    extension.equals("pdf") ? "application/custom" : null,
                    new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)));
            assertEquals("name." + extension, stored.originalName());
            assertEquals(1, stored.sizeBytes());
            assertTrue(Files.exists(temp.resolve("membership-files/3").resolve(stored.storedName())));
            assertNotNull(stored.contentType());
        }
    }

    @Test
    void acceptsBlankWildcardContentTypeAndRejectsInvalidInputs() throws Exception {
        MembershipOptionFileService service = service();
        assertEquals("application/pdf", service.save(1, "a.pdf", " ", new ByteArrayInputStream(new byte[]{1})).contentType());
        assertEquals("image/jpeg", service.save(1, "a.JPG", "*/*", new ByteArrayInputStream(new byte[]{1})).contentType());
        assertThrows(WebApplicationException.class, () -> service.save(null, "a.pdf", null, new ByteArrayInputStream(new byte[]{1})));
        assertThrows(WebApplicationException.class, () -> service.save(1, "a.pdf", null, null));
        assertThrows(WebApplicationException.class, () -> service.save(1, "a.exe", null, new ByteArrayInputStream(new byte[]{1})));
        assertThrows(WebApplicationException.class, () -> service.save(1, " ", null, new ByteArrayInputStream(new byte[]{1})));
        assertThrows(WebApplicationException.class, () -> service.save(1, ".pdf", null, new ByteArrayInputStream(new byte[]{1})));
        assertThrows(WebApplicationException.class, () -> service.save(1, "a.pdf", null, new ByteArrayInputStream(new byte[0])));
    }

    @Test
    void resolvesAndDeletesFiles() throws Exception {
        MembershipOptionFileService service = service();
        MembershipOption option = new MembershipOption();
        option.setId(7);
        MembershipOptionFile file = new MembershipOptionFile();
        file.setMembershipOption(option);
        file.setStoredName("stored.pdf");
        Path path = service.resolveStoredFile(file);
        Files.createDirectories(path.getParent());
        Files.writeString(path, "x");
        service.delete(file);
        assertFalse(Files.exists(path));
        service.delete(null);
        file.setStoredName(null);
        assertThrows(WebApplicationException.class, () -> service.resolveStoredFile(file));
        file.setStoredName("x");
        file.setMembershipOption(null);
        assertThrows(WebApplicationException.class, () -> service.resolveStoredFile(file));
        MembershipOptionFile noId = new MembershipOptionFile();
        noId.setMembershipOption(new MembershipOption());
        noId.setStoredName("x");
        assertThrows(WebApplicationException.class, () -> service.resolveStoredFile(noId));
    }
}

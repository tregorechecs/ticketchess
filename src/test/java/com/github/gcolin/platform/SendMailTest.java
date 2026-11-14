package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.gcolin.auth.LoginMail;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SendMailTest {
    @Test
    void fileModeWritesHtmlAndAttachmentsAndBccBatches() throws Exception {
        File dir = new File("emails");
        if (dir.exists()) {
            for (File file : dir.listFiles()) {
                file.delete();
            }
        }
        SendMail mail = new SendMail();
        Properties properties = new Properties();
        properties.setProperty("mail.USER_NAME", "sender@test");
        mail.setProperties(properties);
        mail.init();
        try {
            LoginMail content = new LoginMail();
            content.setName("Alice");
            content.setLoginUrl("http://localhost/login");
            assertEquals(0, mail.sendBccBatched(content, List.of(), "Empty"));
            mail.send(content, "alice@test", "Hello", new MailAttachment("data.txt", "text/plain", new byte[] {1}));
            assertEquals(31, mail.sendBccBatched(
                    content,
                    java.util.stream.IntStream.range(0, 31).mapToObj(i -> "u" + i + "@test").toList(),
                    "News"));
            assertTrue(dir.exists());
            assertTrue(java.util.Arrays.stream(dir.listFiles())
                    .filter(file -> file.getName().endsWith(".html"))
                    .map(file -> {
                        try {
                            return Files.readString(file.toPath());
                        } catch (Exception e) {
                            return "";
                        }
                    })
                    .anyMatch(text -> text.contains("<html")));
        } finally {
            mail.close();
            for (File file : dir.listFiles()) {
                file.delete();
            }
            dir.delete();
        }
    }

    @Test
    void sendHtmlWritesTheRawBodyWithoutTemplate() throws Exception {
        SendMail mail = new SendMail();
        Properties properties = new Properties();
        properties.setProperty("mail.PASSWORD", "");
        mail.setProperties(properties);
        mail.init();
        File dir = new File("emails");
        try {
            mail.sendHtml("raw@test", "Raw", "<html><head></head><body>plain-body</body></html>");
            assertTrue(java.util.Arrays.stream(dir.listFiles())
                    .map(file -> {
                        try {
                            return Files.readString(file.toPath());
                        } catch (Exception e) {
                            return "";
                        }
                    })
                    .anyMatch(text -> text.contains("plain-body") && text.contains("raw@test - Raw")));
        } finally {
            mail.close();
            if (dir.exists()) {
                for (File file : dir.listFiles()) file.delete();
                dir.delete();
            }
        }
    }

    @Test
    void nullAttachmentUsesSimpleSendPath() throws Exception {
        SendMail mail = new SendMail();
        Properties properties = new Properties();
        properties.setProperty("mail.PASSWORD", "");
        mail.setProperties(properties);
        mail.init();
        try {
            LoginMail content = new LoginMail();
            content.setName("Bob");
            content.setLoginUrl("http://localhost");
            mail.send(content, "bob@test", "Hello", (MailAttachment) null);
            assertTrue(new File("emails").exists());
        } finally {
            mail.close();
            File dir = new File("emails");
            if (dir.exists()) {
                for (File file : dir.listFiles()) file.delete();
                dir.delete();
            }
        }
    }

    /**
     * Simulates multi-org: credentials absent at init (no OrgScope), then present at send time.
     * Must enter SMTP path (no file under ./emails) even if Transport fails asynchronously.
     */
    @Test
    void resolvesCredentialsAtSendTimeNotAtInit() throws Exception {
        File dir = new File("emails");
        if (dir.exists()) {
            File[] existing = dir.listFiles();
            if (existing != null) {
                for (File file : existing) {
                    file.delete();
                }
            }
            dir.delete();
        }
        AtomicReference<String> password = new AtomicReference<>("");
        Properties properties = new Properties() {
            @Override
            public String getProperty(String key) {
                if ("mail.PASSWORD".equals(key)) {
                    return password.get();
                }
                if ("mail.USER_NAME".equals(key)) {
                    return "sender@test";
                }
                return super.getProperty(key);
            }
        };
        SendMail mail = new SendMail();
        mail.setProperties(properties);
        mail.init();
        mail.reloadCredentials();
        password.set("app-password-from-org");
        try {
            mail.sendHtml("user@test", "Subject", "<html><head></head><body>hi</body></html>");
            assertFalse(dir.exists(), "SMTP path must not fall back to ./emails when password is set at send time");
        } finally {
            mail.close();
            if (dir.exists()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        file.delete();
                    }
                }
                dir.delete();
            }
        }
    }
}

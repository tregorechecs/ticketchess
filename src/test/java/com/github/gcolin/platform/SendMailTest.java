package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.gcolin.auth.LoginMail;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;
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
}

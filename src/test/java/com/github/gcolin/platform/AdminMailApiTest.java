package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.jsonwebtoken.security.Keys;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Field;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminMailApiTest {
    private AdminMailApi api;
    private SendMail sendMail;
    private Config config;

    @BeforeEach
    void setUp() throws Exception {
        api = new AdminMailApi();
        sendMail = mock(SendMail.class);
        config = mock(Config.class);
        SecretKey key = Keys.hmacShaKeyFor(
                "integration-test-jwt-key-not-for-production-use-only-pad".getBytes());
        when(config.getKeys()).thenReturn(key);
        when(config.getPublicBaseUrl()).thenReturn("http://localhost:8080");
        doNothing().when(config).applyOrg(any());
        inject(api, "sendMail", sendMail);
        inject(api, "config", config);
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object[] args(String template) {
        return new Object[]{"person@example.com", template, "Person", "Event", "2026-01-01",
            "10", "REF", "http://example.test", "/event/my"};
    }

    @Test
    void showFormExposesAllTemplates() {
        MustacheHtml html = api.showForm();
        assertEquals("platform/adminMail.mustache", html.getTemplate());
        assertEquals(4, ((String[]) html.getModel().get("templates")).length);
    }

    @Test
    void previewsAllTemplatesAndUnknown() {
        for (String template : List.of("registrationConfirmation", "paymentConfirmation",
                "cancelConfirmation", "loginLink")) {
            Response response = api.previewMailForm(args(template)[0].toString(), template, "Person",
                    "Event", "2026", "10", "REF", "http://example.test", "/event/my");
            assertEquals(200, response.getStatus(), template);
        }
        assertEquals(400, api.previewMailForm("x@y", "unknown", "n", "e", null, null, null, null, null).getStatus());
        assertEquals(400, api.previewMailForm("", "loginLink", null, null, null, null, null, null, null).getStatus());
    }

    @Test
    void sendsEachTemplateAndHandlesErrors() throws Exception {
        for (String template : List.of("registrationConfirmation", "paymentConfirmation",
                "cancelConfirmation", "loginLink")) {
            Response response = api.sendMailForm("person@example.com", "Subject", template, "Person",
                    "Event", "2026", "10", "REF", "http://example.test", "/event/my");
            assertEquals(200, response.getStatus(), template);
        }
        assertEquals(400, api.sendMailForm("", "s", "loginLink", null, null, null, null, null, null, null).getStatus());
        assertEquals(400, api.sendMailForm("x@y", "s", "unknown", null, null, null, null, null, null, null).getStatus());
        doThrow(new Exception("mail down")).when(sendMail).send(any(), anyString(), anyString());
        assertEquals(500, api.sendMailForm("x@y", "s", "loginLink", "N", null, null, null, null, null, null).getStatus());
    }
}

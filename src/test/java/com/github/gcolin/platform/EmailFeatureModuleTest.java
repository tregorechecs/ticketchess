package com.github.gcolin.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.github.gcolin.organisation.Organisation;
import com.github.gcolin.organisation.OrgConfig;
import com.github.gcolin.platform.plugin.MailSender;
import java.util.List;
import org.junit.jupiter.api.Test;

class EmailFeatureModuleTest {

    @Test
    void adapterDelegatesToSendMailWhenEmailPluginIsEnabled() throws Exception {
        SendMail sendMail = mock(SendMail.class);
        MailSender sender = EmailFeatureModule.orgGated(sendMail);
        try (TestContext ignored = TestContext.openRequest(ctx -> ctx.setOrganisation(new Organisation()))) {
            sender.send("a@b.test", "Hi", "<p>x</p>");
        }
        verify(sendMail).sendHtml("a@b.test", "Hi", "<p>x</p>");
    }

    @Test
    void adapterIsNoopWhenOrganisationDisabledEmail() throws Exception {
        SendMail sendMail = mock(SendMail.class);
        MailSender sender = EmailFeatureModule.orgGated(sendMail);
        Organisation org = new Organisation();
        new OrgConfig(org).setPlugins(List.of("events"));
        try (TestContext ignored = TestContext.openRequest(ctx -> ctx.setOrganisation(org))) {
            sender.send("a@b.test", "Hi", "<p>x</p>");
        }
        verify(sendMail, never()).sendHtml("a@b.test", "Hi", "<p>x</p>");
    }

    @Test
    void adapterSendsOutsideOfAnyRequest() throws Exception {
        SendMail sendMail = mock(SendMail.class);
        EmailFeatureModule.orgGated(sendMail).send("a@b.test", "Hi", "b");
        verify(sendMail).sendHtml("a@b.test", "Hi", "b");
    }

    @Test
    void moduleDescribesItself() {
        EmailFeatureModule module = new EmailFeatureModule();
        assertEquals("email", module.id());
        assertEquals("plugin.email", module.labelKey());
        assertTrue(module.pathPrefixes().contains("/admin/mail"));
    }
}
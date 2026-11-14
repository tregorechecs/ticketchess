package com.github.gcolin.platform;

import com.github.gcolin.platform.plugin.AbstractFeatureModule;
import com.github.gcolin.platform.plugin.MailSender;
import com.github.gcolin.platform.plugin.ModuleContext;
import com.github.gcolin.platform.plugin.OrgPlugins;
import com.github.gcolin.platform.plugin.PluginIds;
import java.util.Collection;
import java.util.List;

/** Owns the {@link SendMail} singleton and exposes it to other plugins as a {@link MailSender}. */
public class EmailFeatureModule extends AbstractFeatureModule {

    public EmailFeatureModule() {
        super(PluginIds.EMAIL, "plugin.email");
    }

    @Override
    public Collection<String> pathPrefixes() {
        return List.of("/admin/mail");
    }

    @Override
    public void onStart(ModuleContext ctx) {
        SendMail sendMail = new SendMail();
        sendMail.setProperties(ctx.config().getProperties());
        sendMail.setConfig(ctx.config());
        sendMail.init();
        AppContext.get().setSendMail(sendMail);
        ctx.hookBus().setMailSender(orgGated(sendMail));
    }

    /** No-op for organisations that disabled the email plugin. */
    static MailSender orgGated(SendMail sendMail) {
        return (to, subject, body) -> {
            if (OrgPlugins.isEnabled(PluginIds.EMAIL)) {
                sendMail.sendHtml(to, subject, body);
            }
        };
    }
}

package com.github.gcolin.platform;

/** Sends templated mail via injected {@link SendMail} (tests) or {@link com.github.gcolin.platform.plugin.HookBus}. */
public final class Mails {

    private Mails() {}

    public static void send(SendMail fieldOverride, AbstractMail amail, String to, String subject) throws Exception {
        if (fieldOverride != null) {
            fieldOverride.send(amail, to, subject);
            return;
        }
        AppContext app = AppContext.get();
        Config cfg = app.config();
        if (cfg != null) {
            cfg.applyOrg(amail);
        }
        SendMail renderer = app.sendMail();
        String html = renderer != null ? new MailTemplate().render(amail.getTemplate(), amail) : "";
        app.hookBus().mailSender().send(to, subject, html);
    }
}

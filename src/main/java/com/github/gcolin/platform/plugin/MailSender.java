package com.github.gcolin.platform.plugin;

@FunctionalInterface
public interface MailSender {

    void send(String to, String subject, String htmlOrText) throws Exception;

    MailSender NOOP = (to, subject, body) -> {};
}

package com.github.gcolin.platform.plugin;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Extension points shared between plugins. Empty by default; plugins fill it in {@code registerHooks}. */
public final class HookBus {

    private volatile MailSender mailSender = MailSender.NOOP;
    private final List<PlayerSearchContributor> playerSearchContributors = new CopyOnWriteArrayList<>();
    private final List<RegistrationPlayerSource> registrationPlayerSources = new CopyOnWriteArrayList<>();
    private final List<Payable> payables = new CopyOnWriteArrayList<>();
    private final List<PaymentCompletedListener> paymentCompletedListeners = new CopyOnWriteArrayList<>();

    public MailSender mailSender() {
        return mailSender;
    }

    public void setMailSender(MailSender mailSender) {
        this.mailSender = mailSender == null ? MailSender.NOOP : mailSender;
    }

    public void addPlayerSearchContributor(PlayerSearchContributor contributor) {
        playerSearchContributors.add(contributor);
    }

    public List<PlayerSearchContributor> playerSearchContributors() {
        return List.copyOf(playerSearchContributors);
    }

    public void addRegistrationPlayerSource(RegistrationPlayerSource source) {
        registrationPlayerSources.add(source);
    }

    public List<RegistrationPlayerSource> registrationPlayerSources() {
        return List.copyOf(registrationPlayerSources);
    }

    public void addPayable(Payable payable) {
        payables.add(payable);
    }

    public List<Payable> payables() {
        return List.copyOf(payables);
    }

    public void addPaymentCompletedListener(PaymentCompletedListener listener) {
        paymentCompletedListeners.add(listener);
    }

    public void firePaymentCompleted(String kind, Integer paymentId) {
        for (PaymentCompletedListener listener : paymentCompletedListeners) {
            listener.onPaymentCompleted(kind, paymentId);
        }
    }
}

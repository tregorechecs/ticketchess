package com.github.gcolin.platform.plugin;

@FunctionalInterface
public interface PaymentCompletedListener {

    void onPaymentCompleted(String kind, Integer paymentId);
}

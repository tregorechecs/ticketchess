package com.github.gcolin.payment;

/** Stripe API or transport failure. */
public class StripeException extends Exception {

    private final int statusCode;

    public StripeException(String message) {
        this(message, 0, null);
    }

    public StripeException(String message, Throwable cause) {
        this(message, 0, cause);
    }

    public StripeException(String message, int statusCode) {
        this(message, statusCode, null);
    }

    public StripeException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}

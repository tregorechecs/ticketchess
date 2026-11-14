package com.github.gcolin.platform.plugin;

/** Something payments can be attached to (events, memberships, ...). */
public interface Payable {

    /** Stable kind identifier, e.g. {@code "event"} or {@code "membership"}. */
    String kind();
}

package com.github.gcolin.platform.plugin;

import java.util.List;

/** Identifiers of the built-in plugins, in startup order. */
public final class PluginIds {

    public static final String EMAIL = "email";
    public static final String EVENTS = "events";
    public static final String MEMBERSHIP = "membership";
    public static final String PAYMENTS = "payments";
    public static final String CUSTOM_PLAYER = "custom-player";
    public static final String PLAYER_INDEX = "player-index";
    public static final String DESK = "desk";

    public static final List<String> KNOWN =
            List.of(EMAIL, PLAYER_INDEX, CUSTOM_PLAYER, EVENTS, MEMBERSHIP, PAYMENTS, DESK);

    private PluginIds() {}
}

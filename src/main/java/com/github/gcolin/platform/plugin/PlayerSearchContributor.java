package com.github.gcolin.platform.plugin;

import com.github.gcolin.event.EventType;
import com.github.gcolin.player.IPlayer;

/** Resolves player references the federation index cannot (e.g. {@code "@12"} for custom players). */
public interface PlayerSearchContributor {

    /** The player behind {@code ref}, or {@code null} when this contributor does not own the reference. */
    IPlayer find(String ref, EventType eventType);
}

package com.github.gcolin.platform.plugin;

/** Entry of the admin hub contributed by a plugin; shown only when {@code requiredPluginId} is enabled. */
public record AdminTile(String id, String labelKey, String href, String requiredPluginId) {}

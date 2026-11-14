package com.github.gcolin.platform.plugin;

import java.util.Collection;
import java.util.List;
import org.glassfish.jersey.server.ResourceConfig;

/** A feature of the application that can be enabled per organisation. */
public interface FeatureModule {

    String id();

    String labelKey();

    default void registerResources(ResourceConfig config, ModuleContext ctx) {}

    default Collection<AdminTile> adminTiles() {
        return List.of();
    }

    default void contributeSchema(SchemaBag ddl) {}

    default void registerHooks(HookBus bus) {}

    default void onStart(ModuleContext ctx) {}

    /**
     * Path prefixes this plugin owns (for org gating), e.g. {@code "/event"}, {@code "/customplayer"}.
     * A segment made of a single asterisk matches any one path segment (event desk: event, asterisk, desk).
     */
    default Collection<String> pathPrefixes() {
        return List.of();
    }
}

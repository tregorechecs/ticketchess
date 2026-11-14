package com.github.gcolin.platform.plugin;

import org.glassfish.jersey.server.ResourceConfig;

public abstract class AbstractFeatureModule implements FeatureModule {

    private final String id;
    private final String labelKey;

    protected AbstractFeatureModule(String id, String labelKey) {
        this.id = id;
        this.labelKey = labelKey;
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final String labelKey() {
        return labelKey;
    }

    protected static void registerAll(ResourceConfig config, Class<?>... types) {
        for (Class<?> t : types) {
            config.register(t);
        }
    }
}

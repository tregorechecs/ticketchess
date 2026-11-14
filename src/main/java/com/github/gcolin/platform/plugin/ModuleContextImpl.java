package com.github.gcolin.platform.plugin;

import com.github.gcolin.platform.AppContext;
import com.github.gcolin.platform.Caches;
import com.github.gcolin.platform.Config;
import com.github.gcolin.platform.MustacheConfig;
import javax.sql.DataSource;

public final class ModuleContextImpl implements ModuleContext {

    private final AppContext app;

    public ModuleContextImpl(AppContext app) {
        this.app = app;
    }

    @Override
    public Config config() {
        return app.config();
    }

    @Override
    public DataSource dataSource() {
        return app.persistence() == null ? null : app.persistence().getDataSource();
    }

    @Override
    public Caches caches() {
        return app.caches();
    }

    @Override
    public MustacheConfig mustache() {
        return app.mustacheConfig();
    }

    @Override
    public HookBus hookBus() {
        return app.hookBus();
    }

    @Override
    public PluginRegistry plugins() {
        return app.pluginRegistry();
    }
}

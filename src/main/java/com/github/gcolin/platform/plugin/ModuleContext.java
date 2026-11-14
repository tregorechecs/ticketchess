package com.github.gcolin.platform.plugin;

import com.github.gcolin.platform.Caches;
import com.github.gcolin.platform.Config;
import com.github.gcolin.platform.MustacheConfig;
import com.github.gcolin.platform.RequestContext;
import java.util.function.Supplier;
import javax.sql.DataSource;

/** What a {@link FeatureModule} may use from the application. */
public interface ModuleContext {

    Config config();

    DataSource dataSource();

    Caches caches();

    MustacheConfig mustache();

    HookBus hookBus();

    PluginRegistry plugins();

    default Supplier<RequestContext> requestContext() {
        return RequestContext::current;
    }
}

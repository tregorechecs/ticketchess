package com.github.gcolin.desk;

import com.github.gcolin.platform.AppContext;
import com.github.gcolin.platform.plugin.AbstractFeatureModule;
import com.github.gcolin.platform.plugin.ModuleContext;
import com.github.gcolin.platform.plugin.PluginIds;
import java.util.Collection;
import java.util.List;
import org.glassfish.jersey.server.ResourceConfig;

/** Live event desk (attendance / payments at the venue). Routes live under {@code /event/{id}/desk}. */
public class DeskFeatureModule extends AbstractFeatureModule {

    public DeskFeatureModule() {
        super(PluginIds.DESK, "plugin.desk");
    }

    @Override
    public void registerResources(ResourceConfig config, ModuleContext ctx) {
        registerAll(config, EventDeskApi.class);
    }

    @Override
    public void onStart(ModuleContext ctx) {
        AppContext app = AppContext.get();
        EventDeskService service = new EventDeskService();
        service.setDataSource(ctx.dataSource());
        service.setCaches(ctx.caches());
        app.setEventDeskHub(new EventDeskHub());
        app.setEventDeskService(service);
    }

    @Override
    public Collection<String> pathPrefixes() {
        return List.of("/event/*/desk", "/ws/event/*/desk");
    }
}

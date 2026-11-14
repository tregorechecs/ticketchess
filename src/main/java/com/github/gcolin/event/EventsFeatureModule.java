package com.github.gcolin.event;

import com.github.gcolin.platform.plugin.AbstractFeatureModule;
import com.github.gcolin.platform.plugin.AdminTile;
import com.github.gcolin.platform.plugin.HookBus;
import com.github.gcolin.platform.plugin.ModuleContext;
import com.github.gcolin.platform.plugin.PluginIds;
import com.github.gcolin.registration.EventRegisterApi;
import com.github.gcolin.registration.PlayerSubscriptionAdminApi;
import com.github.gcolin.registration.PlayerSubscriptionOptionApi;
import java.util.Collection;
import java.util.List;
import org.glassfish.jersey.server.ResourceConfig;

public class EventsFeatureModule extends AbstractFeatureModule {

    public EventsFeatureModule() {
        super(PluginIds.EVENTS, "plugin.events");
    }

    @Override
    public void registerResources(ResourceConfig config, ModuleContext ctx) {
        registerAll(
                config,
                EventApi.class,
                EventAdminApi.class,
                EventCollectionApi.class,
                EventCollectionMailApi.class,
                EventCollectionStatisticsApi.class,
                EventCsvApi.class,
                EventGroupApi.class,
                EventLicenceCheckApi.class,
                EventMailApi.class,
                EventStatisticsApi.class,
                EventPapiApi.class,
                EventRegisterApi.class,
                PlayerSubscriptionAdminApi.class,
                PlayerSubscriptionOptionApi.class,
                EventsApi.class,
                MyEventsApi.class,
                StatisticsApi.class);
    }

    @Override
    public void registerHooks(HookBus bus) {
        bus.addPayable(() -> "event");
    }

    @Override
    public Collection<AdminTile> adminTiles() {
        return List.of(new AdminTile("events", "admin.features.events", "/admin/events", PluginIds.EVENTS));
    }

    @Override
    public Collection<String> pathPrefixes() {
        return List.of(
                "/event",
                "/events",
                "/myevents",
                "/eventcollection",
                "/eventgroup",
                "/admin/events",
                "/playersubscription",
                "/dashboard");
    }
}

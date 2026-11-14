package com.github.gcolin.player;

import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.plugin.AbstractFeatureModule;
import com.github.gcolin.platform.plugin.HookBus;
import com.github.gcolin.platform.plugin.ModuleContext;
import com.github.gcolin.platform.plugin.OrgPlugins;
import com.github.gcolin.platform.plugin.PluginIds;
import java.util.Collection;
import java.util.List;
import org.glassfish.jersey.server.ResourceConfig;

/** Players typed in by hand and referenced as {@code @id}. */
public class CustomPlayerFeatureModule extends AbstractFeatureModule {

    public CustomPlayerFeatureModule() {
        super(PluginIds.CUSTOM_PLAYER, "plugin.customPlayer");
    }

    @Override
    public void registerResources(ResourceConfig config, ModuleContext ctx) {
        registerAll(config, CustomPlayerApi.class);
    }

    @Override
    public void registerHooks(HookBus bus) {
        bus.addPlayerSearchContributor((ref, eventType) -> {
            if (ref == null || !ref.startsWith("@") || !OrgPlugins.isEnabled(PluginIds.CUSTOM_PLAYER)) {
                return null;
            }
            return RequestContext.require().find().resolveCustomPlayerWithoutHooks(ref, eventType);
        });
        bus.addRegistrationPlayerSource(email -> {
            if (!OrgPlugins.isEnabled(PluginIds.CUSTOM_PLAYER)) {
                return List.of();
            }
            return RequestContext.require().customPlayerDao().findByCreationUser(email);
        });
    }

    @Override
    public Collection<String> pathPrefixes() {
        return List.of("/customplayer");
    }
}

package com.github.gcolin.membership;

import com.github.gcolin.club.ClubSeasonAdminApi;
import com.github.gcolin.platform.plugin.AbstractFeatureModule;
import com.github.gcolin.platform.plugin.AdminTile;
import com.github.gcolin.platform.plugin.HookBus;
import com.github.gcolin.platform.plugin.ModuleContext;
import com.github.gcolin.platform.plugin.PluginIds;
import com.github.gcolin.registration.ClubRegisterApi;
import java.util.Collection;
import java.util.List;
import org.glassfish.jersey.server.ResourceConfig;

public class MembershipFeatureModule extends AbstractFeatureModule {

    public MembershipFeatureModule() {
        super(PluginIds.MEMBERSHIP, "plugin.membership");
    }

    @Override
    public void registerResources(ResourceConfig config, ModuleContext ctx) {
        registerAll(
                config,
                MembershipApi.class,
                MembershipOptionAdminApi.class,
                MembershipOptionFileAdminApi.class,
                LicenseAdminApi.class,
                ClubRegisterApi.class,
                ClubSeasonAdminApi.class);
    }

    @Override
    public void registerHooks(HookBus bus) {
        bus.addPayable(() -> "membership");
    }

    @Override
    public Collection<AdminTile> adminTiles() {
        return List.of(new AdminTile("licenses", "admin.features.memberships", "/license-admin", PluginIds.MEMBERSHIP));
    }

    @Override
    public Collection<String> pathPrefixes() {
        return List.of("/club-register", "/membership", "/membership-option", "/license-admin", "/license");
    }
}

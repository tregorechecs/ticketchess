package com.github.gcolin.payment;

import com.github.gcolin.platform.plugin.AbstractFeatureModule;
import com.github.gcolin.platform.plugin.ModuleContext;
import com.github.gcolin.platform.plugin.PluginIds;
import java.util.Collection;
import java.util.List;
import org.glassfish.jersey.server.ResourceConfig;

public class PaymentsFeatureModule extends AbstractFeatureModule {

    public PaymentsFeatureModule() {
        super(PluginIds.PAYMENTS, "plugin.payments");
    }

    @Override
    public void registerResources(ResourceConfig config, ModuleContext ctx) {
        registerAll(config, PaymentApi.class, RibApi.class);
    }

    @Override
    public Collection<String> pathPrefixes() {
        return List.of("/payment");
    }
}

package com.github.gcolin.player;

import com.github.gcolin.platform.AppContext;
import com.github.gcolin.platform.plugin.AbstractFeatureModule;
import com.github.gcolin.platform.plugin.ModuleContext;
import com.github.gcolin.platform.plugin.PluginIds;
import java.util.Collection;
import java.util.List;
import org.glassfish.jersey.server.ResourceConfig;

/** FFE / FIDE player index (one shared LuceneDb for all organisations) and its pages. */
public class PlayerIndexFeatureModule extends AbstractFeatureModule {

    public PlayerIndexFeatureModule() {
        super(PluginIds.PLAYER_INDEX, "plugin.playerIndex");
    }

    @Override
    public void registerResources(ResourceConfig config, ModuleContext ctx) {
        registerAll(config, PlayerApi.class, ClubApi.class, DatabaseApi.class);
    }

    @Override
    public void onStart(ModuleContext ctx) {
        LuceneDb luceneDb = new LuceneDb();
        luceneDb.setConfig(ctx.config());
        luceneDb.init();
        AppContext.get().setLuceneDb(luceneDb);
    }

    @Override
    public Collection<String> pathPrefixes() {
        return List.of("/player", "/club", "/database");
    }
}

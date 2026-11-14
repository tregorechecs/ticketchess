package com.github.gcolin.platform;

import com.github.gcolin.auth.AuthSim;
import com.github.gcolin.auth.LogAsApi;
import com.github.gcolin.auth.LoggedOnlyFilter;
import com.github.gcolin.auth.LoggedUserTrackerFilter;
import com.github.gcolin.auth.LoggedUsersApi;
import com.github.gcolin.auth.LoginApi;
import com.github.gcolin.auth.LogoutApi;
import com.github.gcolin.auth.OauthCallbackApi;
import com.github.gcolin.auth.RequireRoleFeature;
import com.github.gcolin.auth.UserAuthorizationApi;
import com.github.gcolin.organisation.OrganisationAdminApi;
import com.github.gcolin.platform.plugin.ModuleContextImpl;
import com.github.gcolin.platform.plugin.PluginRegistry;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jersey application: always-on core resources, then the resources of every {@link
 * com.github.gcolin.platform.plugin.FeatureModule}. Instantiated by the servlet filter after
 * {@link ApplicationBootstrapListener} initialised {@link AppContext}.
 */
public class TicketChessApplication extends ResourceConfig {

    private static final Logger logger = LoggerFactory.getLogger(TicketChessApplication.class);

    public TicketChessApplication() {
        registerCore();
        AppContext app = currentApp();
        if (app == null) {
            logger.warn("AppContext not initialised: only core resources registered");
            return;
        }
        PluginRegistry registry = app.pluginRegistry();
        if (registry == null) {
            registry = PluginRegistry.load();
            registry.install(app);
        }
        registry.registerResources(this, new ModuleContextImpl(app));
    }

    private void registerCore() {
        register(ReadOnlyPersistenceFilter.class);
        register(MustacheConfig.class);
        register(MustacheHtml.class);
        register(MustacheMessageBodyWriter.class);
        register(LoggedUserTrackerFilter.class);
        register(UriInfoCaptureFilter.class);
        register(RequireRoleFeature.class);
        register(LoggedOnlyFilter.class);
        register(AuthSim.class);
        register(HomeApi.class);
        register(MentionApi.class);
        register(AdminApi.class);
        register(AdminMailApi.class);
        register(DashboardApi.class);
        register(LogoApi.class);
        register(BackgroundApi.class);
        register(SignatureApi.class);
        register(OrganisationAdminApi.class);
        register(LoginApi.class);
        register(LogoutApi.class);
        register(LogAsApi.class);
        register(LoggedUsersApi.class);
        register(OauthCallbackApi.class);
        register(UserAuthorizationApi.class);
    }

    private static AppContext currentApp() {
        try {
            return AppContext.get();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}

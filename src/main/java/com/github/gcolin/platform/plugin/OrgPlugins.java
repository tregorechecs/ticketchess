package com.github.gcolin.platform.plugin;

import com.github.gcolin.organisation.Organisation;
import com.github.gcolin.organisation.OrgConfig;
import com.github.gcolin.organisation.OrgScope;

/** Plugin switches of the organisation serving the current request. */
public final class OrgPlugins {

    private OrgPlugins() {}

    /** True outside a request (no organisation in scope) and when the organisation enabled the plugin. */
    public static boolean isEnabled(String pluginId) {
        Organisation org = OrgScope.currentOrg();
        return org == null || new OrgConfig(org).isPluginEnabled(pluginId);
    }
}

package com.github.gcolin.platform.plugin;

import com.github.gcolin.desk.DeskFeatureModule;
import com.github.gcolin.event.EventsFeatureModule;
import com.github.gcolin.membership.MembershipFeatureModule;
import com.github.gcolin.payment.PaymentsFeatureModule;
import com.github.gcolin.platform.AppContext;
import com.github.gcolin.platform.EmailFeatureModule;
import com.github.gcolin.player.CustomPlayerFeatureModule;
import com.github.gcolin.player.PlayerIndexFeatureModule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The {@link FeatureModule}s of this process and the path prefixes each one owns. */
public final class PluginRegistry {

    private static final Logger logger = LoggerFactory.getLogger(PluginRegistry.class);

    private final Map<String, FeatureModule> modules = new LinkedHashMap<>();
    private final Map<String, List<String>> pathPrefixes = new LinkedHashMap<>();

    private PluginRegistry() {}

    /** Built-in modules first (in startup order), then any other module found through {@link ServiceLoader}. */
    public static PluginRegistry load() {
        PluginRegistry registry = new PluginRegistry();
        for (FeatureModule module : knownModules()) {
            registry.add(module);
        }
        for (FeatureModule module : ServiceLoader.load(FeatureModule.class)) {
            registry.add(module);
        }
        return registry;
    }

    public static PluginRegistry of(Collection<? extends FeatureModule> modules) {
        PluginRegistry registry = new PluginRegistry();
        for (FeatureModule module : modules) {
            registry.add(module);
        }
        return registry;
    }

    private static List<FeatureModule> knownModules() {
        return List.of(
                new EmailFeatureModule(),
                new PlayerIndexFeatureModule(),
                new CustomPlayerFeatureModule(),
                new EventsFeatureModule(),
                new MembershipFeatureModule(),
                new PaymentsFeatureModule(),
                new DeskFeatureModule());
    }

    private void add(FeatureModule module) {
        if (modules.containsKey(module.id())) {
            return;
        }
        modules.put(module.id(), module);
        pathPrefixes.put(module.id(), List.copyOf(module.pathPrefixes()));
    }

    public List<FeatureModule> modules() {
        return List.copyOf(modules.values());
    }

    public FeatureModule get(String id) {
        return modules.get(id);
    }

    public List<String> ids() {
        return List.copyOf(modules.keySet());
    }

    public void registerResources(ResourceConfig config, ModuleContext ctx) {
        for (FeatureModule module : modules.values()) {
            module.registerResources(config, ctx);
            pathPrefixes.put(module.id(), List.copyOf(module.pathPrefixes()));
            logger.info("Plugin {} registered", module.id());
        }
    }

    public void install(AppContext app) {
        app.setPluginRegistry(this);
    }

    public Map<String, List<String>> pathPrefixes() {
        return Map.copyOf(pathPrefixes);
    }

    /** Admin hub tiles of the enabled plugins. */
    public List<AdminTile> adminTiles(Set<String> enabledPluginIds) {
        List<AdminTile> tiles = new ArrayList<>();
        for (FeatureModule module : modules.values()) {
            for (AdminTile tile : module.adminTiles()) {
                if (tile.requiredPluginId() == null || enabledPluginIds.contains(tile.requiredPluginId())) {
                    tiles.add(tile);
                }
            }
        }
        return tiles;
    }

    /**
     * A path is owned by the plugin with the longest matching prefix; it is allowed unless that plugin is
     * not in {@code enabledPluginIds}. Paths owned by no plugin are always allowed.
     */
    public boolean isPathAllowed(String path, Set<String> enabledPluginIds) {
        if (path == null) {
            return true;
        }
        String owner = null;
        int ownerLength = -1;
        String[] segments = segments(path);
        for (Map.Entry<String, List<String>> entry : pathPrefixes.entrySet()) {
            for (String prefix : entry.getValue()) {
                if (prefix.length() > ownerLength && matches(segments, segments(prefix))) {
                    owner = entry.getKey();
                    ownerLength = prefix.length();
                }
            }
        }
        return owner == null || enabledPluginIds.contains(owner);
    }

    private static String[] segments(String path) {
        int query = path.indexOf('?');
        String clean = query >= 0 ? path.substring(0, query) : path;
        if (clean.startsWith("/")) {
            clean = clean.substring(1);
        }
        return clean.split("/");
    }

    /** Every prefix segment matches ({@code -} may continue the last one), the path may go deeper. */
    private static boolean matches(String[] path, String[] prefix) {
        if (path.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            boolean last = i == prefix.length - 1;
            if ("*".equals(prefix[i])) {
                if (path[i].isEmpty()) {
                    return false;
                }
            } else if (!path[i].equals(prefix[i]) && !(last && path[i].startsWith(prefix[i] + "-"))) {
                return false;
            }
        }
        return true;
    }
}

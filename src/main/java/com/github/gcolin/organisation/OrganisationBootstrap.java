package com.github.gcolin.organisation;

import com.github.gcolin.platform.AppContext;
import com.github.gcolin.platform.Config;
import com.github.gcolin.platform.PropertiesFileUpdater;
import com.github.gcolin.platform.PersistenceService;
import com.github.gcolin.platform.RequestContext;
import javax.sql.DataSource;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ensures at least one {@link Organisation} exists and moves club / invoice / stripe (and mail) settings
 * from {@code params.properties} into the organisation row. Logo/RIB/signature/background stay on disk
 * under {@code CONFIG_DIR/orgs/{id}} (see {@link OrganisationFiles}).
 */
public class OrganisationBootstrap {

    private static final Logger logger = LoggerFactory.getLogger(OrganisationBootstrap.class);

    private final Config config;
    private final DataSource dataSource;

    public OrganisationBootstrap(Config config, DataSource dataSource) {
        this.config = config;
        this.dataSource = dataSource;
    }

    public OrganisationBootstrap(Config config, PersistenceService persistence) {
        this(config, persistence.getDataSource());
    }

    public void ensure() {
        Set<String> migrated = RequestContext.run(dataSource, () -> {
            OrganisationDao dao = new OrganisationDao();
            List<Organisation> orgs = dao.allOrdered();
            Set<String> keys = new LinkedHashSet<>();
            if (orgs.isEmpty()) {
                Organisation created = OrgConfig.seed(platformProperties());
                dao.persist(created);
                keys.addAll(collectMigratedKeys(platformProperties()));
                logger.info(
                        "Created default organisation id={} baseUrl={} from params.properties (club/invoice/stripe/mail)",
                        created.getId(),
                        created.getBaseUrl());
            } else {
                Organisation target = orgs.get(0);
                keys.addAll(backfillFromPlatform(target));
                if (!keys.isEmpty()) {
                    dao.merge(target);
                    logger.info(
                            "Migrated {} club/invoice/stripe/mail keys from params.properties into organisation id={}",
                            keys.size(),
                            target.getId());
                }
            }
            return keys;
        });
        if (!migrated.isEmpty()) {
            purgePlatformKeys(migrated);
        }
        invalidateResolver();
    }

    /** Copies platform club/invoice/stripe/mail values into {@code org} when the org value is blank. */
    Set<String> backfillFromPlatform(Organisation org) {
        Properties platform = platformProperties();
        OrgConfig orgConfig = new OrgConfig(org);
        Set<String> migrated = new LinkedHashSet<>();
        for (String key : platform.stringPropertyNames()) {
            if (!OrgConfig.isClubInvoiceStripeSettingsKey(key)) {
                continue;
            }
            String platformValue = platform.getProperty(key);
            if (platformValue == null || platformValue.isBlank()) {
                continue;
            }
            if (orgConfig.isExplicitlySet(key)) {
                migrated.add(key); // already in DB; still purge duplicate from params
                continue;
            }
            orgConfig.setProperty(key, platformValue);
            migrated.add(key);
        }
        return migrated;
    }

    private static Set<String> collectMigratedKeys(Properties platform) {
        Set<String> keys = new LinkedHashSet<>();
        for (String key : platform.stringPropertyNames()) {
            if (OrgConfig.isClubInvoiceStripeSettingsKey(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private Properties platformProperties() {
        Properties raw = new Properties();
        for (String key : config.getProperties().stringPropertyNames()) {
            String value = config.platformProperty(key);
            if (value != null) {
                raw.setProperty(key, value);
            }
        }
        return raw;
    }

    private void purgePlatformKeys(Set<String> keys) {
        for (String key : keys) {
            config.getProperties().remove(key);
        }
        try {
            PropertiesFileUpdater.removeKeys(config.getConfigFile(), keys);
            logger.info("Removed {} migrated keys from {}", keys.size(), config.getConfigFile());
        } catch (IOException e) {
            logger.warn("Could not purge migrated keys from {}", config.getConfigFile(), e);
        }
    }

    private static void invalidateResolver() {
        try {
            AppContext app = AppContext.get();
            if (app != null && app.organisationResolver() != null) {
                app.organisationResolver().invalidate();
            }
        } catch (RuntimeException ignored) {
            // AppContext may not be fully published yet during init
        }
    }
}

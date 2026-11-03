package com.github.gcolin.organisation;

import com.github.gcolin.platform.AppContext;
import com.github.gcolin.platform.Config;
import com.github.gcolin.platform.PropertiesFileUpdater;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
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
    private final EntityManagerFactory emf;

    public OrganisationBootstrap(Config config, EntityManagerFactory emf) {
        this.config = config;
        this.emf = emf;
    }

    public void ensure() {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            List<Organisation> orgs =
                    em.createQuery("SELECT o FROM Organisation o ORDER BY o.id", Organisation.class).getResultList();
            Set<String> migrated = new LinkedHashSet<>();
            if (orgs.isEmpty()) {
                Organisation created = OrgConfig.seed(platformProperties());
                em.persist(created);
                em.flush();
                migrated.addAll(collectMigratedKeys(platformProperties()));
                logger.info(
                        "Created default organisation id={} slug={} from params.properties (club/invoice/stripe/mail)",
                        created.getId(),
                        created.getSlug());
            } else {
                Organisation target = orgs.get(0);
                migrated.addAll(backfillFromPlatform(target));
                if (!migrated.isEmpty()) {
                    logger.info(
                            "Migrated {} club/invoice/stripe/mail keys from params.properties into organisation id={}",
                            migrated.size(),
                            target.getId());
                }
            }
            em.getTransaction().commit();
            if (!migrated.isEmpty()) {
                purgePlatformKeys(migrated);
            }
            invalidateResolver();
        } catch (RuntimeException e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw e;
        } finally {
            em.close();
        }
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

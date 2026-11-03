package com.github.gcolin.organisation;

import com.github.gcolin.platform.AbstractDao;
import java.util.List;
import java.util.Locale;

public class OrganisationDao extends AbstractDao<Organisation> {

    public OrganisationDao() {
        super(Organisation.class);
    }

    public Organisation findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        return em.createQuery("SELECT o FROM Organisation o WHERE o.slug = :slug", Organisation.class)
                .setParameter("slug", slug.trim().toLowerCase(Locale.ROOT))
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    public Organisation findByBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        return em.createQuery("SELECT o FROM Organisation o WHERE o.baseUrl = :baseUrl", Organisation.class)
                .setParameter("baseUrl", OrgConfig.normalizeBaseUrl(baseUrl))
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    /** Matches {@code host} or {@code host:port} against the host of each organisation base URL. */
    public Organisation findByHost(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        String wanted = host.trim().toLowerCase(Locale.ROOT);
        String wantedNoPort = stripPort(wanted);
        Organisation hostOnly = null;
        for (Organisation org : all()) {
            String orgHost = OrgConfig.hostOf(org.getBaseUrl());
            if (orgHost == null) {
                continue;
            }
            if (orgHost.equals(wanted)) {
                return org;
            }
            if (hostOnly == null && stripPort(orgHost).equals(wantedNoPort)) {
                hostOnly = org;
            }
        }
        return hostOnly;
    }

    public List<Organisation> findActive() {
        return em.createQuery("SELECT o FROM Organisation o WHERE o.active = true ORDER BY o.id", Organisation.class)
                .getResultList();
    }

    public List<Organisation> allOrdered() {
        return em.createQuery("SELECT o FROM Organisation o ORDER BY o.id", Organisation.class)
                .getResultList();
    }

    private static String stripPort(String host) {
        int colon = host.lastIndexOf(':');
        return colon > 0 && host.indexOf(']') < colon ? host.substring(0, colon) : host;
    }
}

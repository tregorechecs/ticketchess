package com.github.gcolin.organisation;

import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import java.util.List;
import java.util.Locale;

public class OrganisationDao extends AbstractDao<Organisation> {

    private static final String WRITE_COLUMNS = "base_url, active, settings_json";

    public OrganisationDao() {
        super(Organisation.class, "organisation");
    }

    public Organisation findByBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        return selectOne(
                "SELECT * FROM organisation WHERE base_url = ? ORDER BY id LIMIT 1",
                OrgConfig.normalizeBaseUrl(baseUrl));
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
        return select("SELECT * FROM organisation WHERE active = TRUE ORDER BY id");
    }

    public List<Organisation> allOrdered() {
        return select("SELECT * FROM organisation ORDER BY id");
    }

    private static String stripPort(String host) {
        int colon = host.lastIndexOf(':');
        return colon > 0 && host.indexOf(']') < colon ? host.substring(0, colon) : host;
    }

    @Override
    protected List<Organisation> select(String sql, Object... params) {
        List<Organisation> list = Db.queryList(sql, Organisation.class, params);
        for (Organisation org : list) {
            OrganisationSettings.unpack(org);
        }
        return list;
    }

    private static Object[] values(Organisation org) {
        OrganisationSettings.pack(org);
        return new Object[] {org.getBaseUrl(), org.isActive(), org.getSettingsJson()};
    }

    @Override
    public void persist(Organisation org) {
        Object[] values = values(org);
        if (org.getId() == null) {
            StringBuilder marks = new StringBuilder();
            for (int i = 0; i < values.length; i++) {
                marks.append(i == 0 ? "?" : ", ?");
            }
            int id = Db.insertReturningInt(
                    "INSERT INTO organisation (" + WRITE_COLUMNS + ") VALUES (" + marks + ")", values);
            org.setId(id);
        } else {
            StringBuilder set = new StringBuilder();
            for (String column : WRITE_COLUMNS.split(",")) {
                if (set.length() > 0) {
                    set.append(", ");
                }
                set.append(column.trim()).append(" = ?");
            }
            Object[] args = new Object[values.length + 1];
            System.arraycopy(values, 0, args, 0, values.length);
            args[values.length] = org.getId();
            Db.update("UPDATE organisation SET " + set + " WHERE id = ?", args);
        }
    }

    @Override
    public Organisation merge(Organisation org) {
        persist(org);
        return org;
    }
}

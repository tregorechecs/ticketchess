package com.github.gcolin.organisation;

import java.util.Properties;

/**
 * Platform properties whose organisation-scoped keys are answered by the current organisation
 * (see {@link OrgConfig}) when a request is in scope, and by {@code params.properties} otherwise.
 */
public class OrgProperties extends Properties {

    private static final long serialVersionUID = 1L;

    @Override
    public String getProperty(String key) {
        Organisation org = OrgScope.currentOrg();
        if (org != null) {
            String value = new OrgConfig(org).getProperty(key);
            if (value != null) {
                return value;
            }
            if (OrgConfig.isOrgScoped(key)) {
                return null;
            }
        }
        return super.getProperty(key);
    }

    /** Value from {@code params.properties} only, ignoring the current organisation. */
    public String rawProperty(String key) {
        return super.getProperty(key);
    }
}

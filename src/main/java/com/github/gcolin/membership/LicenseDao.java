package com.github.gcolin.membership;

import com.github.gcolin.organisation.OrgEntityListener;
import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;

public class LicenseDao extends AbstractDao<License> {

    public LicenseDao() {
        super(License.class, "license");
    }

    @Override
    protected boolean orgScoped() {
        return true;
    }

    @Override
    public void persist(License license) {
        if (license.getId() == null) {
            OrgEntityListener.assignOrg(license);
            touchCreated(license);
            int id = Db.insertReturningInt(
                    "INSERT INTO license (created_at, updated_at, org_id, name, access_rule) VALUES (?, ?, ?, ?, ?)",
                    license.getCreatedAt(),
                    license.getUpdatedAt(),
                    license.getOrgId(),
                    license.getName(),
                    Db.enumName(license.getAccessRule()));
            license.setId(id);
        } else {
            touchUpdated(license);
            Db.update(
                    "UPDATE license SET updated_at = ?, org_id = ?, name = ?, access_rule = ? WHERE id = ?",
                    license.getUpdatedAt(),
                    license.getOrgId(),
                    license.getName(),
                    Db.enumName(license.getAccessRule()),
                    license.getId());
        }
    }

    @Override
    public License merge(License license) {
        persist(license);
        return license;
    }
}

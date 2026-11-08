package com.github.gcolin.membership;

import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LicensePriceDao extends AbstractDao<LicensePrice> {

    public LicensePriceDao() {
        super(LicensePrice.class, "license_price");
    }

    @Override
    protected List<LicensePrice> select(String sql, Object... params) {
        List<LicensePrice> list = selectWith(
                (price, rs) -> {
                    Integer licenseId = intOrNull(rs, "license_id");
                    if (licenseId != null) {
                        License license = new License();
                        license.setId(licenseId);
                        price.setLicense(license);
                    }
                },
                sql,
                params);
        Set<Integer> licenseIds = new LinkedHashSet<>();
        for (LicensePrice price : list) {
            if (price.getLicense() != null) {
                licenseIds.add(price.getLicense().getId());
            }
        }
        Map<Integer, License> licenses = new LicenseDao().findByIds(licenseIds);
        for (LicensePrice price : list) {
            if (price.getLicense() != null) {
                License license = licenses.get(price.getLicense().getId());
                if (license != null) {
                    price.setLicense(license);
                }
            }
        }
        return list;
    }

    @Override
    public void persist(LicensePrice price) {
        if (price.getId() == null) {
            touchCreated(price);
            int id = Db.insertReturningInt(
                    "INSERT INTO license_price (created_at, updated_at, category, price_cents, license_id)"
                            + " VALUES (?, ?, ?, ?, ?)",
                    price.getCreatedAt(),
                    price.getUpdatedAt(),
                    price.getCategory(),
                    price.getPriceCents(),
                    Db.fkId(price.getLicense()));
            price.setId(id);
        } else {
            touchUpdated(price);
            Db.update(
                    "UPDATE license_price SET updated_at = ?, category = ?, price_cents = ?, license_id = ? WHERE id = ?",
                    price.getUpdatedAt(),
                    price.getCategory(),
                    price.getPriceCents(),
                    Db.fkId(price.getLicense()),
                    price.getId());
        }
    }

    @Override
    public LicensePrice merge(LicensePrice price) {
        persist(price);
        return price;
    }
}

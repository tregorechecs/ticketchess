package com.github.gcolin.membership;

import com.github.gcolin.platform.TimedEntity;

public class LicensePrice extends TimedEntity {

    private Integer id;

    private String category;

    private Integer priceCents;

    private License license;

    public LicensePrice() {}

    public LicensePrice(String category, Integer priceCents, License license) {
        this.category = category;
        this.priceCents = priceCents;
        this.license = license;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getPriceCents() {
        return priceCents;
    }

    public void setPriceCents(Integer priceCents) {
        this.priceCents = priceCents;
    }

    public License getLicense() {
        return license;
    }

    public void setLicense(License license) {
        this.license = license;
    }

    @Override
    public String toString() {
        return "LicensePrice{" + "id=" + id + ", category='" + category + '\'' + ", priceCents=" + priceCents
                + ", license=" + (license != null ? license.getName() : null) + '}';
    }
}

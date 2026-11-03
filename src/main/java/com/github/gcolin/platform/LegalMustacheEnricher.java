package com.github.gcolin.platform;

import java.util.Map;

public final class LegalMustacheEnricher {

    private LegalMustacheEnricher() {}

    public static void enrich(Map<String, Object> model, Messages messages, Page pageConfig) {
        String orgName = pageConfig != null && pageConfig.getOrgName() != null ? pageConfig.getOrgName() : "";
        String orgAddress =
                pageConfig != null && pageConfig.getOrgAddress() != null ? pageConfig.getOrgAddress() : "";
        String orgHostingAddress = pageConfig != null && pageConfig.getOrgHostingAddress() != null
                ? pageConfig.getOrgHostingAddress()
                : "";

        model.put("publisherText", messages.get("legal.publisher.text", orgName, orgAddress));
        model.put("hostingText", messages.get("legal.hosting.text", orgName, orgHostingAddress));
        model.put("purposeText", messages.get("legal.purpose.text", orgName));
        model.put("ipText", messages.get("legal.ip.text", orgName));
        model.put("liabilityText", messages.get("legal.liability.text", orgName));

        String orgEmail = pageConfig != null ? pageConfig.getOrgEmail() : null;
        model.put("hasOrgEmail", orgEmail != null && !orgEmail.isEmpty());

        String sourceUrl = pageConfig != null ? pageConfig.getSourceUrl() : null;
        model.put("hasSourceUrl", sourceUrl != null && !sourceUrl.isBlank());
        model.put("titleKey", "legal.title");
    }
}

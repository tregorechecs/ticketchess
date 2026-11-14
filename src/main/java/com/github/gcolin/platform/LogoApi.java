package com.github.gcolin.platform;

import jakarta.ws.rs.Path;

@Path("logo")
public class LogoApi extends AbstractOrgAssetDownloadApi {

    @Override
    protected AbstractOrgImageFileService service() {
        return AppContext.get().logoService();
    }
}

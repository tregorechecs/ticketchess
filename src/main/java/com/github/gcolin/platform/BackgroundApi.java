package com.github.gcolin.platform;

import jakarta.ws.rs.Path;

@Path("background")
public class BackgroundApi extends AbstractOrgAssetDownloadApi {

    @Override
    protected AbstractOrgImageFileService service() {
        return AppContext.get().backgroundService();
    }
}

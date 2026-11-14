package com.github.gcolin.platform;

import jakarta.ws.rs.Path;

@Path("signature")
public class SignatureApi extends AbstractOrgAssetDownloadApi {

    @Override
    protected AbstractOrgImageFileService service() {
        return AppContext.get().signatureService();
    }
}

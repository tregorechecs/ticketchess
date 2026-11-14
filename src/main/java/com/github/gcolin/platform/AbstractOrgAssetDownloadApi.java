package com.github.gcolin.platform;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.core.Response;

public abstract class AbstractOrgAssetDownloadApi {

    protected abstract AbstractOrgImageFileService service();

    @GET
    public Response download() {
        var s = service();
        if (!s.exists()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(ClientDisconnect.copyFile(s.getFile()))
                .type(s.getContentType())
                .header("Cache-Control", "no-cache")
                .build();
    }
}

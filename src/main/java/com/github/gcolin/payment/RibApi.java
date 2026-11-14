package com.github.gcolin.payment;

import jakarta.ws.rs.GET;
import com.github.gcolin.platform.AppContext;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import com.github.gcolin.platform.ClientDisconnect;

@Path("rib")
public class RibApi {

    @GET
    public Response download() {
        if (!AppContext.get().ribService().exists()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        StreamingOutput stream = ClientDisconnect.copyFile(AppContext.get().ribService().getRibFile());
        return Response.ok(stream)
                .type("application/pdf")
                .header("Content-Disposition", "inline; filename=\"" + RibService.FILE_NAME + "\"")
                .build();
    }
}

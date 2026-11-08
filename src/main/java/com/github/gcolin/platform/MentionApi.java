package com.github.gcolin.platform;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.util.Collections;

@Path("mentions")
public class MentionApi {

    @GET
    public MustacheHtml get() {
        return new MustacheHtml(Collections.emptyMap(), "platform/mentions.mustache");
    }
}

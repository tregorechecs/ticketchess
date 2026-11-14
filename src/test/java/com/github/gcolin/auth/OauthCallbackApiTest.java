package com.github.gcolin.auth;

import com.github.gcolin.platform.RequestContext;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.gcolin.platform.TestContext;
import com.github.gcolin.platform.Caches;
import com.github.gcolin.platform.Config;
import io.jsonwebtoken.security.Keys;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Collections;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

class OauthCallbackApiTest {

    @Test
    void callbackShouldReturnBadRequestWhenCodeMissing() throws Exception {
        OauthCallbackApi api = new OauthCallbackApi() {};
        try (TestContext tc = TestContext.open(app -> {
            app.setCaches(new Caches());
            app.setConfig(mock(Config.class));
        }, ctx -> {
            ctx.setLoggedUser(mock(LoggedUser.class));
        })) {
            RequestContext.require().setUriInfo(mock(UriInfo.class));

            Response response = api.callback(null, null);

            assertEquals(400, response.getStatus());
        }
    }

    @Test
    void callbackShouldHandleTokenExchangeFailure() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        OauthCallbackApi api = new OauthCallbackApi() {
            @Override
            protected HttpResponse<String> requestToken(String form) throws IOException, InterruptedException {
                return response;
            }
        };

        when(response.statusCode()).thenReturn(401);
        Config config = mock(Config.class);
        when(config.getOauthClientId()).thenReturn("id");
        when(config.getOauthClientSecret()).thenReturn("secret");
        when(config.getOauthRedirectUri()).thenReturn("http://localhost:8080/oauth-callback");

        try (TestContext tc = TestContext.open(app -> {
            app.setCaches(new Caches());
            app.setConfig(config);
        }, ctx -> {
            ctx.setLoggedUser(mock(LoggedUser.class));
        })) {
            RequestContext.require().setUriInfo(mock(UriInfo.class));

            Response r = api.callback("invalid_code", null);

            assertEquals(401, r.getStatus());
        }
    }

    @Test
    void callbackWithStateParameterShouldDecodeAndRedirect() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        OauthCallbackApi api = new OauthCallbackApi() {
            @Override
            protected HttpResponse<String> requestToken(String form) throws IOException, InterruptedException {
                return response;
            }
        };

        when(response.statusCode()).thenReturn(200);

        JSONObject inner = new JSONObject();
        inner.put("name", "test");
        inner.put("email", "test@gmail.com");
        JSONObject obj = new JSONObject();
        obj.put(
                "id_token",
                "1." + Base64.getUrlEncoder().encodeToString(inner.toString().getBytes()));
        when(response.body()).thenReturn(obj.toString());

        String decodedState = "/event/5";
        String encodedState = Base64.getUrlEncoder().withoutPadding().encodeToString(decodedState.getBytes());

        Config config = mock(Config.class);
        when(config.getAdmins()).thenReturn(Collections.emptySet());
        when(config.getOauthClientId()).thenReturn("id");
        when(config.getOauthClientSecret()).thenReturn("secret");
        when(config.getOauthRedirectUri()).thenReturn("http://localhost:8080/oauth-callback");
        when(config.getKeys())
                .thenReturn(Keys.hmacShaKeyFor(
                        "9f3b2a8c5d4e7f1b2c9a0d6e3f1b4c7a8d2e5f9c1b0a3d6e4f7c8b9a2d1e6f3b".getBytes()));
        try (TestContext tc = TestContext.open(app -> {
            app.setCaches(new Caches());
            app.setConfig(config);
        }, ctx -> {
            ctx.setLoggedUser(mock(LoggedUser.class));
        })) {

            UriInfo uriInfo = mock(UriInfo.class);
            UriBuilder uriBuilder = mock(UriBuilder.class);
            when(uriInfo.getBaseUriBuilder()).thenReturn(uriBuilder);
            when(uriBuilder.path(decodedState)).thenReturn(uriBuilder);
            when(uriBuilder.build()).thenReturn(URI.create("http://localhost:8080/event/5"));
            RequestContext.require().setUriInfo(uriInfo);

            Response r = api.callback("invalid", encodedState);

            assertEquals(303, r.getStatus());
        }
    }

    @Test
    void callbackShouldUseUserinfoWhenIdTokenMissing() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> tokenResponse = mock(HttpResponse.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> userinfoResponse = mock(HttpResponse.class);
        OauthCallbackApi api = new OauthCallbackApi() {
            @Override
            protected HttpResponse<String> requestToken(String form) {
                return tokenResponse;
            }

            @Override
            protected HttpResponse<String> requestUserinfo(String accessToken) {
                return userinfoResponse;
            }
        };

        when(tokenResponse.statusCode()).thenReturn(200);
        when(tokenResponse.body()).thenReturn(new JSONObject().put("access_token", "tok").toString());
        when(userinfoResponse.statusCode()).thenReturn(200);
        when(userinfoResponse.body())
                .thenReturn(new JSONObject().put("email", "user@example.com").put("name", "User").toString());

        Config config = mock(Config.class);
        when(config.getAdmins()).thenReturn(Collections.emptySet());
        when(config.getOauthClientId()).thenReturn("id");
        when(config.getOauthClientSecret()).thenReturn("secret");
        when(config.getOauthRedirectUri()).thenReturn("http://localhost:8080/oauth-callback");
        when(config.getOauthUserinfoUrl()).thenReturn("https://example.com/userinfo");
        when(config.getKeys())
                .thenReturn(Keys.hmacShaKeyFor(
                        "9f3b2a8c5d4e7f1b2c9a0d6e3f1b4c7a8d2e5f9c1b0a3d6e4f7c8b9a2d1e6f3b".getBytes()));
        try (TestContext tc = TestContext.open(app -> {
            app.setCaches(new Caches());
            app.setConfig(config);
        }, ctx -> {
            ctx.setLoggedUser(mock(LoggedUser.class));
        })) {

            UriInfo uriInfo = mock(UriInfo.class);
            when(uriInfo.getBaseUri()).thenReturn(URI.create("http://localhost:8080"));
            RequestContext.require().setUriInfo(uriInfo);

            Response r = api.callback("code", null);

            assertEquals(303, r.getStatus());
        }
    }

}

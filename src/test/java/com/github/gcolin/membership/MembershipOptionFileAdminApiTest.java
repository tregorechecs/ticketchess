package com.github.gcolin.membership;

import com.github.gcolin.platform.RequestContext;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.club.ClubSeasonFilter;
import com.github.gcolin.club.SeasonScope;
import com.github.gcolin.platform.Config;
import com.github.gcolin.platform.MustacheHtml;
import com.github.gcolin.platform.SendMail;
import com.github.gcolin.platform.TestContext;
import io.jsonwebtoken.security.Keys;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MembershipOptionFileAdminApiTest {
    @TempDir Path temp;

    private TestContext tc;

    @AfterEach
    void closeContext() {
        if (tc != null) {
            tc.close();
            tc = null;
        }
    }


    private static UriInfo uriInfo() {
        UriInfo info = mock(UriInfo.class);
        UriBuilder builder = mock(UriBuilder.class);
        when(info.getBaseUriBuilder()).thenReturn(builder);
        when(builder.path(anyString())).thenReturn(builder);
        when(builder.path(any(Class.class))).thenReturn(builder);
        when(builder.path(any(Class.class), anyString())).thenReturn(builder);
        when(builder.queryParam(anyString(), any())).thenReturn(builder);
        when(builder.fragment(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(URI.create("http://localhost/redirect"));
        return info;
    }

    private static MembershipOption option(int id) {
        MembershipOption option = new MembershipOption();
        option.setId(id);
        option.setOptionValue("License");
        return option;
    }

    private static MembershipOptionFile file(MembershipOption option) {
        MembershipOptionFile file = new MembershipOptionFile();
        file.setId(4);
        file.setMembershipOption(option);
        file.setOriginalName("rules.pdf");
        file.setStoredName("stored.pdf");
        file.setContentType("application/pdf");
        return file;
    }

    private MembershipOptionFileAdminApi api(MembershipOption option, MembershipOptionFile file,
            MembershipOptionFileService service, MembershipOptionFileDao files,
            MembershipOptionSubscriptionDao subscriptions) throws Exception {
        MembershipOptionFileAdminApi api = new MembershipOptionFileAdminApi();
        MembershipOptionDao options = mock(MembershipOptionDao.class);
        when(options.find(option.getId())).thenReturn(option);
        when(options.all(any())).thenReturn(List.of(option));
        when(files.findByOptionId(option.getId())).thenReturn(List.of(file));
        when(files.findWithOption(file.getId())).thenReturn(file);
        when(subscriptions.findByOptionId(option.getId())).thenReturn(List.of());
        ClubSeasonFilter seasons = mock(ClubSeasonFilter.class);
        when(seasons.resolve(any())).thenReturn(SeasonScope.all());
        when(seasons.effectiveSeasonId(any())).thenReturn(1);
        Config config = mock(Config.class);
        Properties properties = new Properties();
        properties.setProperty("baseurl", "http://localhost/");
        when(config.getProperties()).thenReturn(properties);
        when(config.getKeys()).thenReturn(Keys.hmacShaKeyFor(
                "integration-test-jwt-key-not-for-production-use-only-pad".getBytes()));
        LoggedUser loggedUser = mock(LoggedUser.class);
        SendMail sendMail = mock(SendMail.class);
        tc = TestContext.open(app -> {
            app.setConfig(config);
            app.setSendMail(sendMail);
            app.setMembershipOptionFileService(service);
        }, ctx -> {
            ctx.setMembershipOptionDao(options);
            ctx.setMembershipOptionFileDao(files);
            ctx.setMembershipOptionSubscriptionDao(subscriptions);
            ctx.setLoggedUser(loggedUser);
            ctx.setClubSeasonFilter(seasons);
        });
        RequestContext.require().setUriInfo(uriInfo());
        return api;
    }

    @Test
    void pageUploadAndDeleteCoverBranches() throws Exception {
        MembershipOption option = option(1);
        MembershipOptionFile file = file(option);
        MembershipOptionFileService service = mock(MembershipOptionFileService.class);
        MembershipOptionFileDao files = mock(MembershipOptionFileDao.class);
        MembershipOptionSubscriptionDao subscriptions = mock(MembershipOptionSubscriptionDao.class);
        MembershipOptionFileAdminApi api = api(option, file, service, files, subscriptions);
        MustacheHtml page = api.page(1, 1, "bad", 2);
        assertEquals("membership/membershipOptionFiles.mustache", page.getTemplate());
        when(service.save(anyInt(), anyString(), any(), any())).thenReturn(
                new MembershipOptionFileService.StoredFile("x.pdf", "stored.pdf", "application/pdf", 2));
        assertEquals(303, api.upload(1, "x.pdf", 1, new ByteArrayInputStream(new byte[]{1})).getStatus());
        doThrow(new jakarta.ws.rs.WebApplicationException("File type is not allowed", 400))
                .when(service).save(anyInt(), anyString(), any(), any());
        assertEquals(303, api.upload(1, "x.exe", 1, new ByteArrayInputStream(new byte[]{1})).getStatus());
        when(files.findWithOption(99)).thenReturn(null);
        assertEquals(404, api.delete(99, 1, 1).getStatus());
        when(files.findWithOption(99)).thenReturn(file);
        assertEquals(400, api.delete(99, 2, 1).getStatus());
        doThrow(new java.io.IOException("disk")).when(service).delete(file);
        assertEquals(303, api.delete(99, 1, 1).getStatus());
    }

    @Test
    void downloadMailPreviewAndSendMailWork() throws Exception {
        MembershipOption option = option(1);
        MembershipOptionFile file = file(option);
        MembershipOptionFileService service = mock(MembershipOptionFileService.class);
        MembershipOptionFileDao files = mock(MembershipOptionFileDao.class);
        MembershipOptionSubscriptionDao subscriptions = mock(MembershipOptionSubscriptionDao.class);
        MembershipOptionFileAdminApi api = api(option, file, service, files, subscriptions);
        Membership member = new Membership();
        member.setUser("member@example.com");
        member.setFirstname("Member");
        member.setStatus(MembershipStatus.PAID);
        MembershipOptionSubscription subscription = new MembershipOptionSubscription();
        subscription.setMembership(member);
        when(subscriptions.findByOptionId(1)).thenReturn(List.of(subscription));
        assertThrows(NotFoundException.class, () -> api.download(999));
        when(service.resolveStoredFile(file)).thenReturn(temp.resolve("stored.pdf"));
        java.nio.file.Files.writeString(temp.resolve("stored.pdf"), "file");
        Response download = api.download(4);
        assertEquals(200, download.getStatus());
        assertEquals("application/pdf", download.getMediaType().toString());
        assertEquals(200, api.mailPreview(4, "**hello**").getStatus());
        assertEquals("membership/membershipOptionFileMail.mustache",
                api.mailPage(4, 1, 3).getTemplate());
        assertEquals(303, api.sendMail(4, "", "body", 1).getStatus());
    }
}

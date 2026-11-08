package com.github.gcolin.platform;

import com.github.gcolin.auth.LoggedUser;
import com.github.gcolin.auth.RoleResolver;
import com.github.gcolin.auth.UserAuthorizationDao;
import com.github.gcolin.desk.EventDeskHub;
import com.github.gcolin.desk.EventDeskService;
import com.github.gcolin.event.EventCollectionDao;
import com.github.gcolin.event.EventCollectionOptionDao;
import com.github.gcolin.event.EventDao;
import com.github.gcolin.event.EventGroupDao;
import com.github.gcolin.event.EventGroupFilter;
import com.github.gcolin.event.EventOptionDao;
import com.github.gcolin.event.PapiService;
import com.github.gcolin.event.PapiUlploadService;
import com.github.gcolin.club.ClubSeasonDao;
import com.github.gcolin.club.ClubSeasonFilter;
import com.github.gcolin.membership.LicenseDao;
import com.github.gcolin.membership.LicensePriceDao;
import com.github.gcolin.membership.MembershipDao;
import com.github.gcolin.membership.MembershipOptionDao;
import com.github.gcolin.membership.MembershipOptionFileDao;
import com.github.gcolin.membership.MembershipOptionSubscriptionDao;
import com.github.gcolin.organisation.Organisation;
import com.github.gcolin.organisation.OrganisationDao;
import com.github.gcolin.organisation.OrgScope;
import com.github.gcolin.payment.DebtService;
import com.github.gcolin.payment.PaymentDao;
import com.github.gcolin.player.CustomPlayerDao;
import com.github.gcolin.player.Find;
import com.github.gcolin.registration.PlayerPendingSubscriptionDao;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import com.github.gcolin.registration.PlayerSubscriptionOptionDao;
import com.github.gcolin.registration.RegisterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import java.util.function.Supplier;

public final class RequestContext {

    private static final ThreadLocal<RequestContext> CURRENT = new ThreadLocal<>();
    private static final String LOGGED_USER_KEY = "loggedUser";

    private HttpServletRequest request;
    private Connection connection;
    /** {@code true} when this context opened (and therefore must close) the connection. */
    private boolean ownsConnection;
    private boolean readOnly;
    private Organisation organisation;
    private OrganisationDao organisationDao;

    private ClubSeasonDao clubSeasonDao;
    private ClubSeasonFilter clubSeasonFilter;
    private LicenseDao licenseDao;
    private LicensePriceDao licensePriceDao;
    private MembershipDao membershipDao;
    private MembershipOptionDao membershipOptionDao;
    private MembershipOptionFileDao membershipOptionFileDao;
    private MembershipOptionSubscriptionDao membershipOptionSubscriptionDao;
    private UserAuthorizationDao userAuthorizationDao;
    private CustomPlayerDao customPlayerDao;
    private EventGroupDao eventGroupDao;
    private EventOptionDao eventOptionDao;
    private EventCollectionOptionDao eventCollectionOptionDao;
    private EventCollectionDao eventCollectionDao;
    private EventDao eventDao;
    private PlayerSubscriptionDao playerSubscriptionDao;
    private PlayerSubscriptionOptionDao playerSubscriptionOptionDao;
    private PlayerPendingSubscriptionDao playerPendingSubscriptionDao;
    private PaymentDao paymentDao;
    private Find find;
    private LoggedUser loggedUserOverride;
    private DebtService debtService;
    private RegisterService registerService;
    private StateService stateService;
    private EventGroupFilter eventGroupFilter;
    private PapiService papiService;
    private PapiUlploadService papiUlploadService;

    private RequestContext() {}

    public static void open(HttpServletRequest request) {
        RequestContext ctx = new RequestContext();
        ctx.request = request;
        ctx.ownsConnection = true;
        CURRENT.set(ctx);
    }

    /**
     * Read-only requests (GET/HEAD/OPTIONS). The connection is acquired lazily on first use, so requests
     * that never touch the database do not hold a pooled connection.
     */
    public static void openReadOnly(HttpServletRequest request) {
        RequestContext ctx = new RequestContext();
        ctx.request = request;
        ctx.readOnly = true;
        ctx.ownsConnection = true;
        CURRENT.set(ctx);
    }

    public static boolean isReadOnly() {
        RequestContext ctx = CURRENT.get();
        return ctx != null && ctx.readOnly;
    }

    /** Commits the work done so far by a read-only request (nothing to clear: JDBC has no persistence context). */
    public static void commitReadAndClear() {
        RequestContext ctx = CURRENT.get();
        if (ctx == null || !ctx.readOnly || ctx.connection == null) {
            return;
        }
        ctx.commitConnection();
    }

    private static Connection acquire() {
        return acquire(AppContext.get().persistence().getDataSource());
    }

    private static Connection acquire(DataSource dataSource) {
        try {
            Connection connection = dataSource.getConnection();
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException e) {
            throw new PersistenceException("Cannot open database connection", e);
        }
    }

    private void commitConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.commit();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Commit failed", e);
        }
    }

    private void rollbackConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.rollback();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Rollback failed", e);
        }
    }

    private void closeConnection() {
        Connection toClose = connection;
        connection = null;
        if (toClose == null || !ownsConnection) {
            return;
        }
        try {
            if (!toClose.isClosed()) {
                toClose.close();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Cannot close database connection", e);
        }
    }

    static void openForTest(Connection connection) {
        RequestContext ctx = new RequestContext();
        ctx.connection = connection;
        CURRENT.set(ctx);
    }

    static void openForTestReadOnly(Connection connection) {
        RequestContext ctx = new RequestContext();
        ctx.connection = connection;
        ctx.readOnly = true;
        CURRENT.set(ctx);
    }

    static void openForTest(HttpServletRequest request, Connection connection) {
        RequestContext ctx = new RequestContext();
        ctx.request = request;
        ctx.connection = connection;
        CURRENT.set(ctx);
    }

    static void openForTest(Find find) {
        RequestContext ctx = new RequestContext();
        ctx.find = find;
        CURRENT.set(ctx);
    }

    static void openForTest(LoggedUser loggedUser) {
        RequestContext ctx = new RequestContext();
        ctx.loggedUserOverride = loggedUser;
        CURRENT.set(ctx);
    }

    static void openForTest(Connection connection, Find find) {
        RequestContext ctx = new RequestContext();
        ctx.connection = connection;
        ctx.find = find;
        CURRENT.set(ctx);
    }

    public static void commit() {
        RequestContext ctx = CURRENT.get();
        if (ctx != null) {
            ctx.commitConnection();
        }
    }

    public static void rollback() {
        RequestContext ctx = CURRENT.get();
        if (ctx != null) {
            ctx.rollbackConnection();
        }
    }

    public static void close() {
        RequestContext ctx = CURRENT.get();
        try {
            if (ctx != null) {
                ctx.closeConnection();
            }
        } finally {
            CURRENT.remove();
            OrgScope.clearFilter();
        }
    }

    public static RequestContext current() {
        return CURRENT.get();
    }

    public static RequestContext require() {
        RequestContext ctx = CURRENT.get();
        if (ctx == null) {
            throw new IllegalStateException("No active request context");
        }
        return ctx;
    }

    public Organisation organisation() {
        return organisation;
    }

    public Integer orgId() {
        return organisation == null ? null : organisation.getId();
    }

    public void setOrganisation(Organisation organisation) {
        this.organisation = organisation;
    }

    /** Servlet context path used for links and login/OAuth return URLs. */
    public String effectiveContextPath() {
        String base = request != null ? request.getContextPath() : "";
        return base == null ? "" : base;
    }

    public OrganisationDao organisationDao() {
        if (organisationDao == null) {
            organisationDao = new OrganisationDao();
        }
        return organisationDao;
    }

    public static void run(Runnable action) {
        run(() -> {
            action.run();
            return null;
        });
    }

    public static <T> T run(Supplier<T> action) {
        return run((DataSource) null, action);
    }

    /**
     * Runs {@code action} in its own transactional context on a connection borrowed from {@code dataSource}
     * (or from the application's data source when {@code null}).
     */
    public static void run(DataSource dataSource, Runnable action) {
        run(dataSource, (Supplier<Object>) () -> {
            action.run();
            return null;
        });
    }

    public static <T> T run(DataSource dataSource, Supplier<T> action) {
        RequestContext previous = CURRENT.get();
        RequestContext ctx = new RequestContext();
        ctx.ownsConnection = true;
        if (previous != null) {
            // Nested run (e.g. desk service called while serving a request): keep the tenant / request.
            ctx.request = previous.request;
            ctx.organisation = previous.organisation;
            ctx.loggedUserOverride = previous.loggedUserOverride;
            ctx.find = previous.find;
        }
        CURRENT.set(ctx);
        try {
            if (dataSource != null) {
                ctx.connection = acquire(dataSource);
            }
            T result = action.get();
            ctx.commitConnection();
            return result;
        } catch (RuntimeException e) {
            try {
                ctx.rollbackConnection();
            } catch (RuntimeException rollbackFailure) {
                e.addSuppressed(rollbackFailure);
            }
            throw e;
        } finally {
            if (previous != null) {
                CURRENT.set(previous);
            } else {
                CURRENT.remove();
                OrgScope.clearFilter();
            }
            ctx.closeConnection();
        }
    }

    /** The request-scoped JDBC connection (acquired lazily, auto-commit disabled). */
    public Connection connection() {
        if (connection == null) {
            connection = acquire();
            ownsConnection = true;
        }
        return connection;
    }

    public HttpServletRequest request() {
        return request;
    }

    public LoggedUser loggedUser() {
        if (loggedUserOverride != null) {
            return loggedUserOverride;
        }
        if (request == null) {
            throw new IllegalStateException("LoggedUser requires an HTTP request");
        }
        HttpSession session = request.getSession(true);
        LoggedUser user = (LoggedUser) session.getAttribute(LOGGED_USER_KEY);
        boolean isNew = user == null;
        if (isNew) {
            user = new LoggedUser();
            session.setAttribute(LOGGED_USER_KEY, user);
        }
        LoggedUser.wire(
                user,
                AppContext.get().caches(),
                debtService(),
                request,
                AppContext.get().config(),
                roleResolver());
        if (isNew) {
            user.initFromCookies();
        }
        return user;
    }

    public RoleResolver roleResolver() {
        return new RoleResolver(
                AppContext.get().config(),
                userAuthorizationDao(),
                AppContext.get().caches().getRoleCache());
    }

    public ClubSeasonDao clubSeasonDao() {
        if (clubSeasonDao == null) {
            clubSeasonDao = new ClubSeasonDao();
        }
        return clubSeasonDao;
    }

    public ClubSeasonFilter clubSeasonFilter() {
        if (clubSeasonFilter == null) {
            clubSeasonFilter = new ClubSeasonFilter();
            clubSeasonFilter.setClubSeasonDao(clubSeasonDao());
        }
        return clubSeasonFilter;
    }

    public LicenseDao licenseDao() {
        if (licenseDao == null) {
            licenseDao = new LicenseDao();
        }
        return licenseDao;
    }

    public LicensePriceDao licensePriceDao() {
        if (licensePriceDao == null) {
            licensePriceDao = new LicensePriceDao();
        }
        return licensePriceDao;
    }

    public MembershipDao membershipDao() {
        if (membershipDao == null) {
            membershipDao = new MembershipDao();
        }
        return membershipDao;
    }

    public MembershipOptionDao membershipOptionDao() {
        if (membershipOptionDao == null) {
            membershipOptionDao = new MembershipOptionDao();
        }
        return membershipOptionDao;
    }

    public MembershipOptionFileDao membershipOptionFileDao() {
        if (membershipOptionFileDao == null) {
            membershipOptionFileDao = new MembershipOptionFileDao();
        }
        return membershipOptionFileDao;
    }

    public MembershipOptionSubscriptionDao membershipOptionSubscriptionDao() {
        if (membershipOptionSubscriptionDao == null) {
            membershipOptionSubscriptionDao = new MembershipOptionSubscriptionDao();
        }
        return membershipOptionSubscriptionDao;
    }

    public UserAuthorizationDao userAuthorizationDao() {
        if (userAuthorizationDao == null) {
            userAuthorizationDao = new UserAuthorizationDao();
        }
        return userAuthorizationDao;
    }

    public CustomPlayerDao customPlayerDao() {
        if (customPlayerDao == null) {
            customPlayerDao = new CustomPlayerDao();
        }
        return customPlayerDao;
    }

    public EventGroupDao eventGroupDao() {
        if (eventGroupDao == null) {
            eventGroupDao = new EventGroupDao();
        }
        return eventGroupDao;
    }

    public EventOptionDao eventOptionDao() {
        if (eventOptionDao == null) {
            eventOptionDao = new EventOptionDao();
            eventOptionDao.setEventDao(this::eventDao);
        }
        return eventOptionDao;
    }

    public EventCollectionOptionDao eventCollectionOptionDao() {
        if (eventCollectionOptionDao == null) {
            eventCollectionOptionDao = new EventCollectionOptionDao();
        }
        return eventCollectionOptionDao;
    }

    public EventCollectionDao eventCollectionDao() {
        if (eventCollectionDao == null) {
            eventCollectionDao = new EventCollectionDao();
            eventCollectionDao.setEventCollectionOptionDao(this::eventCollectionOptionDao);
            eventCollectionDao.setPlayerSubscriptionDao(this::playerSubscriptionDao);
        }
        return eventCollectionDao;
    }

    public EventDao eventDao() {
        if (eventDao == null) {
            eventDao = new EventDao();
            eventDao.setFinder(find());
            eventDao.setEventGroupDao(this::eventGroupDao);
            eventDao.setEventOptionDao(this::eventOptionDao);
            eventDao.setEventCollectionOptionDao(this::eventCollectionOptionDao);
            eventDao.setPlayerSubscriptionDao(this::playerSubscriptionDao);
        }
        return eventDao;
    }

    public PlayerSubscriptionDao playerSubscriptionDao() {
        if (playerSubscriptionDao == null) {
            playerSubscriptionDao = new PlayerSubscriptionDao();
        }
        return playerSubscriptionDao;
    }

    public PlayerSubscriptionOptionDao playerSubscriptionOptionDao() {
        if (playerSubscriptionOptionDao == null) {
            playerSubscriptionOptionDao = new PlayerSubscriptionOptionDao();
        }
        return playerSubscriptionOptionDao;
    }

    public PlayerPendingSubscriptionDao playerPendingSubscriptionDao() {
        if (playerPendingSubscriptionDao == null) {
            playerPendingSubscriptionDao = new PlayerPendingSubscriptionDao();
        }
        return playerPendingSubscriptionDao;
    }

    public PaymentDao paymentDao() {
        if (paymentDao == null) {
            paymentDao = new PaymentDao();
        }
        return paymentDao;
    }

    public Find find() {
        if (find == null) {
            find = new Find();
            find.setLuceneDb(AppContext.get().luceneDb());
            find.setCustomPlayerDao(customPlayerDao());
        }
        return find;
    }

    public DebtService debtService() {
        if (debtService == null) {
            debtService = new DebtService();
            debtService.setPlayerSubscriptionDao(playerSubscriptionDao());
            debtService.setPlayerSubscriptionOptionDao(playerSubscriptionOptionDao());
            debtService.setFind(find());
            debtService.setSendMail(AppContext.get().sendMail());
            debtService.setCaches(AppContext.get().caches());
            debtService.setPaymentDao(paymentDao());
            debtService.setMembershipDao(membershipDao());
        }
        return debtService;
    }

    public RegisterService registerService() {
        if (registerService == null) {
            registerService = new RegisterService();
            registerService.setSendMail(AppContext.get().sendMail());
            registerService.setProperties(AppContext.get().config().getProperties());
            registerService.setPlayerSubscriptionDao(playerSubscriptionDao());
            registerService.setPlayerPendingSubscriptionDao(playerPendingSubscriptionDao());
            registerService.setFind(find());
            registerService.setEventDao(eventDao());
            registerService.setEventOptionDao(eventOptionDao());
            registerService.setEventCollectionOptionDao(eventCollectionOptionDao());
            registerService.setCaches(AppContext.get().caches());
            registerService.setPaymentDao(paymentDao());
            registerService.setConfig(AppContext.get().config());
        }
        return registerService;
    }

    public StateService stateService() {
        if (stateService == null) {
            stateService = new StateService();
            stateService.setRequest(request);
            stateService.setConfig(AppContext.get().config());
        }
        return stateService;
    }

    public EventGroupFilter eventGroupFilter() {
        if (eventGroupFilter == null) {
            eventGroupFilter = new EventGroupFilter();
            eventGroupFilter.setCaches(AppContext.get().caches());
            eventGroupFilter.setEventGroupDao(eventGroupDao());
        }
        return eventGroupFilter;
    }

    public PapiService papiService() {
        if (papiService == null) {
            papiService = new PapiService();
            papiService.setLuceneDb(AppContext.get().luceneDb());
        }
        return papiService;
    }

    public PapiUlploadService papiUlploadService() {
        if (papiUlploadService == null) {
            papiUlploadService = new PapiUlploadService();
        }
        return papiUlploadService;
    }
}

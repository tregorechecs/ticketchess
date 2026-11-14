package com.github.gcolin.platform;

import com.github.gcolin.auth.ActiveLoggedUsers;
import com.github.gcolin.desk.EventDeskHub;
import com.github.gcolin.desk.EventDeskService;
import com.github.gcolin.platform.plugin.FeatureModule;
import com.github.gcolin.platform.plugin.HookBus;
import com.github.gcolin.platform.plugin.ModuleContext;
import com.github.gcolin.platform.plugin.ModuleContextImpl;
import com.github.gcolin.platform.plugin.PluginRegistry;
import com.github.gcolin.event.EventPaymentsReportService;
import com.github.gcolin.event.StatisticsReportService;
import com.github.gcolin.membership.LicensePriceService;
import com.github.gcolin.membership.MembershipOptionFileService;
import com.github.gcolin.membership.MembershipReportService;
import com.github.gcolin.organisation.OrganisationBootstrap;
import com.github.gcolin.organisation.OrganisationFiles;
import com.github.gcolin.organisation.OrganisationResolver;
import com.github.gcolin.payment.PaymentReceiptPdfService;
import com.github.gcolin.payment.RibService;
import com.github.gcolin.payment.StripeCheckoutService;
import com.github.gcolin.player.LuceneDb;
import jakarta.servlet.ServletContext;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AppContext {

    private static final Logger logger = LoggerFactory.getLogger(AppContext.class);
    private static AppContext instance;

    private Config config;
    private Caches caches;
    private PersistenceService persistence;
    private MustacheConfig mustacheConfig;
    private SendMail sendMail;
    private HookBus hookBus = new HookBus();
    private PluginRegistry pluginRegistry;
    private LuceneDb luceneDb;
    private OrganisationResolver organisationResolver;
    private ActiveLoggedUsers activeLoggedUsers;
    private EventDeskHub eventDeskHub;
    private EventDeskService eventDeskService;
    private LogoService logoService;
    private SignatureService signatureService;
    private RibService ribService;
    private LicensePriceService licensePriceService;
    private StatisticsReportService statisticsReportService;
    private EventPaymentsReportService eventPaymentsReportService;
    private PaymentReceiptPdfService paymentReceiptPdfService;
    private StripeCheckoutService stripeCheckoutService;
    private MembershipReportService membershipReportService;
    private MembershipOptionFileService membershipOptionFileService;
    private BackgroundService backgroundService;

    private AppContext() {}

    public static void init(ServletContext servletContext) {
        if (instance != null) {
            return;
        }
        AppContext app = new AppContext();
        instance = app;
        try {
            app.config = new Config();
            app.config.init(servletContext);

            app.caches = new Caches();

            app.persistence = new PersistenceService(app.config);
            OrganisationFiles.migrateLegacyFiles(app.config);
            app.persistence.init();

            // Club / invoice / stripe (+ mail) live on organisation; files under CONFIG_DIR/orgs/{id}.
            new OrganisationBootstrap(app.config, app.persistence).ensure();
            // org_id backfill needs at least one organisation row (skipped earlier if the table was empty).
            app.persistence.ensureOrgIdsAfterOrganisationBootstrap();

            DbInit dbInit = new DbInit(app.persistence);
            dbInit.initLicenses();
            dbInit.initClubSeason();
            dbInit.initMembershipOptions();
            dbInit.initEvents();

            app.mustacheConfig = new MustacheConfig();
            app.mustacheConfig.init();

            app.hookBus = new HookBus();
            app.pluginRegistry = PluginRegistry.load();
            ModuleContext moduleContext = new ModuleContextImpl(app);
            for (FeatureModule module : app.pluginRegistry.modules()) {
                module.registerHooks(app.hookBus);
            }
            List<String> ddl = new ArrayList<>();
            for (FeatureModule module : app.pluginRegistry.modules()) {
                module.onStart(moduleContext);
                module.contributeSchema(ddl::add);
            }
            app.applySchema(ddl);

            app.organisationResolver = new OrganisationResolver();

            app.activeLoggedUsers = new ActiveLoggedUsers();

            app.logoService = new LogoService();
            app.logoService.setConfig(app.config);

            app.signatureService = new SignatureService();
            app.signatureService.setConfig(app.config);

            app.ribService = new RibService();
            app.ribService.setConfig(app.config);

            app.licensePriceService = new LicensePriceService();
            app.statisticsReportService = new StatisticsReportService();
            app.eventPaymentsReportService = new EventPaymentsReportService();
            app.eventPaymentsReportService.setProperties(app.config.getProperties());
            app.paymentReceiptPdfService = new PaymentReceiptPdfService();
            app.paymentReceiptPdfService.setProperties(app.config.getProperties());
            app.paymentReceiptPdfService.setLogoService(app.logoService);
            app.paymentReceiptPdfService.setSignatureService(app.signatureService);
            app.stripeCheckoutService = new StripeCheckoutService();
            app.membershipReportService = new MembershipReportService();
            app.membershipReportService.setProperties(app.config.getProperties());

            app.membershipOptionFileService = new MembershipOptionFileService();
            app.membershipOptionFileService.setConfig(app.config);

            app.backgroundService = new BackgroundService();
            app.backgroundService.setConfig(app.config);

            logger.info("Application context initialized");
        } catch (RuntimeException e) {
            instance = null;
            if (app.persistence != null) {
                try {
                    app.persistence.shutdown();
                } catch (RuntimeException shutdownError) {
                    logger.warn("Failed to clean up persistence after init failure", shutdownError);
                }
            }
            throw e;
        }
    }

    private void applySchema(List<String> ddl) {
        if (ddl.isEmpty()) {
            return;
        }
        try (Connection connection = persistence.getDataSource().getConnection();
                Statement statement = connection.createStatement()) {
            for (String sql : ddl) {
                statement.execute(sql);
            }
            if (!connection.getAutoCommit()) {
                connection.commit();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot apply plugin schema", e);
        }
    }

    public static void shutdown() {
        if (instance == null) {
            return;
        }
        if (instance.sendMail != null) {
            try {
                instance.sendMail.close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Interrupted while closing mail executor", e);
            }
        }
        if (instance.luceneDb != null) {
            try {
                instance.luceneDb.close();
            } catch (IOException e) {
                logger.warn("Cannot close lucene index", e);
            }
        }
        if (instance.persistence != null) {
            instance.persistence.shutdown();
        }
        instance = null;
    }

    public static AppContext get() {
        if (instance == null) {
            throw new IllegalStateException("Application context not initialized");
        }
        return instance;
    }

    static void initForTest(ActiveLoggedUsers activeLoggedUsers) {
        AppContext app = new AppContext();
        app.activeLoggedUsers = activeLoggedUsers;
        instance = app;
    }

    /** Install a blank AppContext for tests; caller sets fields via returned instance. */
    static AppContext initForTest() {
        AppContext app = new AppContext();
        instance = app;
        return app;
    }

    static void clearTestInstance() {
        instance = null;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public void setCaches(Caches caches) {
        this.caches = caches;
    }

    public void setPersistence(PersistenceService persistence) {
        this.persistence = persistence;
    }

    public void setMustacheConfig(MustacheConfig mustacheConfig) {
        this.mustacheConfig = mustacheConfig;
    }

    public void setSendMail(SendMail sendMail) {
        this.sendMail = sendMail;
    }

    public void setHookBus(HookBus hookBus) {
        this.hookBus = hookBus;
    }

    public void setPluginRegistry(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    public void setLuceneDb(LuceneDb luceneDb) {
        this.luceneDb = luceneDb;
    }

    public void setOrganisationResolver(OrganisationResolver organisationResolver) {
        this.organisationResolver = organisationResolver;
    }

    public void setActiveLoggedUsers(ActiveLoggedUsers activeLoggedUsers) {
        this.activeLoggedUsers = activeLoggedUsers;
    }

    public void setEventDeskHub(EventDeskHub eventDeskHub) {
        this.eventDeskHub = eventDeskHub;
    }

    public void setEventDeskService(EventDeskService eventDeskService) {
        this.eventDeskService = eventDeskService;
    }

    public void setLogoService(LogoService logoService) {
        this.logoService = logoService;
    }

    public void setSignatureService(SignatureService signatureService) {
        this.signatureService = signatureService;
    }

    public void setRibService(RibService ribService) {
        this.ribService = ribService;
    }

    public void setLicensePriceService(LicensePriceService licensePriceService) {
        this.licensePriceService = licensePriceService;
    }

    public void setStatisticsReportService(StatisticsReportService statisticsReportService) {
        this.statisticsReportService = statisticsReportService;
    }

    public void setEventPaymentsReportService(EventPaymentsReportService eventPaymentsReportService) {
        this.eventPaymentsReportService = eventPaymentsReportService;
    }

    public void setPaymentReceiptPdfService(PaymentReceiptPdfService paymentReceiptPdfService) {
        this.paymentReceiptPdfService = paymentReceiptPdfService;
    }

    public void setStripeCheckoutService(StripeCheckoutService stripeCheckoutService) {
        this.stripeCheckoutService = stripeCheckoutService;
    }

    public void setMembershipReportService(MembershipReportService membershipReportService) {
        this.membershipReportService = membershipReportService;
    }

    public void setMembershipOptionFileService(MembershipOptionFileService membershipOptionFileService) {
        this.membershipOptionFileService = membershipOptionFileService;
    }

    public void setBackgroundService(BackgroundService backgroundService) {
        this.backgroundService = backgroundService;
    }

    public Config config() {
        return config;
    }

    public Caches caches() {
        return caches;
    }

    public PersistenceService persistence() {
        return persistence;
    }

    public MustacheConfig mustacheConfig() {
        return mustacheConfig;
    }

    public SendMail sendMail() {
        return sendMail;
    }

    public HookBus hookBus() {
        return hookBus;
    }

    /** Null until the application started its plugins (and in most unit tests). */
    public PluginRegistry pluginRegistry() {
        return pluginRegistry;
    }

    /** Shared FFE/FIDE Lucene index for all organisations. */
    public LuceneDb luceneDb() {
        return luceneDb;
    }

    public OrganisationResolver organisationResolver() {
        return organisationResolver;
    }

    public ActiveLoggedUsers activeLoggedUsers() {
        return activeLoggedUsers;
    }

    public EventDeskHub eventDeskHub() {
        return eventDeskHub;
    }

    public EventDeskService eventDeskService() {
        return eventDeskService;
    }

    public LogoService logoService() {
        return logoService;
    }

    public SignatureService signatureService() {
        return signatureService;
    }

    public RibService ribService() {
        return ribService;
    }

    public LicensePriceService licensePriceService() {
        return licensePriceService;
    }

    public StatisticsReportService statisticsReportService() {
        return statisticsReportService;
    }

    public EventPaymentsReportService eventPaymentsReportService() {
        return eventPaymentsReportService;
    }

    public PaymentReceiptPdfService paymentReceiptPdfService() {
        return paymentReceiptPdfService;
    }

    public StripeCheckoutService stripeCheckoutService() {
        return stripeCheckoutService;
    }

    public MembershipReportService membershipReportService() {
        return membershipReportService;
    }

    public MembershipOptionFileService membershipOptionFileService() {
        return membershipOptionFileService;
    }

    public BackgroundService backgroundService() {
        return backgroundService;
    }
}

package com.github.gcolin.platform;

import com.github.gcolin.auth.ActiveLoggedUsers;
import com.github.gcolin.desk.EventDeskHub;
import com.github.gcolin.desk.EventDeskService;
import com.github.gcolin.event.EventPaymentsReportService;
import com.github.gcolin.event.StatisticsReportService;
import com.github.gcolin.membership.LicensePriceService;
import com.github.gcolin.membership.MembershipOptionFileService;
import com.github.gcolin.membership.MembershipReportService;
import com.github.gcolin.organisation.OrganisationBootstrap;
import com.github.gcolin.organisation.OrganisationFiles;
import com.github.gcolin.organisation.OrganisationResolver;
import com.github.gcolin.organisation.OrgScope;
import com.github.gcolin.payment.PaymentReceiptPdfService;
import com.github.gcolin.payment.RibService;
import com.github.gcolin.payment.StripeCheckoutService;
import com.github.gcolin.player.LuceneDb;
import com.github.gcolin.player.LuceneDbRegistry;
import jakarta.servlet.ServletContext;
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
    private LuceneDbRegistry luceneDbRegistry;
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

            DbInit dbInit = new DbInit(app.persistence);
            dbInit.initLicenses();
            dbInit.initClubSeason();
            dbInit.initMembershipOptions();
            dbInit.initEvents();

            app.mustacheConfig = new MustacheConfig();
            app.mustacheConfig.init();

            app.sendMail = new SendMail();
            app.sendMail.setProperties(app.config.getProperties());
            app.sendMail.setConfig(app.config);
            app.sendMail.init();

            app.luceneDbRegistry = new LuceneDbRegistry(app.config);
            app.organisationResolver = new OrganisationResolver();

            app.activeLoggedUsers = new ActiveLoggedUsers();
            app.eventDeskHub = new EventDeskHub();

            app.eventDeskService = new EventDeskService();
            app.eventDeskService.setDataSource(app.persistence.getDataSource());
            app.eventDeskService.setCaches(app.caches);
            app.eventDeskService.setSendMail(app.sendMail);

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
        if (instance.luceneDbRegistry != null) {
            instance.luceneDbRegistry.closeAll();
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

    static void clearTestInstance() {
        instance = null;
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

    /** Lucene index of the current organisation (default organisation outside a request). */
    public LuceneDb luceneDb() {
        return luceneDbRegistry == null ? null : luceneDbRegistry.get(OrgScope.currentOrgIdOrDefault());
    }

    public LuceneDbRegistry luceneDbRegistry() {
        return luceneDbRegistry;
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

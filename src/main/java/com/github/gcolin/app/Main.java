package com.github.gcolin.app;

import com.github.gcolin.platform.Config;
import com.github.gcolin.player.LuceneIndexBuilder;
import java.util.logging.Logger;
import org.eclipse.jetty.ee11.webapp.WebAppContext;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class Main {
    public static void main(String[] args) throws Exception {
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        if (args.length > 0 && LuceneIndexBuilder.REBUILD_ARG.equals(args[0])) {
            int exit = rebuildLuceneIndex();
            System.exit(exit);
            return;
        }

        int port = 8080;
        String portEnv = System.getenv("PORT");
        if (portEnv != null && !portEnv.isBlank()) {
            port = Integer.parseInt(portEnv);
        }

        Server server = new Server();
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.addCustomizer(new ForwardedRequestCustomizer());
        ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
        connector.setPort(port);
        server.addConnector(connector);

        WebAppContext context = new WebAppContext();
        context.setContextPath("/");

        var resource = ResourceFactory.of(context).newResource(Main.class.getResource("/webapp"));

        context.setBaseResource(resource);

        context.setParentLoaderPriority(true);

        server.setHandler(context);

        server.start();
        Logger.getGlobal().info("Server started on http://localhost:" + port);
        server.join();
    }

    /** Rebuilds the shared Lucene index then exits (no Jetty). Returns process exit code. */
    static int rebuildLuceneIndex() {
        var log = LoggerFactory.getLogger(Main.class);
        try {
            Config config = new Config();
            config.init(null);
            LuceneIndexBuilder.rebuild(config);
            return 0;
        } catch (Exception e) {
            log.error("Lucene index rebuild failed", e);
            return 1;
        }
    }
}

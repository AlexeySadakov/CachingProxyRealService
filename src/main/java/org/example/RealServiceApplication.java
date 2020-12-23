package org.example;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public class RealServiceApplication {
    private static final Logger logger = Logger.getLogger("RealService");
    private static final AtomicBoolean health = new AtomicBoolean(false);


    public static void main(String[] args) throws IOException {
        try(InputStream is = RealServiceApplication.class
                .getClassLoader()
                .getResourceAsStream("application.properties")){
            if (is == null) {
                System.out.println("Unable to find application.properties");
                return;
            }
            Properties properties = new Properties();
            properties.load(is);

            int serverPort = Integer.parseInt(properties.getProperty("serverPort"));

            String contextSubmitData = properties.getProperty("contextSubmitData");
            String contextIsHealthy = properties.getProperty("contextIsHealthy");
            String contextSwitchHealth = properties.getProperty("contextSwitchHealth");

            HttpServer server = HttpServer.create(new InetSocketAddress(serverPort), 0);
            server.createContext(contextSubmitData, new SubmitDataHandler(logger));
            server.createContext(contextIsHealthy, new IsHealthyHandler());
            server.createContext(contextSwitchHealth, new HealthSwitchingHandler(logger));

            ExecutorService realServiceExecutor = Executors.newCachedThreadPool();
            server.setExecutor(realServiceExecutor);
            server.start();
        }
    }

    private static void prepareExchangeResponse(HttpExchange exchange, String text) throws IOException {
        exchange.sendResponseHeaders(200, text.getBytes().length);
        OutputStream output = exchange.getResponseBody();
        output.write(text.getBytes());
        output.flush();
    }

    static class HealthSwitchingHandler implements HttpHandler {
        private final Logger logger;

        public HealthSwitchingHandler(Logger logger) {
            this.logger = logger;
        }

        public void handle(HttpExchange exchange) throws IOException {

            logger.info("Current value for main service health is: " + health);
            health.set(!health.get());
            prepareExchangeResponse(exchange, "");
            exchange.close();
            logger.info("And now value for main service health is: " + health);
        }
    }

    private static class IsHealthyHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            if (health.get())
                prepareExchangeResponse(exchange, String.format("IsHealthy: %s", health));
            exchange.close();
        }
    }

    private static class SubmitDataHandler implements HttpHandler {
        private final Logger logger;

        public SubmitDataHandler(Logger logger) {
            this.logger = logger;
        }

        public void handle(HttpExchange exchange) throws IOException {
            String threadName = " "
                    + Thread.currentThread().getName() + " / "
                    + Thread.currentThread().getId();
            logger.info("Received data: " + exchange.getRequestURI().getRawQuery());
            prepareExchangeResponse(exchange, threadName);
            exchange.close();
        }

    }
}

package pt.ulisboa.tecnico.meic.cnv;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static pt.ulisboa.tecnico.meic.cnv.State.ALIVE;

public class LoadBalancer {
    private static final int CACHE_THRESHOLD = 10; // fixme bernardo what do you think?
    private static int PORT = 8000;

    //List containing all available nodes
    private static List<WebServerProxy> farm = new CopyOnWriteArrayList<>();

    //We keep a cache metric to avoid contacting database all the time
    //This could be a potential problem, so we should clean it some times during runtime
    //TODO
    private static Map<Argument, Metric> metricCache = new Hashtable<>();

    //One estimator for each model
    private static Map<String, Estimator> estimators = new Hashtable<>();

    //Amazon DynamoDB repository
    private static RepositoryService repositoryService = new RepositoryService();

    public static void main(String[] args) throws ParseException {
        Options options = new Options()
                .addOption("p", true, "Port to listen for remote requests")
                .addOption("cli", false, "Display command line interface");
        CommandLine cmd = (new DefaultParser()).parse(options, args);

        try {
            String argPort = cmd.getOptionValue("p");
            if (argPort != null) PORT = Integer.valueOf(argPort);
        } catch (RuntimeException e) {
            e.printStackTrace();
            System.out.println("Trying default port...");
        }

        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        new ClearCacheTableTask();
        server.createContext("/r.html", new MyHandler());
        System.out.println("Load balancer is running at *:" + PORT);
        server.start();

        final HttpServer finalServer = server;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("Shutting down load balancer");
                finalServer.stop(1);
            }
        });

        if (cmd.hasOption("cli")) {
            (new CLI()).start();
        }
    }

    private static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            (new LoadBalancerThread(t, farm, metricCache, estimators, repositoryService)).start();
        }
    }

    private static class CLI extends Thread {
        @Override
        public void run() {
            System.out.println("Enter one of the following commands:");
            System.out.println("+ ip:port ~~ adds new webserver to farm");
            System.out.println("- ip:port ~~ removes webserver from farm");
            System.out.println("list ~~ lists farm webservers");
            System.out.println("q ~~ stops the loadbalancer");
            Scanner scanner = new Scanner(System.in);
            System.out.println();
            String line;

            label:
            try {
                while ((line = scanner.nextLine()) != null) {
                    String option = line.split(" ")[0];
                    String[] servers;

                    switch (option) {
                        case "q":
                            break label;
                        case "+":
                            try {
                                servers = line.split(" ")[1].split(",");
                            } catch (RuntimeException e) {
                                System.out.println("Invalid arguments");
                                continue;
                            }
                            for (String s : servers) {
                                try {
                                    WebServerProxy wsp = new WebServerProxy(s);
                                    if (farm.contains(wsp)) {
                                        System.out.println("Node already exists");
                                        continue;
                                    }
                                    if (wsp.isAvailable() == ALIVE) {
                                        farm.add(wsp);
                                        System.out.println("Added node to farm");
                                    } else
                                        System.out.println("Node failed to respond");
                                } catch (RuntimeException e) {
                                    System.out.println("Invalid node address");
                                }
                            }
                            break;
                        case "-":
                            try {
                                servers = line.split(" ")[1].split(",");
                            } catch (RuntimeException e) {
                                System.out.println("Invalid arguments");
                                continue;
                            }
                            for (String s : servers) {
                                try {
                                    WebServerProxy wsp = new WebServerProxy(s);
                                    if (farm.contains(wsp)) {
                                        farm.remove(wsp);
                                        System.out.println("Removed node from farm");
                                    } else
                                        System.out.println("Node not found");
                                } catch (RuntimeException e) {
                                    System.out.println("Invalid node address");
                                }
                            }
                            break;

                        case "list":
                            System.out.println("Listing all nodes:");
                            for (WebServerProxy wsp : farm)
                                System.out.println(wsp);
                            break;
                        default:
                            System.out.println("Unknown option");
                            break;
                    }

                }

            } catch (RuntimeException e) {

            }
            System.exit(1);
        }
    }

    private static class ClearCacheTableTask extends TimerTask {

        public ClearCacheTableTask() {
            new Timer().schedule(this, 10 * 1000);
        }

        @Override
        public void run() {
            if (metricCache.size() < CACHE_THRESHOLD) return;
            metricCache.clear();
            new ClearCacheTableTask();
        }
    }

    private static class CheckWebServerProxysTask extends TimerTask {

        public CheckWebServerProxysTask() {
            new Timer().schedule(this, 10 * 1000);
        }

        @Override
        public void run() {
            for (int i = 0; i < farm.size(); i++) {
                if (farm.get(i).isAvailable() == State.DEAD) {
                    farm.remove(i);
                    i -= 1; // We need to check the same position since it will be removed.
                }
            }
            if (metricCache.size() < CACHE_THRESHOLD) return;
            metricCache.clear();
            new ClearCacheTableTask();
        }
    }
}

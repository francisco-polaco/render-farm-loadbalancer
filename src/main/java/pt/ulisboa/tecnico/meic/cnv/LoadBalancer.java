package pt.ulisboa.tecnico.meic.cnv;

import com.amazonaws.services.ec2.model.Instance;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import static pt.ulisboa.tecnico.meic.cnv.State.ALIVE;

public class LoadBalancer {

    private static final int DELAY_RETRY = 10 * 1000 * 30;
    private static final int DELAY_CLEAR_CACHE = 10 * 1000 * 60;
    private static final int DELAY_CHECK_PROXYS = 10 * 1000;
    private static final int CACHE_THRESHOLD = 200;

    //Queue timer
    public static ConcurrentLinkedQueue<Packet> failedRequests = new ConcurrentLinkedQueue<Packet>();
    //Load Balancer ip address
    public static String ip = findMyIp();
    //List containing all available nodes
    private static List<WebServerProxy> farm = new CopyOnWriteArrayList<>();
    private static int PORT = 8000;
    //We keep a cache metric to avoid contacting database all the time
    private static Map<Argument, Metric> metricCache = new Hashtable<>();
    //Amazon DynamoDB repository
    private static RepositoryService repositoryService = new RepositoryService();
    //One estimator for each model
    private static Estimator estimator = new Estimator(metricCache, repositoryService);

    public static void main(String[] args) throws ParseException {
        Options options = new Options()
                .addOption("p", true, "Port to listen for remote requests")
                .addOption("cli", false, "Display command line interface");
        CommandLine cmd = (new DefaultParser()).parse(options, args);

        if (ip == null) {
            System.err.println("Load Balancer should be able to know his ip address!");
            System.exit(-1);
        }

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

        launchTimerTasks();
        server.createContext("/r.html", new MyHandler());
        System.out.println("Load balancer is running at *:" + PORT);

        if (!cmd.hasOption("cli")) {
            // we may want to consider instances that are already up, to add to the farm
            // this will eventually give us a bad load usage in first iterations,
            // but terminating everything to bring up again its unnecessary work
            Instance toAdd = null;
            List<Instance> previousInstances = ScalerService.getInstance().getAllInstances();
            for (Instance instance : previousInstances) {
                // ignore the load balancer
                if (instance.getState().getName().equalsIgnoreCase("terminated") || instance.getPublicIpAddress().equalsIgnoreCase(ip))
                    continue;

                String instanceState = instance.getState().getName();
                if (instanceState.equalsIgnoreCase("running"))
                    try {
                    if (toAdd == null ||
                            (ScalerService.getInstance().retrieveEC2Statistic(toAdd, "CPUUtilization", "Average") >
                                    (ScalerService.getInstance().retrieveEC2Statistic(instance, "CPUUtilization", "Average"))))
                        toAdd = instance;
                } catch (Exception ignored) {
                }
            }

            // our farm should always have a instance in the farm
            // if we have found a instance we can add it to the farm without needing to create a new one
            boolean reuse = true;
            if (toAdd == null) {
                List<Instance> newInstances = ScalerService.getInstance().createInstance(1, 1);
                toAdd = newInstances.get(0);
                reuse = false;
                System.out.println("Creating instance: " + toAdd.getInstanceId() + " with ip: " + toAdd.getPrivateIpAddress());
            } else
                System.out.println("Reusing instance: " + toAdd.getInstanceId() + " with ip: " + toAdd.getPrivateIpAddress());

            WebServerProxy webServerProxy = new WebServerProxy(toAdd.getPrivateIpAddress() + ":" + "8000", toAdd);
            if (!reuse)
                webServerProxy.setServerState(ServerState.INITIALIZING);

            farm.add(webServerProxy);
        }

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

    private static String findMyIp() {
        String ip = null;
        URL whatismyip = null;
        try {
            whatismyip = new URL("http://checkip.amazonaws.com");
            BufferedReader in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));
            ip = in.readLine(); //you get the IP as a String
        } catch (Exception e) {
        }
        return ip;
    }

    private static void launchTimerTasks() {
        new ClearCacheTableTask();
        //new CheckWebServerProxysTask();
        new RetryRequests();
    }

    private static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            System.out.println("Received request: " + t.getRequestURI().getQuery());
            (new LoadBalancerThread(t, farm, estimator)).start();
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
                                    WebServerProxy wsp = new WebServerProxy(s, null);
                                    if (farm.contains(wsp)) {
                                        System.out.println("Node already exists");
                                        continue;
                                    }
                                    if (wsp.isAvailable().getState() == ALIVE) {
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
                                    WebServerProxy wsp = new WebServerProxy(s, null);
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

            } catch (RuntimeException ignored) {

            }
            System.exit(1);
        }
    }

    private static class ClearCacheTableTask extends TimerTask {

        ClearCacheTableTask() {
            new Timer().schedule(this, DELAY_CLEAR_CACHE);
        }

        @Override
        public void run() {
            if (metricCache.size() < CACHE_THRESHOLD) return;
            metricCache.clear();
            new ClearCacheTableTask();
        }
    }

    private static class CheckWebServerProxysTask extends TimerTask {

        CheckWebServerProxysTask() {
            new Timer().schedule(this, DELAY_CHECK_PROXYS);
        }

        @Override
        public void run() {
            for (int i = 0; i < farm.size(); i++) {
                if (farm.get(i).getServerState() != ServerState.INITIALIZING && farm.get(i).isAvailable().getState() == State.DEAD) {
                    farm.remove(i);
                    i -= 1; // We need to check the same position since it will be removed.
                }
            }
            new CheckWebServerProxysTask();
        }
    }

    private static class RetryRequests extends TimerTask {

        RetryRequests() {
            new Timer().schedule(this, DELAY_RETRY, DELAY_RETRY);
        }

        @Override
        public void run() {
            if (failedRequests.isEmpty())
                return;
            else {
                Packet packet = failedRequests.poll();
                System.out.println("Retrying request with url: " + packet.getHttpExchange().getRequestMethod());
                (new LoadBalancerThread(packet.getHttpExchange(), farm, estimator)).start();
            }
        }
    }

}

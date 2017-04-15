package pt.ulisboa.tecnico.meic.cnv;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.UUID;

public class LoadBalancer {
    private static int PORT = 8000;
    private static ArrayList<WebServerProxy> farm = new ArrayList<>();

    public static void main(String[] args) throws ParseException {
        Options options = new Options();
        options.addOption("p", true, "Port to listen for remote requests");
        CommandLine cmd = (new DefaultParser()).parse(options, args);

        try {
            String argPort = cmd.getOptionValue("p");
            if (argPort != null) PORT = Integer.valueOf(argPort);
        }
        catch(RuntimeException e){
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
        server.createContext("/r.html", new MyHandler());
        System.out.println("Autobalancer is running @ port " + PORT);
        server.start();

        final HttpServer finalServer = server;
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                System.out.println("Shutting down load balancer");
                finalServer.stop(1);
            }
        });

        loadCLI();
        System.exit(1);
    }

    private static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            BestThread bestThread = new BestThread(t);
            bestThread.start();
        }
    }

    private static class BestThread extends Thread {
        private HttpExchange httpExchange;
        private UUID uuid = UUID.randomUUID();

        BestThread(HttpExchange httpExchange) {
            this.httpExchange = httpExchange;
        }

        @Override
        public void run() {
            //String response = "This was the query:" + httpExchange.getRequestURI().getQuery() + "&clientId=" + uuid + "##";

            LoadBalancerChoiceStrategy strategy = new LoadBalancerBestChoice();
            WebServerProxy node = strategy.chooseBestNode(farm);

            try {
                long time = System.currentTimeMillis();
                System.out.println("Choosing node " + node.getAddress() + ":" + node.getPort() +
                        " to respond to requestId=" + uuid + ", according to " + strategy.getClass().getSimpleName());
                node.dispatch(httpExchange, uuid);
                System.out.println("RequestId=" + uuid + " completed in " + (System.currentTimeMillis()-time) + " ms");
            }
            catch (ConnectException e){
                System.out.println("Caught ConnectException, removing " + node);
                farm.remove(node);
                node = null;
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                httpExchange.close();
            }

            /*try {
                httpExchange.sendResponseHeaders(200, response.length());
                OutputStream os = httpExchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }*/
        }
    }

    private static void loadCLI(){
        System.out.println("Enter one of the following commands:");
        System.out.println("+ ip:port ~~ adds new webserver to farm");
        System.out.println("- ip:port ~~ removes webserver from farm");
        System.out.println("list");
        System.out.println("q - quit");
        Scanner scanchoice = new Scanner(System.in);
        System.out.println();
        String line;

        label:
        try {
            while ((line = scanchoice.nextLine()) != null) {
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
                                    System.out.println("Node already known");
                                    continue;
                                }
                                if (wsp.isAvailable()) {
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
                                if(farm.contains(wsp)){
                                    farm.remove(wsp);
                                    System.out.println("Removed node from farm");
                                }
                                else
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
        }
        catch(RuntimeException e){

        }
    }
}

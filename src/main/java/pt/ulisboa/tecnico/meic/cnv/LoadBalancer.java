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
import java.util.ArrayList;
import java.util.Scanner;
import java.util.UUID;

public class LoadBalancer {
    private static int PORT = 8000;
    private static ArrayList<WebServerProxy> farm = new ArrayList<>();

    public static void main(String[] args) throws IOException, ParseException {
        Options options = new Options();
        options.addOption("p", true, "Port to listen for remote requests");
        CommandLine cmd = (new DefaultParser()).parse(options, args);

        String port = cmd.getOptionValue("p");
        if(port != null) PORT = Integer.valueOf(port);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/r.html", new MyHandler());
        System.out.println("Autobalancer is running @ port " + PORT);
        server.start();

        System.out.println("Enter one of the following commands:");
        System.out.println("+ ip:port ~~ adds new webserver to farm");
        System.out.println("- ip:port ~~ removes webserver from farm");
        System.out.println("q - quit");
        Scanner scanchoice = new Scanner(System.in);
        System.out.println();
        String line;

        label:
        while ((line = scanchoice.nextLine()) != null) {
            String option = line.split(" ")[0];
            String[] servers = line.split(" ")[1].split(",");

            switch (option) {
                case "q":
                    break label;
                case "+":
                    for(String s: servers) {
                        farm.add(new WebServerProxy("http://" + s));
                        System.out.println("Added node to farm");
                    }
                    break;
                case "-":
                    for(String s: servers) {
                        for (WebServerProxy wsp : farm) {
                            if (wsp.getRemoteAddress().equals("http://" + s)) {
                                farm.remove(wsp);
                                System.out.println("Removed node from farm");
                                break;
                            } else
                                System.out.println("Node not found");
                        }
                    }
                    break;
                default:
                    System.out.println("Unknown option");
                    break;
            }

        }
        server.stop(1);
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

            LoadBalancerChoiceStrategy strategy = new LoadBalancerLRUChoice();
            WebServerProxy node = strategy.chooseBestNode(farm);

            try {
                long time = System.currentTimeMillis();
                System.out.println("Choosing node " + node.getRemoteAddress() + " to respond to requestId=" + uuid + ", according to " + strategy.getClass().getSimpleName());
                node.dispatch(httpExchange, uuid);
                System.out.println("RequestId=" + uuid + " completed in " + (System.currentTimeMillis()-time) + " ms");
            } catch (IOException e) {
                e.printStackTrace();
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
}

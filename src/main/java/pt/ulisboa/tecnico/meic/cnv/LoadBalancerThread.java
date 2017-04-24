package pt.ulisboa.tecnico.meic.cnv;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.TreeMap;

public class LoadBalancerThread extends Thread {
    private HttpExchange httpExchange;
    private ArrayList<WebServerProxy> farm;

    LoadBalancerThread(HttpExchange httpExchange, ArrayList<WebServerProxy> farm) {
        this.httpExchange = httpExchange;
        this.farm = farm;
    }

    @Override
    public void run() {
        TreeMap<String, String> p = new TreeMap<>();
        parseRequest(httpExchange.getRequestURI().getQuery(), p);

        Request request = new Request(p.get("f"), Integer.valueOf(p.get("sc")), Integer.valueOf(p.get("sr")),
                Integer.valueOf(p.get("wc")), Integer.valueOf(p.get("wr")), Integer.valueOf(p.get("coff")),
                Integer.valueOf(p.get("roff")));

        LoadBalancerChoiceStrategy strategy = new LoadBalancerBestChoice();
        WebServerProxy node = strategy.chooseBestNode(farm, request);

        try {
            System.out.println("[Received] " + request.getId() + " sent to " + node.getRemoteURL() + " -> " + request);
            node.dispatch(httpExchange, request);
            System.out.println("[Completed] " + request.getId() + " finished in " +
                    (System.currentTimeMillis()-request.getTimestamp().getTime()) + " ms");
        }
        catch (ConnectException e){
            System.out.println("Caught ConnectException, removing " + node.getRemoteURL() + " from farm");
            farm.remove(node);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            httpExchange.close();
        }
    }

    private static void parseRequest(String query, TreeMap<String, String> paramsMap) {
        String[] args = query.split("&");
        for (String arg : args) {
            String[] parameters = arg.split("=");
            if (parameters.length == 2) {
                paramsMap.put(parameters[0], parameters[1]);
            }
        }
    }
}

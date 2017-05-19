package pt.ulisboa.tecnico.meic.cnv;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

import static pt.ulisboa.tecnico.meic.cnv.State.DEAD;

public class LoadBalancerThread extends Thread {

    private static final List<String> VALID_MODELS = Arrays.asList("test01.txt", "test02.txt", "test03.txt",
            "test04.txt", "test05.txt", "test-texmap.txt", "wood.txt");
    private HttpExchange httpExchange;
    private List<WebServerProxy> farm;
    private Map<String, Estimator> estimators;
    private RepositoryService repositoryService;

    LoadBalancerThread(HttpExchange httpExchange, List<WebServerProxy> farm, Map<String, Estimator> estimators) {
        this.httpExchange = httpExchange;
        this.farm = farm;
        this.estimators = estimators;
    }

    private static Map<String, String> parseRequest(String query) {
        TreeMap<String, String> paramsMap = new TreeMap<>();
        String[] args = query.split("&");
        for (String arg : args) {
            String[] parameters = arg.split("=");
            if (parameters.length == 2) {
                paramsMap.put(parameters[0], parameters[1]);
            }
        }
        if (!VALID_MODELS.contains(paramsMap.get("m"))) throw new RuntimeException();
        return paramsMap;
    }

    @Override
    public void run() {
        Request request = null;
        try {
            request = new Request(parseRequest(httpExchange.getRequestURI().getQuery()));
        } catch (RuntimeException e) {
            System.out.println("Received invalid request, ignoring");
            String response = "Invalid image parameters";
            try {
                httpExchange.sendResponseHeaders(400, response.length());
                OutputStream os = httpExchange.getResponseBody();
                os.write(response.getBytes());
                os.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            return;
        }

        Estimator estimator = estimators.get(request.getArgument().getModel());

        LoadBalancerChoiceStrategy strategy = new LoadBalancerBestChoice(farm, estimator);
        WebServerProxy node = strategy.chooseBestNode(request);
        node.getActiveJobs().add(request);


        if (!farm.contains(node)) {
            node.setServerState(ServerState.INITIALIZING);
            farm.add(node);
        }

        // Carefully think about the node that came, watch for the state....
        long milis = System.currentTimeMillis();
        while (!ScalerService.getInstance().checkIfReady(node.getRemoteURL())) {
            long l = ThreadLocalRandom.current().nextLong(1, 10);
            try {
                Thread.sleep(l * 1000);
            } catch (InterruptedException ignored) {
            }
            // if in 2 minutes the node doesn't come up just shift to another one
            if (System.currentTimeMillis() - milis == 120000) {
                LoadBalancer.failedRequests.add(new Packet(httpExchange, request));
                farm.remove(node);
                break;
            }
        }

        // even if the node is ready to serve, initializing nodes need to be set to ready
        node.setServerState(ServerState.READY);

        System.out.println("[Received] " + request.getId() + " sent to " + node.getRemoteURL() + " -> " + request);
        try {
            node.dispatch(httpExchange, request);
            System.out.println("[Completed] " + request.getId() + " finished in " +
                    (System.currentTimeMillis() - request.getTimestamp()) + " ms");
        } catch (IOException e) {
            System.out.println("[Aborted] " + request.getId() + " aborted due to " + e.getClass().getSimpleName());
            if (node.isAvailable().getState() == DEAD) {
                farm.remove(node);
            }
            LoadBalancer.failedRequests.add(new Packet(httpExchange, request));
        } finally {
            httpExchange.close();
        }
    }
}

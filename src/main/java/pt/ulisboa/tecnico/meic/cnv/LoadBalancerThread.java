package pt.ulisboa.tecnico.meic.cnv;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static pt.ulisboa.tecnico.meic.cnv.State.DEAD;

public class LoadBalancerThread extends Thread {
    private static final List<String> VALID_MODELS = Arrays.asList("test01.txt", "test02.txt", "test03.txt",
            "test04.txt", "test05.txt", "test-texmap.txt", "wood.txt");
    private HttpExchange httpExchange;
    private List<WebServerProxy> farm;
    private Map<Argument, Metric> metricCache;
    private Map<String, Estimator> estimators;
    private RepositoryService repositoryService;

    LoadBalancerThread(HttpExchange httpExchange, List<WebServerProxy> farm, Map<Argument, Metric> metricCache,
                       Map<String, Estimator> estimators, RepositoryService repositoryService) {
        this.httpExchange = httpExchange;
        this.farm = farm;
        this.metricCache = metricCache;
        this.estimators = estimators;
        this.repositoryService = repositoryService;
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

        LoadBalancerChoiceStrategy strategy = new LoadBalancerBestChoice(farm, metricCache, estimator, repositoryService);
        WebServerProxy node = strategy.chooseBestNode(request);

        // TODO : Carefully think about the node that came, watch for state....

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
        } finally {
            httpExchange.close();
        }
    }
}

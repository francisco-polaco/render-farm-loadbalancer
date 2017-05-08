package pt.ulisboa.tecnico.meic.cnv;

public interface LoadBalancerChoiceStrategy {
    WebServerProxy chooseBestNode(Request request);
}

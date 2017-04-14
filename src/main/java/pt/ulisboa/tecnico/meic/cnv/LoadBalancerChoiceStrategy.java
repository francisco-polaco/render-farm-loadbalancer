package pt.ulisboa.tecnico.meic.cnv;

import java.util.List;

public interface LoadBalancerChoiceStrategy {
    WebServerProxy chooseBestNode(List<WebServerProxy> farm);
}

package pt.ulisboa.tecnico.meic.cnv;

import java.util.List;

public class LoadBalancerBestChoice implements LoadBalancerChoiceStrategy {
    @Override
    public WebServerProxy chooseBestNode(List<WebServerProxy> farm, Request request) {
        for(WebServerProxy wsp: farm){
            if(wsp.hasNoActiveJobs()) return wsp;
        }
        return new LoadBalancerLRUChoice().chooseBestNode(farm, request);
    }
}

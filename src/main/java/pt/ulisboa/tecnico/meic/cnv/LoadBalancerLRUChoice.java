package pt.ulisboa.tecnico.meic.cnv;

import java.util.List;

public class LoadBalancerLRUChoice implements LoadBalancerChoiceStrategy {
    @Override
    public WebServerProxy chooseBestNode(List<WebServerProxy> farm, Request request) {
        WebServerProxy minimum = null;

        for(WebServerProxy wsp: farm){
            if(minimum == null)
                minimum = wsp;
            else if(wsp.getLastTimeUsed() == null){
                minimum = wsp;
            }
            else
                if(wsp.getLastTimeUsed().before(minimum.getLastTimeUsed()))
                    minimum = wsp;
        }

        return minimum;
    }
}

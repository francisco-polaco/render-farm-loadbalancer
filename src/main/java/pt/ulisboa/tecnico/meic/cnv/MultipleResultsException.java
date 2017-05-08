package pt.ulisboa.tecnico.meic.cnv;

import java.util.List;

/**
 * Created by francisco on 08/05/2017.
 */
public class MultipleResultsException extends Exception {
    private List<Metric> multipleResults;

    public MultipleResultsException(List<Metric> multipleResults) {
        super("Multiple results returned. List:\n" + multipleResults);
        this.multipleResults = multipleResults;
    }


    public List<Metric> getMultipleResults() {
        return multipleResults;
    }
}

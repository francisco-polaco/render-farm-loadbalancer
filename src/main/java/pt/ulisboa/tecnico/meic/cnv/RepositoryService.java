package pt.ulisboa.tecnico.meic.cnv;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RepositoryService {
    private static final String TABLE_NAME = "Metrics";
    private final long READ_CAPACITY = 1L;
    private final long WRITE_CAPACITY = 1L;
    private final String PRIMARY_KEY_NAME = "id";
    private final AmazonDynamoDB repository;

    public RepositoryService() {
        AWSCredentials credentials;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        repository = AmazonDynamoDBClientBuilder.standard().withRegion("eu-west-1").withCredentials(
                new AWSStaticCredentialsProvider(credentials)).build();
    }

    //Given a argument, returns a Metric corresponding to f, sc, sr, wc, wr, coff, roff
    //return null if not found
    public List<Metric> getGeneralMetrics(Argument argument) {
        HashMap<String, Condition> scanFilter = new HashMap<>();
        // Getting a condition ready
        scanFilter.put("file", new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withS(argument.getModel())));

        if (argument.getSceneColumns() != -1)
            scanFilter.put("sc", new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withS(String.valueOf(argument.getSceneColumns()))));

        if (argument.getSceneRows() != -1)
            scanFilter.put("sr", new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withS(String.valueOf(argument.getSceneRows()))));

        if (argument.getWindowColumns() != -1)
            scanFilter.put("wc", new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withS(String.valueOf(argument.getWindowColumns()))));

        if (argument.getWindowRows() != -1)
            scanFilter.put("wr", new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withS(String.valueOf(argument.getWindowRows()))));

        if (argument.getColumnOffset() != -1)
            scanFilter.put("coff", new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withS(String.valueOf(argument.getColumnOffset()))));

        if (argument.getRowOffset() != -1)
            scanFilter.put("roff", new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withS(String.valueOf(argument.getRowOffset()))));

        return query(scanFilter);
    }

    public List<Metric> getDiogoMetrics(Argument argument, ComparisonOperator wcComparator,
                                        ComparisonOperator wrComparator) {
        HashMap<String, Condition> scanFilter = new HashMap<>();
        // Getting a condition ready
        scanFilter.put("file", new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withS(argument.getModel())));

        if (argument.getWindowColumns() != -1)
            scanFilter.put("wc", new Condition()
                    .withComparisonOperator(wcComparator.toString())
                    .withAttributeValueList(new AttributeValue().withS(String.valueOf(argument.getWindowColumns()))));

        if (argument.getWindowRows() != -1)
            scanFilter.put("wr", new Condition()
                    .withComparisonOperator(wrComparator.toString())
                    .withAttributeValueList(new AttributeValue().withS(String.valueOf(argument.getWindowRows()))));

        return query(scanFilter);
    }

    public Metric getMetric(Argument argument) {
        final List<Metric> metricDiogo = getGeneralMetrics(argument);
        if (metricDiogo.size() == 0) return null;
        else return metricDiogo.get(0);
    }

    private List<Metric> query(HashMap<String, Condition> scanFilter) {
        ScanRequest scanRequest = new ScanRequest(TABLE_NAME).withScanFilter(scanFilter);
        ScanResult scanResult = repository.scan(scanRequest);

        List<Map<String, AttributeValue>> queryResult = scanResult.getItems();
        List<Metric> metricsToReturn = new ArrayList<>();
        for (Map<String, AttributeValue> element : queryResult) {
            // Unfortunately we need to build things using strings
            metricsToReturn.add(new Metric(Long.valueOf(element.get("m_count").getS()),
                    Long.valueOf(element.get("taken").getS()),
                    Long.valueOf(element.get("not_taken").getS())));
        }
        System.out.println(metricsToReturn);
        return metricsToReturn;
    }

    //Given an argument, returns a Metric list corresponding to f, used to support Estimator
    public Map<Argument, Metric> getMetrics(Argument argument) {
        // Scan items for metrics with a file attribute equal to argument.file
        HashMap<String, Condition> scanFilter = new HashMap<>();
        // Getting a condition ready
        Condition condition = new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withS(argument.getModel())); //S means string
        scanFilter.put("file", condition); // we want to filter by file
        ScanRequest scanRequest = new ScanRequest(TABLE_NAME).withScanFilter(scanFilter);
        ScanResult scanResult = repository.scan(scanRequest);

        List<Map<String, AttributeValue>> queryResult = scanResult.getItems();
        HashMap<Argument, Metric> toRet = new HashMap<>();
        for (Map<String, AttributeValue> element : queryResult) {
            // Unfortunately we need to build things using strings
            toRet.put(new Argument(element.get("file").toString(), Integer.valueOf(element.get("sc").getS()),
                            Integer.valueOf(element.get("sr").getS()), Integer.valueOf(element.get("wc").getS()),
                            Integer.valueOf(element.get("wr").getS()), Integer.valueOf(element.get("roff").getS()),
                            Integer.valueOf(element.get("coff").getS())),
                    new Metric(Long.valueOf(element.get("m_count").getS()),
                            Long.valueOf(element.get("taken").getS()),
                            Long.valueOf(element.get("not_taken").getS())));
        }
        System.out.println(toRet);
        return toRet;
    }

    // Delete this if not needed
    /*private boolean equal(Argument argument, Map<String, AttributeValue> element){
        return argument.getModel().equalsIgnoreCase(element.get("file").toString()) &&
                argument.getSceneColumns() == Integer.valueOf(element.get("sc").toString()) &&
                argument.getSceneRows() == Integer.valueOf(element.get("sr").toString()) &&
                argument.getWindowColumns() == Integer.valueOf(element.get("wc").toString()) &&
                argument.getWindowRows() == Integer.valueOf(element.get("wr").toString()) &&
                argument.getColumnOffset() == Integer.valueOf(element.get("coff").toString()) &&
                argument.getRowOffset() == Integer.valueOf(element.get("roff").toString());

    }*/
}

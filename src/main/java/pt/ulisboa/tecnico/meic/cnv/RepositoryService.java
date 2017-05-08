package pt.ulisboa.tecnico.meic.cnv;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;

import java.lang.reflect.Field;
import java.util.*;

public class RepositoryService {
    private final String TABLE_NAME = "Metrics";
    private final long READ_CAPACITY = 1L;
    private final long WRITE_CAPACITY = 1L;
    private final String PRIMARY_KEY_NAME = "id";
    private final AmazonDynamoDB repository;
    private DynamoDB db;

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
        // db = new DynamoDB(new AmazonDynamoDBClient(credentials));
    }

    //Given a argument, returns a Metric corresponding to f, sc, sr, wc, wr, coff, roff
    //return null if not found
    //TODO
    public Metric getMetric(Argument argument) {
        // GetItemRequest getItemRequest = new GetItemRequest(TABLE_NAME, )
        /*argument.get
        repository.getItem()*/
        //repository.getItem()
        return new Metric(1, 2, 3);
    }

    public void addMetric(String id, Metric metric) {
        Map<String, AttributeValue> item = newItem(id, metric);
        PutItemRequest putItemRequest = new PutItemRequest(TABLE_NAME, item);
        PutItemResult putItemResult = repository.putItem(putItemRequest);
        if (putItemResult.toString().equals("{}"))
            System.out.println("Successfully added item with id = " + id + ", and Metric = " + metric);
        else
            System.out.println("Result: " + putItemResult);
    }

    private Map<String, AttributeValue> newItem(String id, Metric metric) {
        Map<String, AttributeValue> items = new LinkedHashMap<>();
        items.put(PRIMARY_KEY_NAME, new AttributeValue(id));
        for (Field field : metric.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                items.put(field.getName(), new AttributeValue(field.get(metric).toString()));
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return items;
    }

    //Given an argument, returns a Metric list corresponding to f, used to support Estimator
    public List<Metric> getMetrics(Argument argument) {
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
        List<Metric> metricsToReturn = new
                ArrayList<>();
        for (Map<String, AttributeValue> element : queryResult) {
            // Unfortunately we need to build things using strings
            metricsToReturn.add(new Metric(Double.valueOf(element.get("m_count").getS()),
                    Double.valueOf(element.get("taken").getS()),
                    Double.valueOf(element.get("not_taken").getS())));
        }
        System.out.println(metricsToReturn);
        return metricsToReturn;
    }
}

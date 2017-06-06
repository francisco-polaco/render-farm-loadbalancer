package pt.ulisboa.tecnico.meic.cnv;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

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
        createTable();
    }

    private void createTable() {
        CreateTableRequest createTableRequest =
                new CreateTableRequest().withTableName(TABLE_NAME)
                        .withKeySchema(new KeySchemaElement().withAttributeName(PRIMARY_KEY_NAME).withKeyType(KeyType.HASH))
                        .withAttributeDefinitions(new AttributeDefinition().withAttributeName(PRIMARY_KEY_NAME).withAttributeType(ScalarAttributeType.S))
                        .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(READ_CAPACITY).withWriteCapacityUnits(WRITE_CAPACITY));

        // Create table if it does not exist yet
        TableUtils.createTableIfNotExists(repository, createTableRequest);
        // wait for the table to move into ACTIVE state
        try {
            TableUtils.waitUntilActive(repository, TABLE_NAME);
        } catch (InterruptedException e) {
            exit("Error creating the Metrics table in DynamoDB!");
        }
        System.out.println("Connected do DynamoDB!");
    }

    private void exit(String msg) {
        System.err.println(msg);
        System.exit(0);
    }

    public Map<Argument, Metric> getMetricsEstimator(Argument argument) {
        HashMap<String, Condition> scanFilter = new HashMap<>();

        Condition condition = new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withS(argument.getModel()));

        scanFilter.put("model", condition); // we want to filter by file


        ScanRequest scanRequest = new ScanRequest(TABLE_NAME).withScanFilter(scanFilter);
        ScanResult scanResult = repository.scan(scanRequest);

        List<Map<String, AttributeValue>> queryResult = scanResult.getItems();


        return extractMetric(queryResult);
    }

    private Map<Argument, Metric> extractMetric(List<Map<String, AttributeValue>> queryResult) {
        HashMap<Argument, Metric> output = new HashMap<>();
        for (Map<String, AttributeValue> element : queryResult) {
            output.put(new Argument(element.get("model").toString(),
                            Integer.valueOf(element.get("sc").getS()),
                            Integer.valueOf(element.get("sr").getS()),
                            Integer.valueOf(element.get("wc").getS()),
                            Integer.valueOf(element.get("wr").getS()),
                            Integer.valueOf(element.get("roff").getS()),
                            Integer.valueOf(element.get("coff").getS())),

                    new Metric(Long.valueOf(element.get("m_count").getS()),
                            Long.valueOf(element.get("taken").getS()),
                            Long.valueOf(element.get("not_taken").getS())));
        }
        return output;
    }

    public Metric getCachedMetric(String url) {
        HashMap<String, Condition> scanFilter = new HashMap<>();

        Condition condition = new Condition()
                .withComparisonOperator(ComparisonOperator.EQ.toString())
                .withAttributeValueList(new AttributeValue().withS(url));

        scanFilter.put("id", condition);

        ScanRequest scanRequest = new ScanRequest(TABLE_NAME).withScanFilter(scanFilter);
        ScanResult scanResult = repository.scan(scanRequest);

        List<Map<String, AttributeValue>> queryResult = scanResult.getItems();

        Map<Argument, Metric> argumentMetricMap = extractMetric(queryResult);
        for (Argument argument : argumentMetricMap.keySet()) {
            return argumentMetricMap.get(argument);
        }
        return null;
    }


}

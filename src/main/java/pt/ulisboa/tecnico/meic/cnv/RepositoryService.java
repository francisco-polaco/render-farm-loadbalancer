package pt.ulisboa.tecnico.meic.cnv;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;

import java.util.List;

public class RepositoryService {
    private final String TABLE_NAME = "Metrics";
    private final long READ_CAPACITY = 1L;
    private final long WRITE_CAPACITY = 1L;
    private final String PRIMARY_KEY_NAME = "id";
    private final AmazonDynamoDB repository;

    public RepositoryService() {
        AWSCredentials credentials = null;
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
    //TODO
    public Metric getMetric(Argument argument) {
        return new Metric(1, 2, 3);
    }

    //Given an argument, returns a Metric list corresponding to f, used to support Estimator
    //TODO
    public List<Metric> getMetrics(Argument argument) {
        return null;
    }
}

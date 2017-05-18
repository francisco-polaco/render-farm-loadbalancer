package pt.ulisboa.tecnico.meic.cnv;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.Datapoint;
import com.amazonaws.services.cloudwatch.model.Dimension;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsRequest;
import com.amazonaws.services.cloudwatch.model.GetMetricStatisticsResult;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;

import java.util.*;

public class ScalerService {

    private static final ScalerService ourInstance = new ScalerService();

    // this examples are purely theoretical, we need to to consensus on this
    private static final String IMAGE_ID = "ami-d18b83b7";
    private static final String INSTANCE_TYPE = "t2.micro";
    private static final String SECURITY_GROUP = "CNV-ssh+8000+80";
    private static final String KEY_NAME = "cnv-proj";

    private AmazonEC2 ec2;
    private AmazonCloudWatch cloudWatch;

    private ScalerService() {
        init();
    }

    public static ScalerService getInstance() {
        return ourInstance;
    }

    private void init() {
        AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            System.err.println("");
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }
        ec2 = AmazonEC2ClientBuilder.standard().withRegion("eu-west-1").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

        cloudWatch = AmazonCloudWatchClientBuilder.standard().withRegion("eu-west-1").withCredentials(new AWSStaticCredentialsProvider(credentials)).build();
    }

    public void startInstances(List<String> instanceIds) {
        System.out.println("Starting instances...");
        for (String id : instanceIds)
            System.out.println("Starting: " + id);

        StartInstancesRequest startInstancesRequest = new StartInstancesRequest();

        startInstancesRequest = startInstancesRequest.withInstanceIds(instanceIds);
        ec2.startInstances(startInstancesRequest);
    }

    public void stopInstances(List<String> instanceIds) {
        System.out.println("Stopping instances...");
        for (String id : instanceIds)
            System.out.println("Stopping: " + id);

        StopInstancesRequest stopInstancesRequest = new StopInstancesRequest();

        stopInstancesRequest = stopInstancesRequest.withInstanceIds(instanceIds);
        ec2.stopInstances(stopInstancesRequest);
    }


    public void terminateInstances(List<String> instanceIds) {
        System.out.println("Terminating instances...");
        for (String id : instanceIds)
            System.out.println("Terminating: " + id);

        TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest();

        terminateInstancesRequest = terminateInstancesRequest.withInstanceIds(instanceIds);
        ec2.terminateInstances(terminateInstancesRequest);
    }

    public List<Instance> createInstance(int min, int max) {
        System.out.println("Starting min: " + min + " and max: " + max + " instances");
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest();

        runInstancesRequest.withImageId(IMAGE_ID)
                .withInstanceType(INSTANCE_TYPE)
                .withMinCount(min)
                .withMaxCount(max)
                .withKeyName(KEY_NAME)
                .withMonitoring(true)
                .withSecurityGroups(SECURITY_GROUP);

        RunInstancesResult runInstancesResult = ec2.runInstances(runInstancesRequest);
        return runInstancesResult.getReservation().getInstances();
    }

    public List<Instance> getAllInstances() {
        DescribeInstancesResult describeInstancesResult = ec2.describeInstances();
        List<Reservation> reservations = describeInstancesResult.getReservations();
        Set<Instance> instances = new HashSet<Instance>();

        System.out.println("Total reservations = " + reservations.size());

        for (Reservation reservation : reservations)
            instances.addAll(reservation.getInstances());

        System.out.println("Total instances = " + instances.size());

        return new ArrayList<>(instances);
    }

    public List<Instance> getInstancesInState(String state) {
        DescribeInstancesResult describeInstancesResult = ec2.describeInstances();
        List<Reservation> reservations = describeInstancesResult.getReservations();
        Set<Instance> instances = new HashSet<Instance>();

        for (Reservation reservation : reservations) {
            instances.addAll(reservation.getInstances());
        }

        Set<Instance> toRemove = new HashSet<Instance>();
        for (Instance instance : instances) {
            if (!instance.getState().getName().equalsIgnoreCase(state))
                toRemove.add(instance);
        }
        instances.removeAll(toRemove);

        System.out.print("Instances in state " + state + " with ids: " + prettyPrintInstances(instances));


        return new ArrayList<>(instances);
    }


    // Statistic can be per example CPUUtilization
    // Function can be per example Average
    public double retrieveEC2Statistic(Instance instance, String statistic, String function) throws Exception {
        double stat = 0.0;
        try {
            /* TODO total observation time in milliseconds */
            long offsetInMilliseconds = 1000 * 60 * 10;
            Dimension instanceDimension = new Dimension();
            instanceDimension.setName("InstanceId");
            List<Dimension> dims = new ArrayList<Dimension>();
            dims.add(instanceDimension);

            String name = instance.getInstanceId();
            String state = instance.getState().getName();
            if (state.equals("running")) {
                System.out.println("running instance id = " + name);
                instanceDimension.setValue(name);
                GetMetricStatisticsRequest request = new GetMetricStatisticsRequest()
                        .withStartTime(new Date(new Date().getTime() - offsetInMilliseconds))
                        .withNamespace("AWS/EC2")
                        .withPeriod(60)
                        .withMetricName(statistic)
                        .withStatistics(function)
                        .withDimensions(instanceDimension)
                        .withEndTime(new Date());
                GetMetricStatisticsResult getMetricStatisticsResult = cloudWatch.getMetricStatistics(request);
                List<Datapoint> datapoints = getMetricStatisticsResult.getDatapoints();
                for (Datapoint dp : datapoints) {
                    stat += dp.getAverage();
                }
                return stat;
            } else {
                System.out.println("instance id = " + name);
            }
            System.out.println("Instance State : " + state + ".");

        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
        }
        return stat;
    }


    private String prettyPrintInstances(Collection<Instance> instances) {
        StringBuilder stringBuilder = new StringBuilder();
        Iterator<Instance> iterator = instances.iterator();
        while (iterator.hasNext()) {
            Instance instance = iterator.next();
            stringBuilder.append(!iterator.hasNext() ? instance.getInstanceId() + "\n" : instance.getInstanceId() + ", ");
        }
        return stringBuilder.toString();
    }

    public List<Instance> reuse() {
        List<Instance> output = new ArrayList<>();
        for (Instance instance : getAllInstances()) {
            if (instance.getState().getName().equalsIgnoreCase("stopped") ||
                    instance.getState().getName().equalsIgnoreCase("stopping") ||
                    instance.getState().getName().equalsIgnoreCase("pending")) {
                output.add(instance);
            }
        }
        return output;
    }


}
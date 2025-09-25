package org.openas2.processor.msgtracking;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider;
import com.amazonaws.endpoints.AccountIdEndpointMode;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;

import java.util.List;
import java.util.Map;

public class DynamoDbTrackingModule extends BaseMsgTrackingModule implements TrackingModule {

    public DynamoDbTrackingModule() {
        super();
        ClientConfiguration config = new ClientConfiguration().withAccountIdEndpointMode(AccountIdEndpointMode.DISABLED);
        AWSCredentialsProvider credentialsProvider = new EnvironmentVariableCredentialsProvider();

        AmazonDynamoDB dynamodb = AmazonDynamoDBClientBuilder.standard().withClientConfiguration(config).withCredentials(credentialsProvider).withRegion(Regions.US_EAST_1).build();
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public void start() throws OpenAS2Exception {

    }

    @Override
    public void stop() throws OpenAS2Exception {

    }

    @Override
    public boolean healthcheck(List<String> failures) {
        return false;
    }

    @Override
    protected void persist(Message msg, Map<String, String> map) {

    }
}

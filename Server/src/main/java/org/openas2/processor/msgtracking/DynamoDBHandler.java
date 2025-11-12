/* Copyright Uhuru Technology 2016 https://www.uhurutechnology.com
 * Distributed under the GPLv3 license or a commercial license must be acquired.
 */
package org.openas2.processor.msgtracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.OpenAS2Exception;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import jakarta.annotation.Nullable;

import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * DynamoDB handler for message tracking.
 * Implements the IDBHandler interface to provide DynamoDB as a backend
 * for storing AS2 message metadata.
 */
class DynamoDBHandler implements IDBHandler {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDBHandler.class);

    @Nullable
    private DynamoDbClient dynamoDbClient = null;

    private String tableName = null;
    private String awsRegion = null;

    /**
     * Creates DynamoDB client with provided credentials and configuration.
     *
     * @param connectString Not used for DynamoDB (table name passed in params)
     * @param userName AWS Access Key ID (can be null to use default credentials)
     * @param pwd AWS Secret Access Key (can be null to use default credentials)
     * @throws OpenAS2Exception if client creation fails
     */
    @Override
    public void createConnectionPool(String connectString, String userName, String pwd) throws OpenAS2Exception {
        if (dynamoDbClient != null) {
            throw new OpenAS2Exception(
                "DynamoDB client already initialized. Cannot create a new client. Stop current one first.");
        }

        try {
            DynamoDbClientBuilder builder = DynamoDbClient.builder();

            // Set region
            if (awsRegion != null && !awsRegion.isEmpty()) {
                builder.region(Region.of(awsRegion));
            } else {
                // Use default region from environment/config
                logger.warn("No AWS region specified, using default region provider");
            }

            // Set credentials
            if (userName != null && !userName.isEmpty() && pwd != null && !pwd.isEmpty()) {
                AwsBasicCredentials credentials = AwsBasicCredentials.create(userName, pwd);
                builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
                logger.info("Using provided AWS credentials");
            } else {
                // Use default credentials chain (environment variables, instance profile, etc.)
                builder.credentialsProvider(DefaultCredentialsProvider.create());
                logger.info("Using default AWS credentials provider chain");
            }

            dynamoDbClient = builder.build();
            logger.info("DynamoDB client initialized successfully for region: " + awsRegion);

        } catch (Exception e) {
            throw new OpenAS2Exception("Failed to initialize DynamoDB client: " + e.getMessage(), e);
        }
    }

    /**
     * Initializes the DynamoDB handler with configuration parameters.
     *
     * @param jdbcConnectString Not used for DynamoDB
     * @param dbUser AWS Access Key ID (can be null)
     * @param dbPwd AWS Secret Access Key (can be null)
     * @param params Configuration parameters including table_name, aws_region, dynamodb_endpoint
     * @throws OpenAS2Exception if initialization fails
     */
    @Override
    public void start(String jdbcConnectString, String dbUser, String dbPwd, Map<String, String> params)
            throws OpenAS2Exception {

        // Extract DynamoDB-specific parameters
        tableName = params.get("table_name");
        if (tableName == null || tableName.isEmpty()) {
            tableName = "msg_metadata"; // Default table name
        }

        awsRegion = params.get("aws_region");
        if (awsRegion == null || awsRegion.isEmpty()) {
            awsRegion = "us-east-1"; // Default region
            logger.warn("No aws_region parameter specified, defaulting to us-east-1");
        }

        // Create the client
        createConnectionPool(jdbcConnectString, dbUser, dbPwd);

        // If custom endpoint is specified (for local DynamoDB testing)
        String endpoint = params.get("dynamodb_endpoint");
        if (endpoint != null && !endpoint.isEmpty()) {
            logger.info("Using custom DynamoDB endpoint: " + endpoint);
            dynamoDbClient.close();
            dynamoDbClient = DynamoDbClient.builder()
                .region(Region.of(awsRegion))
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(dbUser != null && dbPwd != null
                    ? StaticCredentialsProvider.create(AwsBasicCredentials.create(dbUser, dbPwd))
                    : DefaultCredentialsProvider.create())
                .build();
        }

        logger.info("DynamoDB handler started with table: " + tableName + " in region: " + awsRegion);
    }

    /**
     * Stops the DynamoDB handler and releases resources.
     */
    @Override
    public void stop() {
        destroyConnectionPool();
    }

    /**
     * Closes the DynamoDB client and releases resources.
     */
    @Override
    public void destroyConnectionPool() {
        if (dynamoDbClient != null) {
            try {
                dynamoDbClient.close();
                logger.info("DynamoDB client closed successfully");
            } catch (Exception e) {
                logger.error("Error closing DynamoDB client", e);
            } finally {
                dynamoDbClient = null;
            }
        }
    }

    /**
     * Returns the DynamoDB client.
     * Note: This method signature is maintained for interface compatibility,
     * but returns null as DynamoDB doesn't use JDBC connections.
     * Use getDynamoDbClient() instead.
     *
     * @return null (DynamoDB doesn't use JDBC Connection)
     * @throws SQLException Not thrown
     * @throws OpenAS2Exception if client is not initialized
     */
    @Override
    public Connection getConnection() throws SQLException, OpenAS2Exception {
        if (dynamoDbClient == null) {
            throw new OpenAS2Exception("DynamoDB client not initialized");
        }
        // Return null as DynamoDB doesn't use JDBC connections
        // The actual DynamoDB client can be accessed via getDynamoDbClient()
        return null;
    }

    /**
     * Gets the DynamoDB client for performing operations.
     *
     * @return the DynamoDB client
     * @throws OpenAS2Exception if client is not initialized
     */
    public DynamoDbClient getDynamoDbClient() throws OpenAS2Exception {
        if (dynamoDbClient == null) {
            throw new OpenAS2Exception("DynamoDB client not initialized");
        }
        return dynamoDbClient;
    }

    /**
     * Gets the configured table name.
     *
     * @return the DynamoDB table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Shuts down the DynamoDB client.
     *
     * @param connectString Not used for DynamoDB
     * @return true if shutdown was successful
     * @throws SQLException Not thrown
     * @throws OpenAS2Exception if shutdown fails
     */
    @Override
    public boolean shutdown(String connectString) throws SQLException, OpenAS2Exception {
        destroyConnectionPool();
        return true;
    }
}

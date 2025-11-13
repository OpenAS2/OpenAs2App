/* Copyright Uhuru Technology 2016 https://www.uhurutechnology.com
 * Distributed under the GPLv3 license or a commercial license must be acquired.
 */
package org.openas2.processor.msgtracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.message.Message;
import org.openas2.util.DateUtil;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DynamoDB-based message tracking module.
 * Stores AS2 message metadata in Amazon DynamoDB.
 */
public class DynamoDBTrackingModule extends BaseMsgTrackingModule {

    public static final String PARAM_AWS_ACCESS_KEY = "aws_access_key_id";
    public static final String PARAM_AWS_SECRET_KEY = "aws_secret_access_key";
    public static final String PARAM_CONSISTENT_READ = "consistent_read";

    private static final Logger logger = LoggerFactory.getLogger(DynamoDBTrackingModule.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private DynamoDBHandler dbHandler = null;
    private DynamoDbTable<MessageMetadata> table = null;
    private String tableName;
    private boolean consistentRead = false; // Optional: defaults to eventually consistent if not specified
    private boolean isRunning = false;

    @Override
    public void init(Session session, Map<String, String> options) throws OpenAS2Exception {
        super.init(session, options);
        tableName = getParameter(DbTrackingModule.PARAM_TABLE_NAME, "msg_metadata");

        // Configure read consistency (optional - defaults to eventually consistent if not specified)
        String consistentReadParam = getParameter(PARAM_CONSISTENT_READ, null);
        if (consistentReadParam != null) {
            consistentRead = Boolean.parseBoolean(consistentReadParam);
        }
        // else: remains false (eventually consistent, DynamoDB default)
        logger.info("DynamoDB tracking module initialized with consistent_read_param={}, consistent_read={}", consistentReadParam, consistentRead);
    }

    @Override
    protected void persist(Message msg, Map<String, String> map) {
        try {
            // Check if record exists
            String msgId = map.get(FIELDS.MSG_ID);
            if (msgId == null || msgId.isEmpty()) {
                logger.error("Cannot persist record without MSG_ID: {}", map);
                return;
            }
            MessageMetadata existingMetadata = getMessageMetadata(msgId);
            MessageMetadata metadata = mapToMetadata(map, existingMetadata);

            // Put item to DynamoDB (will insert or update)
            table.putItem(metadata);

            if (logger.isDebugEnabled()) {
                logger.debug("{} tracking record in DynamoDB: {}", existingMetadata != null ? "Updated" : "Created", metadata);
            }
        } catch (Exception e) {
            msg.setLogMsg("Failed to persist tracking event to DynamoDB: " +
                    org.openas2.util.Logging.getExceptionMsg(e) + " ::: Data map: " + map);
            logger.error(msg.getLogMsg(), e);
        }
    }

    /**
     * Lists all messages from DynamoDB.
     * Note: Uses scan operation which can be expensive for large tables.
     * Consider implementing pagination for production use.
     *
     * @return list of message metadata records
     */
    public ArrayList<Map<String, String>> listMessages() {
        ArrayList<Map<String, String>> rows = new ArrayList<>();

        try {
            // Scan the table (consider using pagination for large datasets)
            ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                    .limit(1000) // Limit to 1000 items
                    .build();

            table.scan(scanRequest).items().forEach(metadata -> {
                Map<String, String> row = metadataToMap(metadata);
                rows.add(row);
            });

            if (logger.isDebugEnabled()) {
                logger.debug("Listed " + rows.size() + " messages from DynamoDB");
            }

        } catch (DynamoDbException e) {
            logger.error("Failed to list messages from DynamoDB", e);
        }

        return rows;
    }

    /**
     * Retrieves a specific message by ID from DynamoDB.
     * Uses strongly consistent reads if configured.
     *
     * @param msgId the message ID to retrieve
     * @return map containing message metadata, or empty map if not found
     */
    public Map<String, String> showMessage(String msgId) {
        Map<String, String> row = new HashMap<>();

        try {
            Key key = Key.builder()
                    .partitionValue(msgId)
                    .build();

            GetItemEnhancedRequest request = GetItemEnhancedRequest.builder()
                    .key(key)
                    .consistentRead(consistentRead)
                    .build();

            MessageMetadata metadata = table.getItem(request);

            if (metadata != null) {
                row = metadataToMap(metadata);
                if (logger.isTraceEnabled()) {
                    logger.trace("Retrieved message from DynamoDB: " + msgId + " (consistentRead=" + consistentRead + ")");
                }
            } else {
                logger.warn("Message not found in DynamoDB: " + msgId);
            }

        } catch (DynamoDbException e) {
            logger.error("Failed to retrieve message from DynamoDB: " + msgId, e);
        }

        return row;
    }

    /**
     * Retrieves messages within a date range for chart data.
     * Uses the StateIndex GSI for efficient querying.
     *
     * @param params map containing startDate and endDate
     * @return list of message metadata within the date range
     */
    public ArrayList<HashMap<String, String>> getDataCharts(HashMap<String, String> params) {
        ArrayList<HashMap<String, String>> rows = new ArrayList<>();

        try {
            String startDate = params.get("startDate");
            String endDate = params.get("endDate");

            if (startDate == null || endDate == null) {
                logger.error("startDate and endDate are required for getDataCharts");
                return rows;
            }

            // Convert dates to timestamps for comparison
            String startTimestamp = startDate + " 00:00:00.000";
            String endTimestamp = endDate + " 23:59:59.999";

            // Use scan with filter expression for date range
            // Note: For better performance, consider using GSI with state as partition key
            Map<String, AttributeValue> expressionValues = new HashMap<>();
            expressionValues.put(":startDate", AttributeValue.builder().s(startTimestamp).build());
            expressionValues.put(":endDate", AttributeValue.builder().s(endTimestamp).build());

            ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                    .filterExpression(software.amazon.awssdk.enhanced.dynamodb.Expression.builder()
                            .expression("createDt BETWEEN :startDate AND :endDate")
                            .expressionValues(expressionValues)
                            .build())
                    .limit(1000)
                    .build();

            table.scan(scanRequest).items().forEach(metadata -> {
                HashMap<String, String> row = new HashMap<>();
                row.put(FIELDS.MSG_ID, metadata.getMsgId());
                row.put(FIELDS.STATE, metadata.getState());
                row.put(FIELDS.STATUS, metadata.getStatus());
                row.put(FIELDS.CREATE_DT, metadata.getCreateDt());
                rows.add(row);
            });

            if (logger.isDebugEnabled()) {
                logger.debug("Retrieved {} chart data records from DynamoDB", rows.size());
            }

        } catch (Exception e) {
            logger.error("Failed to retrieve chart data from DynamoDB", e);
        }

        return rows;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void start() throws OpenAS2Exception {
        dbHandler = new DynamoDBHandler();
        dbHandler.start(null,
                getParameter(PARAM_AWS_ACCESS_KEY, null),
                getParameter(PARAM_AWS_SECRET_KEY, null),
                getParameters());

        // Initialize Enhanced Client
        DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dbHandler.getDynamoDbClient())
                .build();

        // Get table reference
        table = enhancedClient.table(tableName, TableSchema.fromBean(MessageMetadata.class));

        isRunning = true;
        logger.info("DynamoDB tracking module started with table: {}", tableName);
    }

    @Override
    public void stop() {
        if (dbHandler != null) {
            dbHandler.stop();
        }
        isRunning = false;
        logger.info("DynamoDB tracking module stopped");
    }

    @Override
    public boolean healthcheck(List<String> failures) {
        try {
            // Simple healthcheck: try to describe the table
            if (table == null || dbHandler == null) {
                failures.add(this.getClass().getSimpleName() + " - DynamoDB client not initialized");
                return false;
            }
            // Try a simple get operation to verify connectivity
            table.describeTable();
            return true;
        } catch (Exception e) {
            failures.add(this.getClass().getSimpleName() + " - Failed to check DynamoDB tracking module: " + e.getMessage());
            return false;
        }
    }

    /**
     * Retrieves a MessageMetadata object by message ID.
     * Uses strongly consistent reads if configured (important for Global Tables).
     *
     * @param msgId the message ID
     * @return MessageMetadata object or null if not found
     */
    private MessageMetadata getMessageMetadata(String msgId) {
        try {
            Key key = Key.builder()
                    .partitionValue(msgId)
                    .build();

            GetItemEnhancedRequest request = GetItemEnhancedRequest.builder()
                    .key(key)
                    .consistentRead(consistentRead)
                    .build();

            return table.getItem(request);
        } catch (Exception e) {
            logger.trace("Message not found (this is normal for new records): {}", msgId);
            return null;
        }
    }

    /**
     * Converts a map to MessageMetadata object.
     *
     * @param map              the source map
     * @param existingMetadata existing metadata for update operations
     * @return MessageMetadata object
     */
    private MessageMetadata mapToMetadata(Map<String, String> map, MessageMetadata existingMetadata) {
        MessageMetadata metadata = existingMetadata != null ? existingMetadata : new MessageMetadata();

        // Set partition key
        metadata.setMsgId(map.get(FIELDS.MSG_ID));

        // Set other fields only if they're present in the map
        metadata.setPriorMsgId(map.get(FIELDS.PRIOR_MSG_ID));
        metadata.setMdnId(map.get(FIELDS.MDN_ID));
        metadata.setDirection(map.get(FIELDS.DIRECTION));
        metadata.setIsResend(map.get(FIELDS.IS_RESEND));
        String resendCountStr = map.get(FIELDS.RESEND_COUNT);
        if (resendCountStr != null && !resendCountStr.isEmpty()) {
            try {
                metadata.setResendCount(Integer.parseInt(resendCountStr));
            } catch (NumberFormatException e) {
                logger.warn("Invalid resend_count value: {}", resendCountStr);
            }
        }
        metadata.setSenderId(map.get(FIELDS.SENDER_ID));
        metadata.setReceiverId(map.get(FIELDS.RECEIVER_ID));
        metadata.setStatus(map.get(FIELDS.STATUS));
        metadata.setState(map.get(FIELDS.STATE));
        metadata.setStateMsg(map.get(FIELDS.STATE_MSG));
        metadata.setSignatureAlgorithm(map.get(FIELDS.SIGNATURE_ALGORITHM));
        metadata.setEncryptionAlgorithm(map.get(FIELDS.ENCRYPTION_ALGORITHM));
        metadata.setCompression(map.get(FIELDS.COMPRESSION));
        metadata.setFileName(map.get(FIELDS.FILE_NAME));
        metadata.setSentFileName(map.get(FIELDS.SENT_FILE_NAME));
        metadata.setContentType(map.get(FIELDS.CONTENT_TYPE));
        metadata.setContentTransferEncoding(map.get(FIELDS.CONTENT_TRANSFER_ENCODING));
        metadata.setMdnMode(map.get(FIELDS.MDN_MODE));
        metadata.setMdnResponse(map.get(FIELDS.MDN_RESPONSE));

        // Handle timestamps
        String currentTimestamp = DateUtil.getSqlTimestamp();

        if (existingMetadata != null) {
            // Update timestamp
            metadata.setUpdateDt(currentTimestamp);
            // Preserve create timestamp
            if (existingMetadata.getCreateDt() != null) {
                metadata.setCreateDt(existingMetadata.getCreateDt());
            }
        } else {
            // New record - set create timestamp
            metadata.setCreateDt(currentTimestamp);
        }

        return metadata;
    }

    /**
     * Converts MessageMetadata object to a map.
     *
     * @param metadata the MessageMetadata object
     * @return map containing message metadata
     */
    private Map<String, String> metadataToMap(MessageMetadata metadata) {
        Map<String, String> map = new HashMap<>();
        map.put(FIELDS.MSG_ID, metadata.getMsgId());
        map.put(FIELDS.PRIOR_MSG_ID, metadata.getPriorMsgId());
        map.put(FIELDS.MDN_ID, metadata.getMdnId());
        map.put(FIELDS.DIRECTION, metadata.getDirection());
        map.put(FIELDS.IS_RESEND, metadata.getIsResend());
        map.put(FIELDS.RESEND_COUNT, metadata.getResendCountStr());
        map.put(FIELDS.SENDER_ID, metadata.getSenderId());
        map.put(FIELDS.RECEIVER_ID, metadata.getReceiverId());
        map.put(FIELDS.STATUS, metadata.getStatus());
        map.put(FIELDS.STATE, metadata.getState());
        map.put(FIELDS.STATE_MSG, metadata.getStateMsg());
        map.put(FIELDS.SIGNATURE_ALGORITHM, metadata.getSignatureAlgorithm());
        map.put(FIELDS.ENCRYPTION_ALGORITHM, metadata.getEncryptionAlgorithm());
        map.put(FIELDS.COMPRESSION, metadata.getCompression());
        map.put(FIELDS.FILE_NAME, metadata.getFileName());
        map.put(FIELDS.SENT_FILE_NAME, metadata.getSentFileName());
        map.put(FIELDS.CONTENT_TYPE, metadata.getContentType());
        map.put(FIELDS.CONTENT_TRANSFER_ENCODING, metadata.getContentTransferEncoding());
        map.put(FIELDS.MDN_MODE, metadata.getMdnMode());
        map.put(FIELDS.MDN_RESPONSE, metadata.getMdnResponse());
        map.put(FIELDS.CREATE_DT, metadata.getCreateDt());
        map.put(FIELDS.UPDATE_DT, metadata.getUpdateDt());
        return map;
    }
}

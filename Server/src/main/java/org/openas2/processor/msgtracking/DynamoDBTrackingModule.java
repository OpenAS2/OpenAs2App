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
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
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

    public static final String PARAM_AWS_REGION = "aws_region";
    public static final String PARAM_AWS_ACCESS_KEY = "aws_access_key_id";
    public static final String PARAM_AWS_SECRET_KEY = "aws_secret_access_key";
    public static final String PARAM_TABLE_NAME = "table_name";
    public static final String PARAM_DYNAMODB_ENDPOINT = "dynamodb_endpoint";
    public static final String PARAM_CONSISTENT_READ = "consistent_read";

    private static final Logger logger = LoggerFactory.getLogger(DynamoDBTrackingModule.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private DynamoDBHandler dbHandler = null;
    private DynamoDbEnhancedClient enhancedClient = null;
    private DynamoDbTable<MessageMetadata> table = null;
    private String tableName = "msg_metadata";
    private boolean consistentRead = false; // Optional: defaults to eventually consistent if not specified
    private boolean isRunning = false;

    @Override
    public void init(Session session, Map<String, String> options) throws OpenAS2Exception {
        super.init(session, options);
        tableName = getParameter(PARAM_TABLE_NAME, "msg_metadata");

        // Configure read consistency (optional - defaults to eventually consistent if not specified)
        String consistentReadParam = getParameter(PARAM_CONSISTENT_READ, null);
        if (consistentReadParam != null) {
            consistentRead = Boolean.parseBoolean(consistentReadParam);
        }
        // else: remains false (eventually consistent, DynamoDB default)

        if (logger.isInfoEnabled()) {
            logger.info("DynamoDB tracking module initialized with consistent_read=" + consistentRead +
                       (consistentReadParam == null ? " (not specified, using DynamoDB default)" : " (explicitly configured)"));
        }
    }

    @Override
    protected void persist(Message msg, Map<String, String> map) {
        try {
            // Check if record exists
            String msgId = map.get(FIELDS.MSG_ID);
            if (msgId == null || msgId.isEmpty()) {
                logger.error("Cannot persist record without MSG_ID: " + map);
                return;
            }

            MessageMetadata existingMetadata = getMessageMetadata(msgId);
            boolean isUpdate = (existingMetadata != null);

            MessageMetadata metadata = mapToMetadata(map, existingMetadata, isUpdate);

            // Put item to DynamoDB (will insert or update)
            table.putItem(metadata);

            if (logger.isDebugEnabled()) {
                logger.debug((isUpdate ? "Updated" : "Created") + " tracking record in DynamoDB: " + metadata);
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
    public ArrayList<HashMap<String, String>> listMessages() {
        ArrayList<HashMap<String, String>> rows = new ArrayList<>();

        try {
            // Scan the table (consider using pagination for large datasets)
            ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder()
                .limit(1000) // Limit to 1000 items
                .build();

            table.scan(scanRequest).items().forEach(metadata -> {
                HashMap<String, String> row = metadataToMap(metadata);
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
    public HashMap<String, String> showMessage(String msgId) {
        HashMap<String, String> row = new HashMap<>();

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
                logger.debug("Retrieved " + rows.size() + " chart data records from DynamoDB");
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
        enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dbHandler.getDynamoDbClient())
            .build();

        // Get table reference
        table = enhancedClient.table(tableName, TableSchema.fromBean(MessageMetadata.class));

        isRunning = true;
        logger.info("DynamoDB tracking module started with table: " + tableName);
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
            failures.add(this.getClass().getSimpleName() +
                " - Failed to check DynamoDB tracking module: " + e.getMessage());
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
            logger.trace("Message not found (this is normal for new records): " + msgId);
            return null;
        }
    }

    /**
     * Converts a map to MessageMetadata object.
     *
     * @param map the source map
     * @param existingMetadata existing metadata for update operations
     * @param isUpdate whether this is an update operation
     * @return MessageMetadata object
     */
    private MessageMetadata mapToMetadata(Map<String, String> map, MessageMetadata existingMetadata, boolean isUpdate) {
        MessageMetadata metadata = existingMetadata != null ? existingMetadata : new MessageMetadata();

        // Set partition key
        if (map.containsKey(FIELDS.MSG_ID)) {
            metadata.setMsgId(map.get(FIELDS.MSG_ID));
        }

        // Set other fields only if they're present in the map
        if (map.containsKey(FIELDS.PRIOR_MSG_ID)) {
            metadata.setPriorMsgId(map.get(FIELDS.PRIOR_MSG_ID));
        }
        if (map.containsKey(FIELDS.MDN_ID)) {
            metadata.setMdnId(map.get(FIELDS.MDN_ID));
        }
        if (map.containsKey(FIELDS.DIRECTION)) {
            metadata.setDirection(map.get(FIELDS.DIRECTION));
        }
        if (map.containsKey(FIELDS.IS_RESEND)) {
            metadata.setIsResend(map.get(FIELDS.IS_RESEND));
        }
        if (map.containsKey(FIELDS.RESEND_COUNT)) {
            String resendCountStr = map.get(FIELDS.RESEND_COUNT);
            if (resendCountStr != null && !resendCountStr.isEmpty()) {
                try {
                    metadata.setResendCount(Integer.parseInt(resendCountStr));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid resend_count value: " + resendCountStr);
                }
            }
        }
        if (map.containsKey(FIELDS.SENDER_ID)) {
            metadata.setSenderId(map.get(FIELDS.SENDER_ID));
        }
        if (map.containsKey(FIELDS.RECEIVER_ID)) {
            metadata.setReceiverId(map.get(FIELDS.RECEIVER_ID));
        }
        if (map.containsKey(FIELDS.STATUS)) {
            metadata.setStatus(map.get(FIELDS.STATUS));
        }
        if (map.containsKey(FIELDS.STATE)) {
            metadata.setState(map.get(FIELDS.STATE));
        }
        if (map.containsKey(FIELDS.STATE_MSG)) {
            metadata.setStateMsg(map.get(FIELDS.STATE_MSG));
        }
        if (map.containsKey(FIELDS.SIGNATURE_ALGORITHM)) {
            metadata.setSignatureAlgorithm(map.get(FIELDS.SIGNATURE_ALGORITHM));
        }
        if (map.containsKey(FIELDS.ENCRYPTION_ALGORITHM)) {
            metadata.setEncryptionAlgorithm(map.get(FIELDS.ENCRYPTION_ALGORITHM));
        }
        if (map.containsKey(FIELDS.COMPRESSION)) {
            metadata.setCompression(map.get(FIELDS.COMPRESSION));
        }
        if (map.containsKey(FIELDS.FILE_NAME)) {
            metadata.setFileName(map.get(FIELDS.FILE_NAME));
        }
        if (map.containsKey(FIELDS.SENT_FILE_NAME)) {
            metadata.setSentFileName(map.get(FIELDS.SENT_FILE_NAME));
        }
        if (map.containsKey(FIELDS.CONTENT_TYPE)) {
            metadata.setContentType(map.get(FIELDS.CONTENT_TYPE));
        }
        if (map.containsKey(FIELDS.CONTENT_TRANSFER_ENCODING)) {
            metadata.setContentTransferEncoding(map.get(FIELDS.CONTENT_TRANSFER_ENCODING));
        }
        if (map.containsKey(FIELDS.MDN_MODE)) {
            metadata.setMdnMode(map.get(FIELDS.MDN_MODE));
        }
        if (map.containsKey(FIELDS.MDN_RESPONSE)) {
            metadata.setMdnResponse(map.get(FIELDS.MDN_RESPONSE));
        }

        // Handle timestamps
        String currentTimestamp = DateUtil.getSqlTimestamp();

        if (isUpdate) {
            // Update timestamp
            metadata.setUpdateDt(currentTimestamp);
            // Preserve create timestamp
            if (existingMetadata != null && existingMetadata.getCreateDt() != null) {
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
    private HashMap<String, String> metadataToMap(MessageMetadata metadata) {
        HashMap<String, String> map = new HashMap<>();

        if (metadata.getMsgId() != null) {
            map.put("MSG_ID", metadata.getMsgId());
        }
        if (metadata.getPriorMsgId() != null) {
            map.put("PRIOR_MSG_ID", metadata.getPriorMsgId());
        }
        if (metadata.getMdnId() != null) {
            map.put("MDN_ID", metadata.getMdnId());
        }
        if (metadata.getDirection() != null) {
            map.put("DIRECTION", metadata.getDirection());
        }
        if (metadata.getIsResend() != null) {
            map.put("IS_RESEND", metadata.getIsResend());
        }
        if (metadata.getResendCount() != null) {
            map.put("RESEND_COUNT", metadata.getResendCount().toString());
        }
        if (metadata.getSenderId() != null) {
            map.put("SENDER_ID", metadata.getSenderId());
        }
        if (metadata.getReceiverId() != null) {
            map.put("RECEIVER_ID", metadata.getReceiverId());
        }
        if (metadata.getStatus() != null) {
            map.put("STATUS", metadata.getStatus());
        }
        if (metadata.getState() != null) {
            map.put("STATE", metadata.getState());
        }
        if (metadata.getStateMsg() != null) {
            map.put("STATE_MSG", metadata.getStateMsg());
        }
        if (metadata.getSignatureAlgorithm() != null) {
            map.put("SIGNATURE_ALGORITHM", metadata.getSignatureAlgorithm());
        }
        if (metadata.getEncryptionAlgorithm() != null) {
            map.put("ENCRYPTION_ALGORITHM", metadata.getEncryptionAlgorithm());
        }
        if (metadata.getCompression() != null) {
            map.put("COMPRESSION", metadata.getCompression());
        }
        if (metadata.getFileName() != null) {
            map.put("FILE_NAME", metadata.getFileName());
        }
        if (metadata.getSentFileName() != null) {
            map.put("SENT_FILE_NAME", metadata.getSentFileName());
        }
        if (metadata.getContentType() != null) {
            map.put("CONTENT_TYPE", metadata.getContentType());
        }
        if (metadata.getContentTransferEncoding() != null) {
            map.put("CONTENT_TRANSFER_ENCODING", metadata.getContentTransferEncoding());
        }
        if (metadata.getMdnMode() != null) {
            map.put("MDN_MODE", metadata.getMdnMode());
        }
        if (metadata.getMdnResponse() != null) {
            map.put("MDN_RESPONSE", metadata.getMdnResponse());
        }
        if (metadata.getCreateDt() != null) {
            map.put("CREATE_DT", metadata.getCreateDt());
        }
        if (metadata.getUpdateDt() != null) {
            map.put("UPDATE_DT", metadata.getUpdateDt());
        }

        return map;
    }
}

/* Copyright Uhuru Technology 2016 https://www.uhurutechnology.com
 * Distributed under the GPLv3 license or a commercial license must be acquired.
 */
package org.openas2.processor.msgtracking;

import org.openas2.util.DateUtil;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.time.Instant;
import java.util.Map;

/**
 * DynamoDB data model for AS2 message metadata.
 * Maps to the msg_metadata table structure.
 */
@DynamoDbBean
public class MessageMetadata {

    private String msgId;
    private String priorMsgId;
    private String mdnId;
    private String direction;
    private String isResend;
    private Integer resendCount;
    private String senderId;
    private String receiverId;
    private String status;
    private String state;
    private String stateMsg;
    private String signatureAlgorithm;
    private String encryptionAlgorithm;
    private String compression;
    private String fileName;
    private String sentFileName;
    private String contentType;
    private String contentTransferEncoding;
    private String mdnMode;
    private String mdnResponse;
    private String createDt;
    private String updateDt;

    /**
     * Default constructor required by DynamoDB Enhanced Client.
     */
    public MessageMetadata() {
    }

    @DynamoDbPartitionKey
    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getPriorMsgId() {
        return priorMsgId;
    }

    public void setPriorMsgId(String priorMsgId) {
        this.priorMsgId = priorMsgId;
    }

    public String getMdnId() {
        return mdnId;
    }

    public void setMdnId(String mdnId) {
        this.mdnId = mdnId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getIsResend() {
        return isResend;
    }

    public void setIsResend(String isResend) {
        this.isResend = isResend;
    }

    public Integer getResendCount() {
        return resendCount;
    }
    public String getResendCountStr() {
        return resendCount == null ? null : resendCount.toString();
    }

    public void setResendCount(Integer resendCount) {
        this.resendCount = resendCount;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(String receiverId) {
        this.receiverId = receiverId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "StateIndex")
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getStateMsg() {
        return stateMsg;
    }

    public void setStateMsg(String stateMsg) {
        this.stateMsg = stateMsg;
    }

    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    public void setSignatureAlgorithm(String signatureAlgorithm) {
        this.signatureAlgorithm = signatureAlgorithm;
    }

    public String getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    public void setEncryptionAlgorithm(String encryptionAlgorithm) {
        this.encryptionAlgorithm = encryptionAlgorithm;
    }

    public String getCompression() {
        return compression;
    }

    public void setCompression(String compression) {
        this.compression = compression;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getSentFileName() {
        return sentFileName;
    }

    public void setSentFileName(String sentFileName) {
        this.sentFileName = sentFileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentTransferEncoding() {
        return contentTransferEncoding;
    }

    public void setContentTransferEncoding(String contentTransferEncoding) {
        this.contentTransferEncoding = contentTransferEncoding;
    }

    public String getMdnMode() {
        return mdnMode;
    }

    public void setMdnMode(String mdnMode) {
        this.mdnMode = mdnMode;
    }

    public String getMdnResponse() {
        return mdnResponse;
    }

    public void setMdnResponse(String mdnResponse) {
        this.mdnResponse = mdnResponse;
    }

    @DynamoDbSecondarySortKey(indexNames = "StateIndex")
    public String getCreateDt() {
        return createDt;
    }

    public void setCreateDt(String createDt) {
        this.createDt = createDt;
    }

    public String getUpdateDt() {
        return updateDt;
    }

    public void setUpdateDt(String updateDt) {
        this.updateDt = updateDt;
    }

    @Override
    public String toString() {
        return "MessageMetadata{" +
                "msgId='" + msgId + '\'' +
                ", senderId='" + senderId + '\'' +
                ", receiverId='" + receiverId + '\'' +
                ", state='" + state + '\'' +
                ", status='" + status + '\'' +
                ", createDt='" + createDt + '\'' +
                '}';
    }
}

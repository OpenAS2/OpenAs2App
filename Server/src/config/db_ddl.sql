-- ----------------------------------------------------------------------- 
-- msg_metadata 
-- ----------------------------------------------------------------------- 

DROP TABLE msg_metadata IF EXISTS;

-- ----------------------------------------------------------------------- 
-- msg_metadata 
-- ----------------------------------------------------------------------- 

CREATE TABLE msg_metadata
(
    ID INTEGER NOT NULL IDENTITY,
    MSG_ID VARCHAR NOT NULL,
    PRIOR_MSG_ID VARCHAR,
    MDN_ID VARCHAR,
    DIRECTION VARCHAR(25),
    IS_RESEND VARCHAR(1),
    RESEND_COUNT INTEGER,
    SENDER_ID VARCHAR(255) NOT NULL,
    RECEIVER_ID VARCHAR(255) NOT NULL,
    STATUS VARCHAR(255),
    STATE VARCHAR(255),
    SIGNATURE_ALGORITHM VARCHAR(255),
    ENCRYPTION_ALGORITHM VARCHAR(255),
    COMPRESSION VARCHAR(255),
    FILE_NAME VARCHAR(255),
    CONTENT_TYPE VARCHAR(255),
    CONTENT_TRANSFER_ENCODING VARCHAR(255),
    MDN_MODE VARCHAR(255),
    MDN_RESPONSE VARCHAR,
    STATE_MSG VARCHAR,
    CREATE_DT TIMESTAMP,
    UPDATE_DT TIMESTAMP,
    PRIMARY KEY (ID)
);

CREATE UNIQUE INDEX MSG_ID_UNIQUE ON msg_metadata (MSG_ID);


-- -----------------------------------------------------------------------
-- msg_metadata
-- -----------------------------------------------------------------------
-- This DDL must stay aligned with the authoritative schema in
-- Server/src/resources/db/openas2-schema.xml. If you add/remove a column
-- here, mirror the change in the XML (and vice versa).

DROP TABLE msg_metadata IF EXISTS;

-- -----------------------------------------------------------------------
-- msg_metadata
-- -----------------------------------------------------------------------

CREATE TABLE msg_metadata
(
    ID INTEGER NOT NULL AUTO_INCREMENT,
    MSG_ID LONGVARCHAR NOT NULL,
    PRIOR_MSG_ID LONGVARCHAR,
    MDN_ID LONGVARCHAR,
    DIRECTION VARCHAR(25),
    IS_RESEND VARCHAR(1) DEFAULT 'N',
    RESEND_COUNT INTEGER DEFAULT 0,
    SENDER_ID VARCHAR(255) NOT NULL,
    RECEIVER_ID VARCHAR(255) NOT NULL,
    STATUS VARCHAR(255),
    STATE VARCHAR(255),
    SIGNATURE_ALGORITHM VARCHAR(255),
    ENCRYPTION_ALGORITHM VARCHAR(255),
    COMPRESSION VARCHAR(255),
    FILE_NAME VARCHAR(255),
    SENT_FILE_NAME VARCHAR(255),
    CONTENT_TYPE VARCHAR(255),
    CONTENT_TRANSFER_ENCODING VARCHAR(255),
    MDN_MODE VARCHAR(255),
    MDN_RESPONSE LONGVARCHAR,
    STATE_MSG LONGVARCHAR,
    CREATE_DT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATE_DT TIMESTAMP,
    PRIMARY KEY (ID)
);

CREATE UNIQUE INDEX MSG_ID_UNIQUE ON msg_metadata (MSG_ID);

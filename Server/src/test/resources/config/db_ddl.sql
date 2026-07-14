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

-- -----------------------------------------------------------------------
-- partner / partnership tables used by org.openas2.partner.DbPartnershipFactory
-- to load partnerships from a database instead of the partnerships XML file.
-- partnership_attribute.CATEGORY is 'attribute' for regular partnership
-- attributes, 'sender'/'receiver' for per-partnership overrides applied on
-- top of the linked partner's attributes or 'pollerConfig' for the attributes
-- of a partnership specific directory poller.
-- -----------------------------------------------------------------------

DROP TABLE partnership_attribute IF EXISTS;
DROP TABLE partnership IF EXISTS;
DROP TABLE partner_attribute IF EXISTS;
DROP TABLE partner IF EXISTS;

CREATE TABLE partner
(
    ID INTEGER NOT NULL AUTO_INCREMENT,
    NAME VARCHAR(255) NOT NULL,
    PRIMARY KEY (ID)
);

CREATE UNIQUE INDEX PARTNER_NAME_UNIQUE ON partner (NAME);

CREATE TABLE partner_attribute
(
    ID INTEGER NOT NULL AUTO_INCREMENT,
    PARTNER_ID INTEGER NOT NULL,
    ATTRIBUTE_NAME VARCHAR(255) NOT NULL,
    ATTRIBUTE_VALUE VARCHAR(4000),
    PRIMARY KEY (ID),
    FOREIGN KEY (PARTNER_ID) REFERENCES partner (ID)
);

CREATE UNIQUE INDEX PARTNER_ATTRIBUTE_UNIQUE ON partner_attribute (PARTNER_ID, ATTRIBUTE_NAME);

CREATE TABLE partnership
(
    ID INTEGER NOT NULL AUTO_INCREMENT,
    NAME VARCHAR(255) NOT NULL,
    SENDER_PARTNER_ID INTEGER NOT NULL,
    RECEIVER_PARTNER_ID INTEGER NOT NULL,
    PRIMARY KEY (ID),
    FOREIGN KEY (SENDER_PARTNER_ID) REFERENCES partner (ID),
    FOREIGN KEY (RECEIVER_PARTNER_ID) REFERENCES partner (ID)
);

CREATE UNIQUE INDEX PARTNERSHIP_NAME_UNIQUE ON partnership (NAME);

CREATE TABLE partnership_attribute
(
    ID INTEGER NOT NULL AUTO_INCREMENT,
    PARTNERSHIP_ID INTEGER NOT NULL,
    CATEGORY VARCHAR(20) DEFAULT 'attribute' NOT NULL,
    ATTRIBUTE_NAME VARCHAR(255) NOT NULL,
    ATTRIBUTE_VALUE VARCHAR(4000),
    PRIMARY KEY (ID),
    FOREIGN KEY (PARTNERSHIP_ID) REFERENCES partnership (ID)
);

CREATE UNIQUE INDEX PARTNERSHIP_ATTRIBUTE_UNIQUE ON partnership_attribute (PARTNERSHIP_ID, CATEGORY, ATTRIBUTE_NAME);

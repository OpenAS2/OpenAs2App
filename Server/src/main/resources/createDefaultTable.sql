CREATE SCHEMA `openas2` ;
USE `openas2`;

CREATE TABLE `as2_partner` (
  `name` VARCHAR(60) NOT NULL,
  `as2_id` VARCHAR(128) NOT NULL COMMENT 'The AS2 ID must be comprised of 1 to 128 printable ASCII characters and is case-sensitive',

  `x509_alias` VARCHAR(60) NOT NULL,
  `email` VARCHAR(255) NULL,
  PRIMARY KEY (`name`))
  DEFAULT CHARACTER SET = utf8 COLLATE = utf8_general_ci;

INSERT INTO `openas2`.`as2_partner` (`name`, `as2_id`, `x509_alias`, `email`) VALUES ('OpenAS2A_DB', 'OpenAS2A_DB', 'openas2a', 'as2@amtrust.fr');
INSERT INTO `openas2`.`as2_partner` (`name`, `as2_id`, `x509_alias`, `email`) VALUES ('OpenAS2B_DB', 'OpenAS2B_DB', 'openas2b', 'as2@amtrust.fr');


CREATE TABLE `as2_partnership` (
  `name` VARCHAR(255),
  `sender` VARCHAR(60) NOT NULL,
  `receiver` VARCHAR(60) NOT NULL,
  `protocol` ENUM('as1', 'as2') NOT NULL  DEFAULT 'as2',
  `content_transfer_encoding` VARCHAR(30) NOT NULL DEFAULT '8bit',
  `compression_type` ENUM('zlib') DEFAULT NULL,
  `compression_mode` ENUM('compress-before-signing', 'compress-after-signing') NOT NULL DEFAULT 'compress-before-signing',
  `subject` VARCHAR(255) NULL,
  `mdnsubject` VARCHAR(255) NULL,
  `as2_url` VARCHAR(255) NULL,
  `as2_mdn_to` VARCHAR(255) NULL,
  `as2_receipt_option` VARCHAR(255) NULL,
  `as2_mdn_options` VARCHAR(255) NULL,
  `encrypt` ENUM('3des', 'cast5', 'idea', 'rc2', 'rc2_cbc', 'aes128', 'aes192', 'aes256', 'aes256_wrap') NOT NULL  DEFAULT '3des',
  `sign` ENUM( 'md2', 'md5', 'sha1', 'sha224', 'sha256', 'sha384', 'sha512') NOT NULL  DEFAULT 'sha256',
  `resend_max_retries` SMALLINT UNSIGNED NULL,
  `prevent_canonicalization_for_mic` BOOLEAN NULL,
  `no_set_transfer_encoding_for_signing` BOOLEAN NULL,
  `no_set_transfer_encoding_for_encryption` BOOLEAN NULL,
  `rename_digest_to_old_name` BOOLEAN NULL,
  `remove_cms_algorithm_protection_attrib` BOOLEAN NULL,
  PRIMARY KEY (`name`),
  UNIQUE INDEX `unique_sender_receiver` (`sender` ASC, `receiver` ASC),
  FOREIGN KEY (`sender`) REFERENCES `as2_partner` (`name`) ON DELETE CASCADE ON UPDATE CASCADE,
  FOREIGN KEY (`receiver`) REFERENCES `as2_partner` (`name`) ON DELETE CASCADE ON UPDATE CASCADE)
  DEFAULT CHARACTER SET = utf8 COLLATE = utf8_general_ci;

INSERT INTO `openas2`.`as2_partnership` (`name`, `sender`, `receiver`, `protocol`, `content_transfer_encoding`, `compression_type`, `compression_mode`, `subject`, `mdnsubject`, `as2_url`, `as2_mdn_to`, `as2_receipt_option`, `as2_mdn_options`, `encrypt`, `sign`, `resend_max_retries`, `prevent_canonicalization_for_mic`, `no_set_transfer_encoding_for_signing`, `no_set_transfer_encoding_for_encryption`, `rename_digest_to_old_name`, `remove_cms_algorithm_protection_attrib`) 
VALUES ('OpenAS2A_DB-to-OpenAS2B_DB', 'OpenAS2A_DB', 'OpenAS2B_DB', 'as2', '8bit', 'zlib', 'compress-before-signing', 'From OpenAS2A_DB to OpenAS2B_DB', 'Your requested MDN response from $receiver.as2_id$', 'http://localhost:20090', 'edi@openas2b.org', 'http://localhost:10091', 'signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, SHA256', '3des', 'sha256', '3', '0', '0', '0', '0', '0');

CREATE TABLE `as2_message_A` (
`ID` int(11) NOT NULL AUTO_INCREMENT,
`MSG_ID` longtext NOT NULL,
`MDN_ID` longtext,
`DIRECTION` varchar(25) DEFAULT NULL,
`IS_RESEND` varchar(1) DEFAULT 'N',
`RESEND_COUNT` int(11) DEFAULT '0',
`SENDER_ID` varchar(255) NOT NULL,
`RECEIVER_ID` varchar(255) NOT NULL,
`STATUS` varchar(255) DEFAULT NULL,
`STATE` varchar(255) DEFAULT NULL,
`SIGNATURE_ALGORITHM` varchar(255) DEFAULT NULL,
`ENCRYPTION_ALGORITHM` varchar(255) DEFAULT NULL,
`COMPRESSION` varchar(255) DEFAULT NULL,
`FILE_NAME` varchar(255) DEFAULT NULL,
`CONTENT_TYPE` varchar(255) DEFAULT NULL,
`CONTENT_TRANSFER_ENCODING` varchar(255) DEFAULT NULL,
`MDN_MODE` varchar(255) DEFAULT NULL,
`MDN_RESPONSE` longtext,
`STATE_MSG` longtext,
`CREATE_DT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
`UPDATE_DT` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=latin1;


CREATE TABLE `as2_message_B` (
`ID` int(11) NOT NULL AUTO_INCREMENT,
`MSG_ID` longtext NOT NULL,
`MDN_ID` longtext,
`DIRECTION` varchar(25) DEFAULT NULL,
`IS_RESEND` varchar(1) DEFAULT 'N',
`RESEND_COUNT` int(11) DEFAULT '0',
`SENDER_ID` varchar(255) NOT NULL,
`RECEIVER_ID` varchar(255) NOT NULL,
`STATUS` varchar(255) DEFAULT NULL,
`STATE` varchar(255) DEFAULT NULL,
`SIGNATURE_ALGORITHM` varchar(255) DEFAULT NULL,
`ENCRYPTION_ALGORITHM` varchar(255) DEFAULT NULL,
`COMPRESSION` varchar(255) DEFAULT NULL,
`FILE_NAME` varchar(255) DEFAULT NULL,
`CONTENT_TYPE` varchar(255) DEFAULT NULL,
`CONTENT_TRANSFER_ENCODING` varchar(255) DEFAULT NULL,
`MDN_MODE` varchar(255) DEFAULT NULL,
`MDN_RESPONSE` longtext,
`STATE_MSG` longtext,
`CREATE_DT` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
`UPDATE_DT` timestamp NOT NULL DEFAULT '0000-00-00 00:00:00',
PRIMARY KEY (`ID`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=latin1;
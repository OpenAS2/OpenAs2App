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

INSERT INTO `openas2`.`as2_partnership` (`name`, `sender`, `receiver`, `protocol`, `content_transfer_encoding`, `compression_type`, `subject`, `mdnsubject`, `as2_url`, `as2_mdn_to`, `as2_receipt_option`, `as2_mdn_options`, `encrypt`, `sign`, `resend_max_retries`, `prevent_canonicalization_for_mic`, `no_set_transfer_encoding_for_signing`, `no_set_transfer_encoding_for_encryption`, `rename_digest_to_old_name`, `remove_cms_algorithm_protection_attrib`) 
VALUES ('OpenAS2A_DB-to-OpenAS2B_DB', 'OpenAS2A_DB', 'OpenAS2B_DB', 'as2', '8bit', 'zlib', 'From OpenAS2A_DB to OpenAS2B_DB', 'Your requested MDN response from $receiver.as2_id$', 'http://localhost:20090', 'edi@openas2b.org', 'http://localhost:10091', 'signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, SHA256', '3des', 'sha256', '3', '0', '0', '0', '0', '0');



CREATE TABLE `as2_message_status` (
  `status` varchar(20) NOT NULL COMMENT 'Status name',
  `type` varchar(15) NOT NULL COMMENT 'Status type: PROCESSING, PENDING, ERROR or DONE',
  `message_direction` enum('in','out') NOT NULL DEFAULT 'out' COMMENT 'Indicates whether the status is for incoming ("in") or outgoing ("out") messages',
  `label` varchar(256) NOT NULL COMMENT 'Status label',
  `label_fr` varchar(256) NOT NULL COMMENT 'Libellé du statut en français',
  PRIMARY KEY (`status`),
  KEY `fk_as2_message_status__status_type_idx` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='List of the different AS2 message status';


INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('FILE_SUBMITTED', 'PROCESSING', 'out', 'File submitted in an outgoing directory', 'Fichier soumis dans un répertoire d’envoi');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MDN_ARCHIVED', 'DONE', 'out', 'Sent message MDN archived and deleted', 'MDN du message AS2 envoyé archivé et supprimé');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MDN_ARCHIVING', 'PROCESSING', 'out', 'Archiving the MDN of the sent message', 'Archivage du MDN du message AS2 envoyé en cours');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MDN_ERROR', 'ERROR', 'out', 'Error MDN received: message sent but the MDN received is reporting errors', 'Réception d’un MDN d’erreur signalant que le message envoyé est incorrect');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MDN_NOTSENT', 'ERROR', 'in', 'Failed to send the MDN despite several attempts, abandon', 'Impossibilité d’envoyer le MDN malgré plusieurs tentatives, abandon.');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MDN_NOT_RECEIVED', 'ERROR', 'out', 'Message sent, but MDN not received', 'Message envoyé, mais MDN non reçu');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MDN_RECEIVED', 'DONE', 'out', 'Message sent and MDN received', 'Message envoyé et MDN reçu');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MDN_RETRY', 'PROCESSING', 'in', 'Failed to send the MDN, MDN put in resend queue', 'Impossibilité d’envoyer le MDN, mise en file d’attente');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MDN_SENDING', 'PROCESSING', 'in', 'Incoming message received, MDN sending in progress', 'Message AS2 entrant reçu, MDN en cours d’envoi');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MDN_SENT', 'DONE', 'in', 'Incoming message received and MDN sent', 'MDN envoyé');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MDN_WAITING', 'PENDING', 'out', 'Waiting for the MDN', 'Message en attente d’un MDN');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MSG_ARCHIVED', 'DONE', 'in', 'Incoming message archived and deleted', 'Message AS2 reçu archivé et supprimé');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MSG_ARCHIVING', 'PROCESSING', 'in', 'Archiving the incoming message', 'Archivage du message AS2 reçu en cours');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MSG_ERROR', 'ERROR', 'in', 'Invalid message received (sending an error MDN if requested by the message)', 'Message reçu incorrect (envoi d’un MDN d’erreur, si demandé dans le message)');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MSG_NOTSENT', 'ERROR', 'out', 'Failed to send the message', 'Impossibilité d’envoyer le message');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MSG_RECEIVED', 'DONE', 'in', 'Incoming message received without MDN request', 'Message AS2 reçu sans demande de MDN');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MSG_RETRY', 'PROCESSING', 'out', 'Failed to send the message, message put in resend queue', 'Impossibilité d’envoyer le message, mise en file d’attente');
INSERT INTO `openas2`.`as2_message_status` (`status`, `type`, `message_direction`, `label`, `label_fr`) VALUES ('MSG_SENT', 'DONE', 'out', 'Message sent without MDN request', 'Message envoyé sans demande de MDN');

CREATE TABLE `as2_message` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `partnership` varchar(255) NOT NULL,
  `message_id` varchar(255) NOT NULL,
  `filename` varchar(255) DEFAULT NULL,
  `date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `mdn_id` varchar(255) DEFAULT NULL,
  `mdn_filename` varchar(255) DEFAULT NULL,
  `mdn_date` timestamp NULL DEFAULT NULL,
  `status` varchar(20) DEFAULT NULL,
  `comment` varchar(2000) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_partnership_as2_message` (`partnership`),
  KEY `date_as2_message` (`date`),
  KEY `status_as2_message` (`status`),
  KEY `messageId_as2_message` (`message_id`),
  CONSTRAINT `fk_partnership_as2_message` FOREIGN KEY (`partnership`) REFERENCES `as2_partnership` (`name`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_status_as2_message` FOREIGN KEY (`status`) REFERENCES `as2_message_status` (`status`) ON UPDATE CASCADE
) DEFAULT CHARACTER SET = utf8 COLLATE = utf8_general_ci;

CREATE TABLE `as2_message2` (
`id` int(11) NOT NULL AUTO_INCREMENT,
`partnership` varchar(255) NOT NULL,
`message_id` varchar(255) NOT NULL,
`filename` varchar(255) DEFAULT NULL,
`date` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
`mdn_id` varchar(255) DEFAULT NULL,
`mdn_filename` varchar(255) DEFAULT NULL,
`mdn_date` timestamp NULL DEFAULT NULL,
`status` varchar(20) DEFAULT NULL,
`comment` varchar(2000) DEFAULT NULL,
PRIMARY KEY (`id`),
KEY `fk_partnership_as2_message2` (`partnership`),
KEY `date_as2_message2` (`date`),
KEY `status_as2_message2` (`status`),
KEY `messageId_as2_message2` (`message_id`),
CONSTRAINT `fk_partnership_as2_message2` FOREIGN KEY (`partnership`) REFERENCES `as2_partnership` (`name`) ON DELETE CASCADE ON UPDATE CASCADE,
CONSTRAINT `fk_status_as2_message2` FOREIGN KEY (`status`) REFERENCES `as2_message_status` (`status`) ON UPDATE CASCADE
) DEFAULT CHARACTER SET = utf8 COLLATE = utf8_general_ci;


CREATE USER 'openas2'@'localhost' IDENTIFIED BY '2oM2905Z#8';
GRANT ALL PRIVILEGES ON openas2.* TO 'openas2'@'localhost';
FLUSH PRIVILEGES;

DROP TABLE IF EXISTS `blame`;
CREATE TABLE `blame` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  `blame_reason` varchar(255) DEFAULT NULL,
  `round_id` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DROP TABLE IF EXISTS `round_log`;
CREATE TABLE `round_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  `raw_tx` mediumtext,
  `txid` varchar(255) DEFAULT NULL,
  `round_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKich4kq85yi96aqyhqrp3ct8xa` (`round_id`),
  CONSTRAINT `FKich4kq85yi96aqyhqrp3ct8xa` FOREIGN KEY (`round_id`) REFERENCES `round` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8;

DROP TABLE IF EXISTS `round`;
CREATE TABLE `round` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  `anonymity_set` int(11) NOT NULL,
  `fail_reason` varchar(255) DEFAULT NULL,
  `nb_liquidities` int(11) NOT NULL,
  `nb_must_mix` int(11) NOT NULL,
  `round_id` varchar(255) DEFAULT NULL,
  `round_status` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_tlhlq1hltmjx6q626o0mc5f3a` (`round_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8;

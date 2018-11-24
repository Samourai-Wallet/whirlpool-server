DROP TABLE IF EXISTS `blame`;
CREATE TABLE `blame` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `created` datetime DEFAULT NULL,
  `updated` datetime DEFAULT NULL,
  `blame_reason` varchar(255) DEFAULT NULL,
  `round_id` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

DROP TABLE IF EXISTS `mix_txid`;
CREATE TABLE `mix_txid` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `txid` char(64) NOT NULL,
  `denomination` int(11) NOT NULL,
  `created` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `mix_txid_uniq` (`txid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

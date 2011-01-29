CREATE TABLE `rooms` (
  `address` varchar(255) NOT NULL,
  `master_name` varchar(255) NOT NULL,
  `title` varchar(255) NOT NULL,
  `current_players` tinyint(4) NOT NULL,
  `max_players` tinyint(4) NOT NULL,
  `has_password` tinyint(1) NOT NULL,
  `description` text,
  PRIMARY KEY (`address`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8
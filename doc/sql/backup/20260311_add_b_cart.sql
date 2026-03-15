-- 新增购物车表：支持每个用户多条购物车记录、勾选若干条合并支付
DROP TABLE IF EXISTS `b_cart`;
CREATE TABLE `b_cart` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `thing_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `count` int(11) NOT NULL DEFAULT '1',
  `create_time` varchar(30) DEFAULT NULL,
  `update_time` varchar(30) DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_cart_user_id` (`user_id`) USING BTREE,
  KEY `idx_cart_thing_id` (`thing_id`) USING BTREE,
  CONSTRAINT `b_cart_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `b_user` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `b_cart_ibfk_2` FOREIGN KEY (`thing_id`) REFERENCES `b_thing` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC;

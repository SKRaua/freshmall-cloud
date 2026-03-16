-- 库存事件消费幂等日志表（Thing 服务）

CREATE TABLE IF NOT EXISTS `b_stock_event_consume_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `event_id` varchar(64) NOT NULL,
  `order_id` bigint(20) NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'PROCESSING',
  `create_time` bigint(20) NOT NULL,
  `update_time` bigint(20) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_stock_event_id` (`event_id`),
  KEY `idx_stock_event_order` (`order_id`),
  KEY `idx_stock_event_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

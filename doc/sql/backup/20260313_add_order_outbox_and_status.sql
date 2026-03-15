-- 订单状态扩展 + Outbox + 幂等处理日志

ALTER TABLE `b_order`
  MODIFY COLUMN `status` varchar(2) DEFAULT NULL COMMENT '0=已取消,1=待发货,2=待收货,3=已完成,4=待支付';

CREATE TABLE IF NOT EXISTS `b_order_event_outbox` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `event_id` varchar(64) NOT NULL,
  `order_id` bigint(20) NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `payload` text NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'NEW',
  `retry_count` int(11) NOT NULL DEFAULT '0',
  `next_retry_time` bigint(20) NOT NULL,
  `create_time` bigint(20) NOT NULL,
  `update_time` bigint(20) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_order_event_id` (`event_id`),
  KEY `idx_outbox_dispatch` (`status`,`next_retry_time`),
  KEY `idx_outbox_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE IF NOT EXISTS `b_order_event_process_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `event_id` varchar(64) NOT NULL,
  `order_id` bigint(20) NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `create_time` bigint(20) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_order_event_process` (`order_id`,`event_type`),
  KEY `idx_process_event_id` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

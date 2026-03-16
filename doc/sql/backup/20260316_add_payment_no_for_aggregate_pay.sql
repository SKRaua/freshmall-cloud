-- 聚合支付能力：为订单表增加 payment_no 字段与索引
-- 执行顺序：先执行 DDL，再发布订单服务代码

ALTER TABLE `b_order`
  ADD COLUMN `payment_no` varchar(64) DEFAULT NULL COMMENT '聚合支付单号' AFTER `order_number`;

CREATE INDEX `idx_order_payment_no_status` ON `b_order` (`payment_no`, `status`);
CREATE INDEX `idx_order_user_payment_no` ON `b_order` (`user_id`, `payment_no`);

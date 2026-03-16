-- 订单号扩列：避免高并发下订单号长度和碰撞风险
-- 执行顺序：先执行 DDL，再发布订单服务代码

ALTER TABLE `b_order`
  MODIFY COLUMN `order_number` varchar(32) DEFAULT NULL COMMENT '订单编号';

CREATE INDEX `idx_order_order_number` ON `b_order` (`order_number`);

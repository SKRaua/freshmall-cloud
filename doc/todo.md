## bug：

* [X] 商品详情页相关推荐无法点击
* [X] 管理端单独修改订单状态不生效，且与已取消按钮重复
* [X] 客户端取消订单提示成功，但是无法进入已取消状态
* [X] 购物车批量下单失败，后端报错
  改thing服务的扣库存为原子 SQL，不改接口协议和前端参数
* [ ] 购物车结算只能显示一个商品，无法兼容后端paymentNo统一支付
* [ ]

## 功能：

* [X] 引入redis缓存
* [X] 引入复杂订单处理：消息队列
* [X] 统一下单接口（direct/cart）+ 聚合支付号 paymentNo
* [ ] 首页应该将按热度和按照最新排行加回来在产品分类的右边加入，并预留按照时间价格筛选的功能
* [ ] 引入对与订单的评论功能
* [ ] 完善服务器日志相关和管理端监控
* [ ] 点击订单应当进入订单详情，且上方显示商品标签可点击进入商品详情
* [ ] 优化推荐算法：协同或者ES？
* [ ] 引入更优的认证机制

## 优化：

* [X] 合适位置省略商品名称过长部分

## 2026-03-16 数据库与接口变更记录：

* 数据库变更脚本：`doc/sql/backup/20260316_add_payment_no_for_aggregate_pay.sql`
* 数据库变更脚本：`doc/sql/backup/20260316_expand_order_number_length.sql`
* 变更内容：
  * b_order.order_number 扩容为 varchar(32)
  * 新增索引 idx_order_order_number
  * b_order 新增 payment_no 字段（聚合支付单号）
  * 新增索引 idx_order_payment_no_status
  * 新增索引 idx_order_user_payment_no
* 字段语义：
  * order_number：单笔订单号（一条订单一号）
  * payment_no：聚合支付号（一次支付可对应多条订单，尤其是购物车合并支付）
* 后端接口新增：
  * POST /order/submit 统一下单（mode=direct/cart）
  * POST /order/payByPaymentNo 按 paymentNo 聚合支付
* 兼容性：
  * 原 /order/create 与 /order/cart/checkout 保留
  * /order/cart/checkout 已内部复用统一下单逻辑

## 2026-03-16 统一异常处理流程（可维护性改造）：

* 通用异常：新增 `freshmall-common` 的 `BizException`
* 服务端适配：
  * `order`：新增全局异常处理器，统一 `BizException/IllegalArgumentException/未知异常` 返回
  * `thing`：新增全局异常处理器，统一处理业务异常与上传大小异常
* 业务代码适配：
  * `order` 模块中参数校验与业务失败场景统一改为抛出 `BizException`
* 前端适配：
  * `client/admin` axios 统一兼容 `msg/message/trace` 字段
  * 请求失败统一返回标准化错误对象，页面可直接使用 `err.msg` 提示

## 2026-03-16 Outbox + RabbitMQ 最小高可用链路落地：

* 订单服务：
  * 保留统一下单接口（direct/cart），本地事务内写订单 + Outbox 事件
  * Relay 调度器改为发布 MQ：`b_order_event_outbox(NEW/RETRY) -> RabbitMQ`
  * 发布成功后 `status=DONE`；发布失败按指数退避重试；超过上限后 `status=DEAD`
* 商品服务：
  * 新增库存事件消费者，消费 `ORDER_CREATED/ORDER_CANCELED`
  * 新增 `b_stock_event_consume_log` 做 eventId 幂等控制
  * 消费失败自动重试，最终投递到 DLQ，支持人工/任务回放
* 新增数据库脚本：
  * `doc/sql/backup/20260316_add_stock_event_consume_log.sql`

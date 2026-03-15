package com.freshmall.order.constant;

/**
 * 订单状态定义。
 */
public final class OrderStatusConstants {

    private OrderStatusConstants() {
    }

    public static final String CANCELED = "0";
    public static final String TO_SHIP = "1";
    public static final String TO_RECEIVE = "2";
    public static final String FINISHED = "3";
    public static final String PENDING_PAY = "4";
}

package org.linlinjava.litemall.core.notify;

public enum NotifyType {
    PAY_SUCCEED("paySucceed"),
    SHIP("ship"),
    REFUND("refund"),
    CAPTCHA("captcha"),
    
    // 快递员通知类型
    COURIER_ACCEPT("courierAccept"),        // 快递员接单通知（通知买家）
    COURIER_DELIVER("courierDeliver"),      // 配送完成通知（通知买家）
    COURIER_TIMEOUT("courierTimeout"),      // 配送超时通知（通知快递员）
    COURIER_WITHDRAW("courierWithdraw");    // 提现到账通知（通知快递员）

    private String type;

    NotifyType(String type) {
        this.type = type;
    }

    public String getType() {
        return this.type;
    }
}

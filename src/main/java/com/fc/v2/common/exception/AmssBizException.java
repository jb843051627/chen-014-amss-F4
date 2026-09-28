package com.fc.v2.common.exception;

/**
 * 对账业务规矩被突破时抛出（整批退回、核销越额、凭据缺失等）。
 * 消息一句话只说一件事，由全局异常解析器原样回执，不许拿"数不对"这类话充数。
 *
 * @author fuce
 * @date 2026-09-28
 */
public class AmssBizException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public AmssBizException(String message) {
        super(message);
    }
}

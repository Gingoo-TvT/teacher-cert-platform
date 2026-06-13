package cn.edu.gpnu.platform.common.exception;

import cn.edu.gpnu.platform.common.api.ResultCode;
import lombok.Getter;

/**
 * 业务异常。由 GlobalExceptionHandler 统一转为 {@link cn.edu.gpnu.platform.common.api.Result}。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String msg) {
        super(msg);
        this.code = ResultCode.BIZ_ERROR.getCode();
    }

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public BizException(ResultCode rc) {
        super(rc.getMsg());
        this.code = rc.getCode();
    }
}

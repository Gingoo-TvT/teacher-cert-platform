package cn.edu.gpnu.platform.boot.handler;

import cn.edu.gpnu.platform.common.api.Result;
import cn.edu.gpnu.platform.common.api.ResultCode;
import cn.edu.gpnu.platform.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

/**
 * 全局异常处理：统一转为 {@link Result}，不向前端泄漏堆栈。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 参数校验异常：返回首个字段级错误 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? "参数校验失败" : fe.getField() + ": " + fe.getDefaultMessage();
        return Result.fail(ResultCode.BAD_REQUEST.getCode(), msg);
    }

    /** 方法级权限异常：@PreAuthorize 等在 MVC 内抛出时也应返回 403 */
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException.class)
    public Result<Void> handleAccessDenied(AccessDeniedException e) {
        log.warn("权限异常: {}", e.getMessage());
        return Result.fail(ResultCode.FORBIDDEN);
    }

    /**
     * 数据完整性异常兜底（如唯一键冲突 DuplicateKeyException）：转友好业务提示而非裸 500。
     * 本库多数唯一键不含 deleted，软删后同码重建等场景可能绕过服务层预检直接撞库（见字典编码 Phase43.4）；
     * 各服务层已知场景应自行 catch 转更精确的提示，这里仅作最后一道防线。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("数据完整性异常: {}", e.getMessage());
        return Result.fail(ResultCode.BIZ_ERROR.getCode(), "数据已存在或不满足唯一性约束，请刷新后重试");
    }

    /**
     * 请求方法不被支持（对已存在的路径用了未映射的 HTTP 方法，如对仅 GET 的 /api/test 发 POST/PUT）：
     * 405，属客户端错误、非服务端故障（不可归入下方 500 兜底，否则误报为系统异常/告警噪声）。
     */
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("请求方法不被支持: {}", e.getMessage());
        return Result.fail(HttpStatus.METHOD_NOT_ALLOWED.value(), "请求方法不被支持");
    }

    /** 无匹配处理器（路径不存在）：404，属客户端错误。 */
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNoHandler(NoHandlerFoundException e) {
        log.warn("无匹配处理器: {}", e.getMessage());
        return Result.fail(ResultCode.NOT_FOUND);
    }

    /**
     * 兜底：未预期的系统异常。返回 HTTP 500（P1-10）——此前无 @ResponseStatus 默认 200，导致所有未知故障
     * 对 APM/网关/告警「隐形」（HTTP 全绿）。改 500 让监控可见；响应体仍保留统一 {@link Result}（code/msg）结构，
     * 前端拦截器已同步兼容非 2xx 的 Result 体（见 request.ts 错误分支提取 data.msg），友好提示不丢失。
     * 已知可恢复类（BizException/参数校验/数据完整性冲突）仍返回 200+业务码，语义是「客户端可修正」而非服务端故障。
     */
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception.class)
    public Result<Void> handle(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.ERROR);
    }
}

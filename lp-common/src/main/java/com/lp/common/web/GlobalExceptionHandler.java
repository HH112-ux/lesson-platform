package com.lp.common.web;

import com.lp.common.exception.BizException;
import com.lp.common.exception.ErrorCode;
import com.lp.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates exceptions into the unified response envelope.
 *
 * <p>{@link BizException} keeps its own code, a failed bean validation yields
 * {@code 40001}, and any uncaught exception yields {@code 50001} without leaking internals.
 * The HTTP status stays {@code 200} because the envelope code carries the semantic result.
 * Every body includes a trace id.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Converts a business exception into a failed envelope, keeping its code.
     *
     * @param ex      business exception raised by application code
     * @param request current request, used for logging
     * @return failed envelope carrying the exception code and trace id
     * @author Caspian
     * @since 0.1.0
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException ex, HttpServletRequest request) {
        log.warn("biz error code={} path={} message={}", ex.getCode(),
                request == null ? "-" : request.getRequestURI(), ex.getMessage());
        return Result.fail(ex.getCode(), ex.getMessage(), TraceIds.resolve(request));
    }

    /**
     * Converts a bean validation failure into {@code 40001} with the first field error.
     *
     * @param ex      validation failure raised by request binding
     * @param request current request, used for the trace id
     * @return failed envelope with code {@code 40001}
     * @author Caspian
     * @since 0.1.0
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException ex,
                                         HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(GlobalExceptionHandler::formatFieldError)
                .orElse(ErrorCode.PARAM_INVALID.getDefaultMessage());
        return Result.fail(ErrorCode.PARAM_INVALID.getCode(), detail, TraceIds.resolve(request));
    }

    /**
     * Converts any uncaught exception into {@code 50001} and logs it with the stack trace.
     *
     * @param ex      uncaught exception
     * @param request current request, used for logging and the trace id
     * @return failed envelope with code {@code 50001}
     * @author Caspian
     * @since 0.1.0
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("unexpected error path={}",
                request == null ? "-" : request.getRequestURI(), ex);
        return Result.fail(ErrorCode.INTERNAL_ERROR.getCode(),
                ErrorCode.INTERNAL_ERROR.getDefaultMessage(), TraceIds.resolve(request));
    }

    /**
     * Formats a single field error as {@code field: message}.
     *
     * @param fieldError violating field
     * @return formatted detail string
     * @author Caspian
     * @since 0.1.0
     */
    private static String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}

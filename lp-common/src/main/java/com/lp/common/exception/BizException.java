package com.lp.common.exception;

/**
 * Exception raised by business code to signal a failed operation with a segmented error code.
 *
 * <p>Handlers translate it into a response envelope through
 * {@link com.lp.common.web.GlobalExceptionHandler}; callers must not catch it just to re-wrap
 * the same code.</p>
 *
 * @author Caspian
 * @since 0.1.0
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    /**
     * Creates an exception using the default message of the given error code.
     *
     * @param errorCode canonical error code
     * @author Caspian
     * @since 0.1.0
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.code = errorCode.getCode();
    }

    /**
     * Creates an exception with a custom message while keeping the canonical code.
     *
     * @param errorCode canonical error code
     * @param message   custom message, used when extra context is needed
     * @author Caspian
     * @since 0.1.0
     */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    /**
     * Creates an exception with an explicit numeric code.
     *
     * <p>Prefer {@link #BizException(ErrorCode)} so that codes stay centralized; use this
     * constructor only for codes not yet promoted to {@link ErrorCode}.</p>
     *
     * @param code    numeric error code
     * @param message custom message
     * @author Caspian
     * @since 0.1.0
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Returns the numeric error code carried by this exception.
     *
     * @return segmented numeric error code
     * @author Caspian
     * @since 0.1.0
     */
    public int getCode() {
        return code;
    }
}

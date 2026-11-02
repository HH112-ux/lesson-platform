package com.lp.common.result;

import java.io.Serializable;

/**
 * Unified response envelope returned by every HTTP endpoint.
 *
 * <p>Success is always {@code code = 0}; failures carry a segmented error code as defined in
 * {@code AGENTS.md} section 11.4: {@code 4xxxx} client/protocol, {@code 5xxxx} server or
 * dependency, {@code 6xxxx} business domain. The {@code traceId} is always populated so that a
 * client report can be correlated with server logs.</p>
 *
 * @param <T> payload type
 * @author Caspian
 * @since 0.1.0
 */
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Success code shared by all endpoints. */
    public static final int SUCCESS_CODE = 0;

    private int code;

    private String message;

    private T data;

    private String traceId;

    /**
     * Creates an empty envelope; required by serialization frameworks.
     *
     * @author Caspian
     * @since 0.1.0
     */
    public Result() {
    }

    /**
     * Creates a fully populated envelope.
     *
     * @param code    business or protocol code, {@code 0} means success
     * @param message human readable message
     * @param data    payload, may be {@code null}
     * @param traceId request trace identifier, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public Result(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    /**
     * Builds a successful envelope with an explicit trace id.
     *
     * @param data    payload, may be {@code null}
     * @param traceId request trace identifier
     * @param <T>     payload type
     * @return successful envelope
     * @author Caspian
     * @since 0.1.0
     */
    public static <T> Result<T> ok(T data, String traceId) {
        return new Result<>(SUCCESS_CODE, "ok", data, traceId);
    }

    /**
     * Builds a successful envelope without a trace id.
     *
     * @param data payload, may be {@code null}
     * @param <T>  payload type
     * @return successful envelope
     * @author Caspian
     * @since 0.1.0
     */
    public static <T> Result<T> ok(T data) {
        return ok(data, null);
    }

    /**
     * Builds a successful envelope with an empty payload.
     *
     * @param <T> payload type
     * @return successful envelope
     * @author Caspian
     * @since 0.1.0
     */
    public static <T> Result<T> ok() {
        return ok(null);
    }

    /**
     * Builds a failed envelope with an explicit trace id.
     *
     * @param code    error code
     * @param message human readable message
     * @param traceId request trace identifier
     * @param <T>     payload type
     * @return failed envelope
     * @author Caspian
     * @since 0.1.0
     */
    public static <T> Result<T> fail(int code, String message, String traceId) {
        return new Result<>(code, message, null, traceId);
    }

    /**
     * Builds a failed envelope without a trace id.
     *
     * @param code    error code
     * @param message human readable message
     * @param <T>     payload type
     * @return failed envelope
     * @author Caspian
     * @since 0.1.0
     */
    public static <T> Result<T> fail(int code, String message) {
        return fail(code, message, null);
    }

    /**
     * Tells whether this envelope represents a successful call.
     *
     * @return {@code true} when {@code code == 0}
     * @author Caspian
     * @since 0.1.0
     */
    public boolean isSuccess() {
        return code == SUCCESS_CODE;
    }

    /**
     * Returns the business or protocol code.
     *
     * @return error code, {@code 0} for success
     * @author Caspian
     * @since 0.1.0
     */
    public int getCode() {
        return code;
    }

    /**
     * Sets the business or protocol code.
     *
     * @param code error code, {@code 0} for success
     * @author Caspian
     * @since 0.1.0
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * Returns the human readable message.
     *
     * @return message text
     * @author Caspian
     * @since 0.1.0
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the human readable message.
     *
     * @param message message text
     * @author Caspian
     * @since 0.1.0
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Returns the payload.
     *
     * @return payload, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public T getData() {
        return data;
    }

    /**
     * Sets the payload.
     *
     * @param data payload, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public void setData(T data) {
        this.data = data;
    }

    /**
     * Returns the request trace identifier.
     *
     * @return trace id, may be {@code null}
     * @author Caspian
     * @since 0.1.0
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * Sets the request trace identifier.
     *
     * @param traceId trace id
     * @author Caspian
     * @since 0.1.0
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}

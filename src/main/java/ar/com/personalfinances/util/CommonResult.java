package ar.com.personalfinances.util;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class CommonResult {

    private boolean error;
    private boolean warning;
    private String message;
    private Object payload;

    public static CommonResult ok() {
        return new CommonResult();
    }

    public static CommonResult ok(Object payload) {
        return CommonResult.ok(payload, null);
    }

    public static CommonResult ok(Object payload, String message) {
        CommonResult result = new CommonResult();
        result.setPayload(payload);
        result.setMessage(message);
        return result;
    }

    public static CommonResult error(String message) {
        return error(message, null);
    }

    public static CommonResult error(String message, Object payload) {
        CommonResult result = new CommonResult();
        result.setError(true);
        result.setMessage(message);
        result.setPayload(payload);
        return result;
    }

    public static CommonResult warn(String message) {
        return warn(message, null);
    }

    public static CommonResult warn(String message, Object payload) {
        CommonResult result = new CommonResult();
        result.setWarning(true);
        result.setMessage(message);
        result.setPayload(payload);
        return result;
    }

    @Override
    public String toString() {
        return "CommonResult [" +
                "error=" + error + ", " +
                "warning=" + warning + ", " +
                ((message != null) ? "message='" + message + "', " : "") +
                ((payload != null) ? "payload=" + payload + ", " : "") +
                "]";
    }
}
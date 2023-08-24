package ar.com.personalfinances.util;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApplicationMessage {

    private String message;
    private ApplicationMessageType type;

    public ApplicationMessage(String message, ApplicationMessageType type) {
        this.message = message;
        this.type = type;
    }

    public enum ApplicationMessageType {
        PRIMARY, SECONDARY, SUCCESS, DANGER, WARNING, INFO, LIGHT, DARK
    }

    public static ApplicationMessage info(String message) {
        return new ApplicationMessage(message, ApplicationMessageType.INFO);
    }

    public static ApplicationMessage warn(String message) {
        return new ApplicationMessage(message, ApplicationMessageType.WARNING);
    }

    public static ApplicationMessage error(String message) {
        return new ApplicationMessage(message, ApplicationMessageType.DANGER);
    }
}
package ar.com.personalfinances.api.galicia.util;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Credentials {

    private String documentNumber;
    private String username;
    private String password;

    public Credentials(String documentNumber, String username, String password) {
        this.documentNumber = documentNumber;
        this.username = username;
        this.password = password;
    }

    @Override
    public String toString() {
        return "Credentials [" +
                ((documentNumber != null) ? "documentNumber='" + documentNumber + "', " : "") +
                ((username != null) ? "username='" + username + "', " : "") +
                ((password != null) ? "password='" + password + "', " : "") +
                "]";
    }
}
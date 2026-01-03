package ar.com.personalfinances.service;

import ar.com.personalfinances.util.ApplicationMessage;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@Service
public class ApplicationMessageService {

    private static final String SESSION_KEY = "APPLICATION_MESSAGE";

    public void add(HttpServletRequest request, ApplicationMessage message) {
        if (message != null) {
            request.getSession().setAttribute(SESSION_KEY, message);
        }
    }

    public ApplicationMessage consume(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;

        ApplicationMessage message = (ApplicationMessage) session.getAttribute(SESSION_KEY);
        if (message != null) {
            session.removeAttribute(SESSION_KEY);
        }
        return message;
    }
}
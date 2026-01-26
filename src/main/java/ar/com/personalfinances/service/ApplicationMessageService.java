package ar.com.personalfinances.service;

import ar.com.personalfinances.util.ApplicationMessage;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;

@Service
public class ApplicationMessageService {

    private static final String SESSION_KEY = "APPLICATION_MESSAGE";

    public void add(HttpServletRequest request, ApplicationMessage message) {
        if (message != null) {
            HttpSession session = request.getSession();
            List<ApplicationMessage> messages = (List<ApplicationMessage>) session.getAttribute(SESSION_KEY);
            if (messages == null) messages = new ArrayList<>();
            messages.add(message);
            session.setAttribute(SESSION_KEY, messages);
        }
    }

    public List<ApplicationMessage> consume(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;

        List<ApplicationMessage> message = (List<ApplicationMessage>) session.getAttribute(SESSION_KEY);
        if (message != null) {
            session.removeAttribute(SESSION_KEY);
        }
        return message;
    }
}
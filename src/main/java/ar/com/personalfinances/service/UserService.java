package ar.com.personalfinances.service;

import ar.com.personalfinances.entity.User;

import java.util.List;

public interface UserService {
    User saveUser(User user);
    List<Object> isUserPresent(User user);
}
package com.freshmall.user.service;

import com.freshmall.common.entity.User;
import java.util.List;

public interface UserService {
    List<User> getUserList(String keyword);

    User getAdminUser(User user);

    User getNormalUser(User user);

    void createUser(User user);

    void deleteUser(String id);

    void updateUser(User user);

    User getUserByToken(String token);

    User getUserDetail(String userId);
}

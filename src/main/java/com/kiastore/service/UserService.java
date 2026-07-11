package com.kiastore.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.kiastore.dao.UserDao;
import com.kiastore.model.User;

import java.util.List;
import java.util.Optional;

public class UserService {

    private final UserDao userDao;

    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public List<User> all() {
        return userDao.findAll();
    }

    public Optional<User> findById(int id) {
        return userDao.findById(id);
    }

    public User save(User u) {
        if (u.getId() > 0) {
            userDao.update(u);
            return u;
        } else {
            return userDao.insert(u);
        }
    }

    public User create(String username, String password, String name, com.kiastore.model.Role role) {
        String hash = BCrypt.withDefaults().hashToString(10, password.toCharArray());
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(hash);
        u.setName(name);
        u.setRole(role);
        u.setActive(true);
        return userDao.insert(u);
    }

    public boolean delete(int id) {
        return userDao.delete(id);
    }
}

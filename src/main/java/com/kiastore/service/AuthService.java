package com.kiastore.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.kiastore.dao.UserDao;
import com.kiastore.model.User;
import com.kiastore.util.Result;

import java.util.Optional;

public class AuthService {

    private final UserDao userDao;

    public AuthService(UserDao userDao) {
        this.userDao = userDao;
    }

    /**
     * Authenticates a user.
     */
    public Result<User> login(String username, String password) {
        if (username == null || username.isBlank()) {
            return Result.fail("اسم المستخدم مطلوب"); // Arabic error: Username is required
        }
        if (password == null || password.isBlank()) {
            return Result.fail("كلمة المرور مطلوبة"); // Arabic error: Password is required
        }

        Optional<User> opt = userDao.findByUsername(username.trim());
        if (opt.isEmpty()) {
            return Result.fail("اسم المستخدم أو كلمة المرور غير صحيحة"); // Invalid username or password
        }

        User u = opt.get();
        if (!u.isActive()) {
            return Result.fail("هذا الحساب معطل حالياً"); // Account is disabled
        }

        BCrypt.Result verified = BCrypt.verifyer().verify(password.toCharArray(), u.getPasswordHash());
        if (verified.verified) {
            return Result.ok(u);
        } else {
            return Result.fail("اسم المستخدم أو كلمة المرور غير صحيحة");
        }
    }
}

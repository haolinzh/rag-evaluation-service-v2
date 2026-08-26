package com.rag.eval.service;

import com.rag.eval.model.AppUser;
import com.rag.eval.model.AuthenticatedUser;
import com.rag.eval.model.LoginResponse;
import com.rag.eval.model.Permission;
import com.rag.eval.model.Role;
import com.rag.eval.repository.RoleRepo;
import com.rag.eval.repository.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_WINDOW = Duration.ofMinutes(15);
    private static final String FAIL_PREFIX = "auth:fail:";

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;
    private final RedisTemplate<String, String> redis;

    public AuthService(UserRepo userRepo, RoleRepo roleRepo, PasswordEncoder passwordEncoder, TokenStore tokenStore,
                       @Qualifier("redisTemplate") RedisTemplate<String, String> redis) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
        this.redis = redis;
    }

    public LoginResponse login(String username, String password, boolean adminOnly) {
        String uname = username == null ? "" : username.trim();
        String failKey = FAIL_PREFIX + uname.toLowerCase();
        if (isLocked(failKey)) {
            log.warn("Login blocked (too many failed attempts): username={}", uname);
            throw new IllegalArgumentException("失败次数过多，请 15 分钟后再试");
        }
        AppUser user = userRepo.findByUsername(uname).orElse(null);
        if (user == null || !user.isEnabled()
                || password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            recordFailure(failKey);
            log.warn("Login failed: username={}", uname);
            throw new IllegalArgumentException("用户名或密码错误");
        }
        redis.delete(failKey);
        AuthenticatedUser au = toAuthenticatedUser(user);
        if (adminOnly && !au.permissions().contains("user:manage")) {
            log.warn("Admin login rejected (insufficient privileges): username={}", uname);
            throw new IllegalArgumentException("该账号无管理权限");
        }
        String token = tokenStore.create(user.getId());
        return new LoginResponse(token, au);
    }

    private boolean isLocked(String failKey) {
        String v = redis.opsForValue().get(failKey);
        if (v == null) return false;
        try {
            return Integer.parseInt(v) >= MAX_FAILED_ATTEMPTS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void recordFailure(String failKey) {
        redis.opsForValue().increment(failKey);
        redis.expire(failKey, LOCK_WINDOW);
    }

    public void logout(String token) {
        tokenStore.revoke(token);
    }

    public AuthenticatedUser currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser u) {
            return u;
        }
        return null;
    }

    public AuthenticatedUser currentUserOrGuest() {
        AuthenticatedUser u = currentUser();
        return u != null ? u : guestPrincipal();
    }

    public AuthenticatedUser guestPrincipal() {
        Set<String> perms = roleRepo.findByCode("GUEST")
            .map(role -> role.getPermissions().stream()
                .map(Permission::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new)))
            .orElseGet(LinkedHashSet::new);
        return new AuthenticatedUser(null, "guest", "游客", null, perms);
    }

    public static AuthenticatedUser toAuthenticatedUser(AppUser user) {
        Set<String> perms = new LinkedHashSet<>();
        for (Role role : user.getRoles()) {
            for (Permission p : role.getPermissions()) {
                perms.add(p.getCode());
            }
        }
        return new AuthenticatedUser(user.getId(), user.getUsername(),
            user.getDisplayName(), user.getDepartment(), perms);
    }
}

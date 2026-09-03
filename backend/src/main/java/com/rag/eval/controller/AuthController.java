package com.rag.eval.controller;

import com.rag.eval.model.AppUser;
import com.rag.eval.model.AuthenticatedUser;
import com.rag.eval.model.LoginRequest;
import com.rag.eval.model.LoginResponse;
import com.rag.eval.model.Permission;
import com.rag.eval.model.RegisterRequest;
import com.rag.eval.model.Role;
import com.rag.eval.model.RoleDto;
import com.rag.eval.model.RoleRequest;
import com.rag.eval.model.UserDto;
import com.rag.eval.model.UserRequest;
import com.rag.eval.repository.PermissionRepo;
import com.rag.eval.repository.RoleRepo;
import com.rag.eval.repository.UserRepo;
import com.rag.eval.service.AuthService;
import com.rag.eval.service.NotificationService;
import com.rag.eval.service.TokenStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenStore tokenStore;
    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final PermissionRepo permissionRepo;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    public AuthController(AuthService authService, TokenStore tokenStore, UserRepo userRepo,
                          RoleRepo roleRepo, PermissionRepo permissionRepo, PasswordEncoder passwordEncoder,
                          NotificationService notificationService) {
        this.authService = authService;
        this.tokenStore = tokenStore;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.permissionRepo = permissionRepo;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse resp = authService.login(request.username(), request.password(),
                Boolean.TRUE.equals(request.adminOnly()));
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (req.username() == null || req.username().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名不能为空"));
        }
        if (req.password() == null || req.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码不能为空"));
        }
        if (req.password().length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码至少 6 位"));
        }
        String uname = req.username().trim();
        if (userRepo.findByUsername(uname).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名已存在"));
        }
        try {
            AppUser user = new AppUser();
            user.setUsername(uname);
            user.setPasswordHash(passwordEncoder.encode(req.password()));
            user.setDisplayName(req.displayName());
            user.setDepartment(req.department());
            user.setEnabled(true);
            // 自助注册固定给「普通员工」角色，绝不接受 roleCodes/enabled，防止提权。
            roleRepo.findByCode("USER").ifPresent(role -> user.setRoles(new LinkedHashSet<>(Set.of(role))));
            userRepo.save(user);
            String token = tokenStore.create(user.getId());
            return ResponseEntity.ok(new LoginResponse(token, AuthService.toAuthenticatedUser(user)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenStore.revoke(authHeader.substring(7));
        }
        return ResponseEntity.ok(Map.of("message", "已退出"));
    }

    @GetMapping("/me")
    public AuthenticatedUser me() {
        return authService.currentUser();
    }

    @GetMapping("/guest-permissions")
    public List<String> guestPermissions() {
        return new ArrayList<>(authService.guestPrincipal().permissions());
    }

    @PreAuthorize("hasAnyAuthority('user:manage','role:manage')")
    @GetMapping("/permissions")
    public List<Permission> permissions() {
        return permissionRepo.findAll().stream()
            .sorted(Comparator.comparing(Permission::getGroup).thenComparing(Permission::getCode))
            .toList();
    }

    @PreAuthorize("hasAuthority('user:manage')")
    @GetMapping("/users")
    public List<UserDto> listUsers() {
        return userRepo.findAll().stream()
            .sorted(Comparator.comparing(AppUser::getId))
            .map(this::toUserDto)
            .toList();
    }

    @PreAuthorize("hasAuthority('user:manage')")
    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody UserRequest req) {
        if (req.username() == null || req.username().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名不能为空"));
        }
        if (req.password() == null || req.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "密码不能为空"));
        }
        if (userRepo.findByUsername(req.username().trim()).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名已存在"));
        }
        try {
            AppUser user = new AppUser();
            user.setUsername(req.username().trim());
            user.setPasswordHash(passwordEncoder.encode(req.password()));
            user.setDisplayName(req.displayName());
            user.setDepartment(req.department());
            user.setEnabled(req.enabled() == null || req.enabled());
            user.setRoles(resolveRoles(req.roleCodes()));
            userRepo.save(user);
            notificationService.notify("user_manage", "用户创建", "管理员创建用户 " + user.getUsername(), authService.currentUser(), user.getUsername());
            return ResponseEntity.ok(toUserDto(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('user:manage')")
    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody UserRequest req) {
        AppUser user = userRepo.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "用户不存在"));
        }
        try {
            if (req.username() != null && !req.username().isBlank()
                    && !req.username().trim().equals(user.getUsername())) {
                if (userRepo.findByUsername(req.username().trim()).isPresent()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "用户名已存在"));
                }
                user.setUsername(req.username().trim());
            }
            if (req.password() != null && !req.password().isBlank()) {
                user.setPasswordHash(passwordEncoder.encode(req.password()));
            }
            if (req.displayName() != null) user.setDisplayName(req.displayName());
            if (req.department() != null) user.setDepartment(req.department());
            if (req.enabled() != null) user.setEnabled(req.enabled());
            if (req.roleCodes() != null) user.setRoles(resolveRoles(req.roleCodes()));
            userRepo.save(user);
            notificationService.notify("user_manage", "用户更新", "管理员更新用户 " + user.getUsername(), authService.currentUser(), user.getUsername());
            return ResponseEntity.ok(toUserDto(user));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('user:manage')")
    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        AuthenticatedUser me = authService.currentUser();
        if (me != null && me.id().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "不能删除当前登录用户"));
        }
        String targetUsername = userRepo.findById(id).map(AppUser::getUsername).orElse(null);
        if (!userRepo.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "用户不存在"));
        }
        userRepo.deleteById(id);
        notificationService.notify("user_manage", "用户删除", "管理员删除用户 " + targetUsername, me, targetUsername);
        return ResponseEntity.ok(Map.of("message", "已删除"));
    }

    @PreAuthorize("hasAnyAuthority('user:manage','role:manage')")
    @GetMapping("/roles")
    public List<RoleDto> listRoles() {
        return roleRepo.findAll().stream()
            .sorted(Comparator.comparing(Role::getId))
            .map(this::toRoleDto)
            .toList();
    }

    @PreAuthorize("hasAuthority('role:manage')")
    @PostMapping("/roles")
    public ResponseEntity<?> createRole(@RequestBody RoleRequest req) {
        if (req.code() == null || req.code().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "角色 code 不能为空"));
        }
        if (req.name() == null || req.name().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "角色名称不能为空"));
        }
        if (roleRepo.existsByCode(req.code().trim())) {
            return ResponseEntity.badRequest().body(Map.of("error", "角色 code 已存在"));
        }
        try {
            Role role = new Role();
            role.setCode(req.code().trim());
            role.setName(req.name().trim());
            role.setDescription(req.description());
            role.setBuiltin(false);
            role.setPermissions(resolvePermissions(req.permissionCodes()));
            roleRepo.save(role);
            notificationService.notify("role_manage", "角色创建", "管理员创建角色 " + role.getName(), authService.currentUser(), null);
            return ResponseEntity.ok(toRoleDto(role));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('role:manage')")
    @PutMapping("/roles/{id}")
    public ResponseEntity<?> updateRole(@PathVariable Long id, @RequestBody RoleRequest req) {
        Role role = roleRepo.findById(id).orElse(null);
        if (role == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "角色不存在"));
        }
        try {
            if (req.code() != null && !req.code().isBlank() && !req.code().trim().equals(role.getCode())) {
                if (role.isBuiltin()) {
                    return ResponseEntity.badRequest().body(Map.of("error", "内置角色不可修改 code"));
                }
                if (roleRepo.existsByCode(req.code().trim())) {
                    return ResponseEntity.badRequest().body(Map.of("error", "角色 code 已存在"));
                }
                role.setCode(req.code().trim());
            }
            if (req.name() != null && !req.name().isBlank()) role.setName(req.name().trim());
            if (req.description() != null) role.setDescription(req.description());
            if (req.permissionCodes() != null) role.setPermissions(resolvePermissions(req.permissionCodes()));
            roleRepo.save(role);
            notificationService.notify("role_manage", "角色更新", "管理员更新角色 " + role.getName(), authService.currentUser(), null);
            return ResponseEntity.ok(toRoleDto(role));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('role:manage')")
    @DeleteMapping("/roles/{id}")
    public ResponseEntity<?> deleteRole(@PathVariable Long id) {
        Role role = roleRepo.findById(id).orElse(null);
        if (role == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "角色不存在"));
        }
        if (role.isBuiltin()) {
            return ResponseEntity.badRequest().body(Map.of("error", "内置角色不可删除"));
        }
        roleRepo.delete(role);
        notificationService.notify("role_manage", "角色删除", "管理员删除角色 " + role.getName(), authService.currentUser(), null);
        return ResponseEntity.ok(Map.of("message", "已删除"));
    }

    private UserDto toUserDto(AppUser user) {
        return new UserDto(user.getId(), user.getUsername(), user.getDisplayName(),
            user.getDepartment(), user.isEnabled(), roleCodesOf(user.getRoles()));
    }

    private RoleDto toRoleDto(Role role) {
        return new RoleDto(role.getId(), role.getCode(), role.getName(), role.getDescription(),
            role.isBuiltin(), role.getPermissions().stream().map(Permission::getCode)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    private Set<String> roleCodesOf(Set<Role> roles) {
        return roles.stream().map(Role::getCode).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Role> resolveRoles(Set<String> codes) {
        if (codes == null) return new LinkedHashSet<>();
        return codes.stream()
            .map(code -> roleRepo.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在: " + code)))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Permission> resolvePermissions(Set<String> codes) {
        if (codes == null) return new LinkedHashSet<>();
        return codes.stream()
            .map(code -> permissionRepo.findById(code)
                .orElseThrow(() -> new IllegalArgumentException("权限不存在: " + code)))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}

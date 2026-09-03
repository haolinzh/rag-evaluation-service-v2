package com.rag.eval.config;

import com.rag.eval.model.AppUser;
import com.rag.eval.model.DocumentMeta;
import com.rag.eval.model.Permission;
import com.rag.eval.model.Role;
import com.rag.eval.repository.DocumentMetaRepo;
import com.rag.eval.repository.PermissionRepo;
import com.rag.eval.repository.RoleRepo;
import com.rag.eval.repository.UserRepo;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seedRbac(PermissionRepo permissionRepo, RoleRepo roleRepo,
                               UserRepo userRepo, DocumentMetaRepo docRepo,
                               PasswordEncoder passwordEncoder, JdbcTemplate jdbc) {
        return args -> {
            ensureUniqueFileName(jdbc);
            ensureQueueIndex(jdbc);
            if (permissionRepo.count() == 0) {
                permissionRepo.saveAll(permissionCatalog());
            }
            if (roleRepo.count() == 0) {
                seedRoles(roleRepo, permissionRepo);
            }
            ensureGuestRole(roleRepo, permissionRepo);
            ensureWebSearchPermission(roleRepo, permissionRepo);
            ensureMessagePermission(roleRepo, permissionRepo);
            if (userRepo.count() == 0) {
                seedAdmin(userRepo, roleRepo, passwordEncoder);
            }
            backfillDocuments(docRepo, userRepo);
        };
    }

    private void ensureUniqueFileName(JdbcTemplate jdbc) {
        try {
            // 兜底唯一约束：ddl-auto=update 不会给已存在的列补约束，这里幂等建索引。
            jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_document_meta_file_name ON document_meta (file_name)");
        } catch (Exception e) {
            System.err.println("Failed to create unique index on document_meta.file_name: " + e.getMessage());
        }
    }

    private void ensureQueueIndex(JdbcTemplate jdbc) {
        try {
            // poller 按 (status, next_retry_at) 捞待处理任务，缺索引会退化成顺序扫描。
            jdbc.execute("CREATE INDEX IF NOT EXISTS idx_document_meta_queue ON document_meta (status, next_retry_at)");
        } catch (Exception e) {
            System.err.println("Failed to create queue index on document_meta: " + e.getMessage());
        }
    }

    private void backfillDocuments(DocumentMetaRepo docRepo, UserRepo userRepo) {
        List<DocumentMeta> docs = docRepo.findAll();
        if (docs.isEmpty()) return;
        AppUser admin = userRepo.findByUsername("admin").orElse(null);
        boolean changed = false;
        for (DocumentMeta d : docs) {
            // 存量文档等价旧行为：所有人可见，归属管理员。
            if (d.getVisibility() == null) {
                d.setVisibility("PUBLIC");
                changed = true;
            }
            if (d.getOwnerId() == null && admin != null) {
                d.setOwnerId(admin.getId());
                d.setOwnerName(admin.getDisplayName());
                d.setOwnerDepartment(admin.getDepartment());
                changed = true;
            }
        }
        if (changed) {
            docRepo.saveAll(docs);
        }
    }

    private List<Permission> permissionCatalog() {
        return List.of(
            p("document:read:public", "读「所有人可见」文档", "文档"),
            p("document:read:department", "读「本部门可见」文档", "文档"),
            p("document:read:executive", "读「高管可见」文档", "文档"),
            p("document:read:private", "读「作者可见」文档", "文档"),
            p("document:read:any", "全局穿透（跨部门/作者）", "文档"),
            p("document:manage:own", "管理自己的文档", "文档"),
            p("document:manage:all", "管理任意文档", "文档"),
            p("chat:read:own", "读自己的会话", "聊天"),
            p("chat:delete:own", "删自己的会话", "聊天"),
            p("chat:web", "使用联网搜索", "聊天"),
            p("evaluation:use", "使用评测功能", "评测"),
            p("user:manage", "用户管理", "系统"),
            p("role:manage", "角色管理", "系统"),
            p("config:view", "查看配置", "系统"),
            p("config:edit", "修改配置", "系统"),
            p("ops:view", "运维页", "系统"),
            p("log:view", "查看请求日志", "系统"),
            p("log:clear", "清空请求日志", "系统"),
            p("cache:clear", "清空语义缓存", "系统"),
            p("report:view", "查看指标汇总/CSV", "系统"),
            p("message:view", "查看消息中心", "系统")
        );
    }

    private Permission p(String code, String name, String group) {
        return new Permission(code, name, group);
    }

    private void seedRoles(RoleRepo roleRepo, PermissionRepo permissionRepo) {
        roleRepo.save(role("ADMIN", "管理员", "拥有全部权限", true, permissionRepo,
            "document:read:public", "document:read:department", "document:read:executive",
            "document:read:private", "document:read:any", "document:manage:own", "document:manage:all",
            "chat:read:own", "chat:delete:own", "chat:web", "evaluation:use", "user:manage", "role:manage",
            "config:view", "config:edit", "ops:view", "log:view", "log:clear", "cache:clear", "report:view", "message:view"));

        roleRepo.save(role("EXECUTIVE", "高管", "跨部门全局可见，含作者私有", true, permissionRepo,
            "document:read:public", "document:read:department", "document:read:executive",
            "document:read:private", "document:read:any", "document:manage:own",
            "chat:read:own", "chat:delete:own", "chat:web", "evaluation:use", "report:view", "message:view"));

        roleRepo.save(role("USER", "普通员工", "默认员工权限", true, permissionRepo,
            "document:read:public", "document:read:department", "document:read:private",
            "document:manage:own", "chat:read:own", "chat:delete:own", "evaluation:use", "message:view"));
    }

    private void ensureGuestRole(RoleRepo roleRepo, PermissionRepo permissionRepo) {
        String[] codes = {"document:read:public", "config:view", "ops:view", "report:view"};
        Role guest = roleRepo.findByCode("GUEST").orElse(null);
        if (guest == null) {
            roleRepo.save(role("GUEST", "游客", "匿名只读访问", true, permissionRepo, codes));
            return;
        }
        // 幂等对齐 GUEST 为规范权限集合（去掉历史遗留的 log:view / evaluation:use 等）
        Set<String> current = new LinkedHashSet<>();
        for (Permission p : guest.getPermissions()) current.add(p.getCode());
        Set<String> canonical = new LinkedHashSet<>(List.of(codes));
        if (!current.equals(canonical)) {
            Set<Permission> perms = new LinkedHashSet<>();
            for (String c : codes) permissionRepo.findById(c).ifPresent(perms::add);
            guest.setPermissions(perms);
            roleRepo.save(guest);
        }
    }

    private void ensureWebSearchPermission(RoleRepo roleRepo, PermissionRepo permissionRepo) {
        Permission web = permissionRepo.findById("chat:web").orElseGet(() ->
            permissionRepo.save(new Permission("chat:web", "使用联网搜索", "聊天")));
        for (String code : List.of("ADMIN", "EXECUTIVE")) {
            Role role = roleRepo.findByCode(code).orElse(null);
            if (role == null) continue;
            boolean has = role.getPermissions().stream().anyMatch(p -> "chat:web".equals(p.getCode()));
            if (has) continue;
            Set<Permission> perms = new LinkedHashSet<>(role.getPermissions());
            perms.add(web);
            role.setPermissions(perms);
            roleRepo.save(role);
        }
    }

    private void ensureMessagePermission(RoleRepo roleRepo, PermissionRepo permissionRepo) {
        Permission msg = permissionRepo.findById("message:view").orElseGet(() ->
            permissionRepo.save(new Permission("message:view", "查看消息中心", "系统")));
        for (String code : List.of("ADMIN", "EXECUTIVE", "USER")) {
            Role role = roleRepo.findByCode(code).orElse(null);
            if (role == null) continue;
            boolean has = role.getPermissions().stream().anyMatch(p -> "message:view".equals(p.getCode()));
            if (has) continue;
            Set<Permission> perms = new LinkedHashSet<>(role.getPermissions());
            perms.add(msg);
            role.setPermissions(perms);
            roleRepo.save(role);
        }
    }

    private Role role(String code, String name, String description, boolean builtin,
                      PermissionRepo permissionRepo, String... codes) {
        Role role = new Role();
        role.setCode(code);
        role.setName(name);
        role.setDescription(description);
        role.setBuiltin(builtin);
        Set<Permission> perms = new LinkedHashSet<>();
        for (String c : codes) {
            permissionRepo.findById(c).ifPresent(perms::add);
        }
        role.setPermissions(perms);
        return role;
    }

    private void seedAdmin(UserRepo userRepo, RoleRepo roleRepo, PasswordEncoder passwordEncoder) {
        AppUser admin = new AppUser();
        admin.setUsername("admin");
        admin.setPasswordHash(passwordEncoder.encode("admin"));
        admin.setDisplayName("管理员");
        admin.setDepartment("系统管理");
        admin.setEnabled(true);
        roleRepo.findByCode("ADMIN").ifPresent(role -> admin.setRoles(new LinkedHashSet<>(Set.of(role))));
        userRepo.save(admin);
    }
}

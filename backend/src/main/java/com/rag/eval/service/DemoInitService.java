package com.rag.eval.service;

import com.rag.eval.model.AppUser;
import com.rag.eval.model.AuthenticatedUser;
import com.rag.eval.model.JudgeConfig;
import com.rag.eval.model.Permission;
import com.rag.eval.model.Role;
import com.rag.eval.repository.PermissionRepo;
import com.rag.eval.repository.RoleRepo;
import com.rag.eval.repository.UserRepo;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Service
public class DemoInitService {

    private final CorpusService corpusService;
    private final PermissionRepo permissionRepo;
    private final RoleRepo roleRepo;
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final EvaluationService evaluationService;

    public DemoInitService(CorpusService corpusService,
                           PermissionRepo permissionRepo,
                           RoleRepo roleRepo,
                           UserRepo userRepo,
                           PasswordEncoder passwordEncoder,
                           EvaluationService evaluationService) {
        this.corpusService = corpusService;
        this.permissionRepo = permissionRepo;
        this.roleRepo = roleRepo;
        this.userRepo = userRepo;
        this.passwordEncoder = passwordEncoder;
        this.evaluationService = evaluationService;
    }

    public void init(Consumer<Map<String, Object>> onEvent, AuthenticatedUser viewer) {
        emit(onEvent, Map.of("type", "phase", "phase", "documents", "message", "正在入库演示文档并分块…"));
        corpusService.ensureIngested(onEvent);

        emit(onEvent, Map.of("type", "phase", "phase", "rbac", "message", "正在创建演示权限、角色与用户…"));
        int perms = seedDemoPermissions();
        boolean role = seedDemoRole();
        boolean user = seedDemoUser();
        emit(onEvent, Map.of("type", "rbac",
            "permissionsCreated", perms, "roleCreated", role, "userCreated", user));

        emit(onEvent, Map.of("type", "phase", "phase", "evaluation", "message", "正在触发一次测评…"));
        evaluationService.runEvaluation(null, true, new JudgeConfig(null, null), null, onEvent, viewer);
    }

    private int seedDemoPermissions() {
        List<Permission> demo = List.of(
            new Permission("document:download", "下载文档", "文档"),
            new Permission("chat:export", "导出对话", "聊天"),
            new Permission("report:export", "导出评测报告", "评测")
        );
        int created = 0;
        for (Permission p : demo) {
            if (!permissionRepo.existsById(p.getCode())) {
                permissionRepo.save(p);
                created++;
            }
        }
        return created;
    }

    private boolean seedDemoRole() {
        if (roleRepo.findByCode("DEMO").isPresent()) return false;
        Role role = new Role();
        role.setCode("DEMO");
        role.setName("演示角色");
        role.setDescription("Demo 演示用角色：只读知识库 + 评测 + 联网");
        role.setBuiltin(false);
        Set<Permission> perms = new LinkedHashSet<>();
        for (String code : List.of(
            "document:read:public", "document:read:department", "document:read:executive",
            "chat:read:own", "chat:delete:own", "chat:web",
            "evaluation:use", "report:view", "document:download", "chat:export", "report:export")) {
            permissionRepo.findById(code).ifPresent(perms::add);
        }
        role.setPermissions(perms);
        roleRepo.save(role);
        return true;
    }

    private boolean seedDemoUser() {
        if (userRepo.findByUsername("demo").isPresent()) return false;
        AppUser user = new AppUser();
        user.setUsername("demo");
        user.setPasswordHash(passwordEncoder.encode("demo123"));
        user.setDisplayName("演示用户");
        user.setDepartment("演示");
        user.setEnabled(true);
        roleRepo.findByCode("DEMO").ifPresent(role -> user.setRoles(new LinkedHashSet<>(Set.of(role))));
        userRepo.save(user);
        return true;
    }

    private void emit(Consumer<Map<String, Object>> onEvent, Map<String, Object> event) {
        try {
            onEvent.accept(event);
        } catch (Exception ignored) {
            // client disconnected mid-stream
        }
    }
}

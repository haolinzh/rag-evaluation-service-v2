package com.rag.eval.integration;

import com.rag.eval.model.Permission;
import com.rag.eval.model.Role;
import com.rag.eval.repository.PermissionRepo;
import com.rag.eval.repository.RoleRepo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacDataInitializerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RoleRepo roleRepo;

    @Autowired
    private PermissionRepo permissionRepo;

    @Autowired
    @Qualifier("seedRbac")
    private CommandLineRunner seedRbac;

    @Test
    void adminExecutiveUserHaveMessageView() {
        for (String code : new String[]{"ADMIN", "EXECUTIVE", "USER"}) {
            Role role = roleRepo.findByCode(code).orElseThrow();
            Set<String> perms = role.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet());
            assertTrue(perms.contains("message:view"), code + " 应有 message:view");
        }
    }

    @Test
    void guestLacksMessageView() {
        Role guest = roleRepo.findByCode("GUEST").orElseThrow();
        Set<String> perms = guest.getPermissions().stream().map(Permission::getCode).collect(Collectors.toSet());
        assertFalse(perms.contains("message:view"));
    }

    @Test
    void messagePermissionExistsInCatalog() {
        assertTrue(permissionRepo.findById("message:view").isPresent());
    }

    @Test
    void ensureMessagePermissionIsIdempotent() throws Exception {
        Role before = roleRepo.findByCode("ADMIN").orElseThrow();
        int permsBefore = before.getPermissions().size();

        // 模拟「已有库」再次启动：重新执行 seedRbac
        seedRbac.run();

        Role after = roleRepo.findByCode("ADMIN").orElseThrow();
        assertEquals(permsBefore, after.getPermissions().size(), "重复启动不应产生重复授权");
        assertTrue(after.getPermissions().stream().anyMatch(p -> "message:view".equals(p.getCode())));
    }
}

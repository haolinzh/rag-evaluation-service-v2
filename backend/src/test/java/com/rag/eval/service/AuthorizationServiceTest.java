package com.rag.eval.service;

import com.rag.eval.model.AuthenticatedUser;
import com.rag.eval.model.DocumentMeta;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationServiceTest {

    private final AuthorizationService service = new AuthorizationService();

    private static AuthenticatedUser user(Long id, String department, String... perms) {
        return new AuthenticatedUser(id, "u" + id, "User " + id, department, Set.of(perms));
    }

    private static DocumentMeta doc(Long ownerId, String visibility, String ownerDepartment) {
        DocumentMeta d = new DocumentMeta();
        d.setOwnerId(ownerId);
        d.setVisibility(visibility);
        d.setOwnerDepartment(ownerDepartment);
        return d;
    }

    @Test
    void hasPermission_nullOrMissing_false() {
        assertFalse(service.hasPermission(null, "document:read:any"));
        assertFalse(service.hasPermission(user(1L, null), "document:read:any"));
    }

    @Test
    void canView_nullViewerOrDoc_false() {
        assertFalse(service.canView(null, doc(1L, "PUBLIC", null)));
        assertFalse(service.canView(user(1L, null), null));
    }

    @Test
    void canView_readAny_true() {
        var viewer = user(1L, "eng", "document:read:any");
        assertTrue(service.canView(viewer, doc(99L, "PRIVATE", "hr")));
    }

    @Test
    void canView_owner_true() {
        var viewer = user(1L, "eng");
        assertTrue(service.canView(viewer, doc(1L, "PRIVATE", "hr")));
    }

    @Test
    void canView_public_requiresPermission() {
        assertTrue(service.canView(user(1L, null, "document:read:public"), doc(2L, "PUBLIC", null)));
        assertFalse(service.canView(user(1L, null), doc(2L, "PUBLIC", null)));
    }

    @Test
    void canView_department_requiresSameDept() {
        assertTrue(service.canView(user(1L, "eng", "document:read:department"), doc(2L, "DEPARTMENT", "eng")));
        assertFalse(service.canView(user(1L, "eng", "document:read:department"), doc(2L, "DEPARTMENT", "hr")));
    }

    @Test
    void canView_executive_requiresPermission() {
        assertTrue(service.canView(user(1L, null, "document:read:executive"), doc(2L, "EXECUTIVE", null)));
        assertFalse(service.canView(user(1L, null, "document:read:department"), doc(2L, "EXECUTIVE", null)));
    }

    @Test
    void canView_private_requiresPermissionAndOwnership() {
        assertTrue(service.canView(user(1L, null, "document:read:private"), doc(1L, "PRIVATE", null)));
        assertFalse(service.canView(user(1L, null, "document:read:private"), doc(2L, "PRIVATE", null)));
    }

    @Test
    void canManage_allOrOwn() {
        var owner = user(1L, "eng", "document:manage:own");
        assertTrue(service.canManage(owner, doc(1L, "PUBLIC", null)));
        assertFalse(service.canManage(owner, doc(2L, "PUBLIC", null)));
        assertTrue(service.canManage(user(9L, null, "document:manage:all"), doc(2L, "PUBLIC", null)));
    }
}

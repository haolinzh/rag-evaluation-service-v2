package com.rag.eval.integration;

import com.rag.eval.model.AppNotification;
import com.rag.eval.model.AuthenticatedUser;
import com.rag.eval.repository.AppNotificationRepo;
import com.rag.eval.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AppNotificationRepo repo;

    private final AuthenticatedUser admin =
        new AuthenticatedUser(1L, "admin", "管理员", "系统管理", Set.of("user:manage", "message:view"));

    private final AuthenticatedUser alice =
        new AuthenticatedUser(100L, "alice", "Alice", "研发部", Set.of("message:view"));

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    @Test
    void adminSeesAllNotifications() {
        notificationService.notify("document", "t1", "c1", 100L, "alice", null);
        notificationService.notify("document", "t2", "c2", 200L, "bob", "alice");
        notificationService.notify("document", "t3", "c3", 200L, "bob", null);

        List<AppNotification> all = notificationService.listFor(admin, 100);
        assertEquals(3, all.size());
        assertEquals(3, notificationService.unreadCount(admin));
    }

    @Test
    void userSeesOnlyOwnAndTargeted() {
        notificationService.notify("document", "t1", "c1", 100L, "alice", null);
        notificationService.notify("document", "t2", "c2", 200L, "bob", "alice");
        notificationService.notify("document", "t3", "c3", 200L, "bob", null);

        List<AppNotification> visible = notificationService.listFor(alice, 100);
        assertEquals(2, visible.size());
        assertTrue(visible.stream().allMatch(n ->
            (n.getActorId() != null && n.getActorId().equals(100L)) || "alice".equals(n.getTargetUsername())));
    }

    @Test
    void unrelatedUserSeesNothing() {
        notificationService.notify("document", "t1", "c1", 100L, "alice", null);
        notificationService.notify("document", "t2", "c2", 200L, "bob", null);

        AuthenticatedUser stranger =
            new AuthenticatedUser(300L, "carol", "Carol", "市场部", Set.of("message:view"));
        assertEquals(0, notificationService.listFor(stranger, 100).size());
        assertEquals(0, notificationService.unreadCount(stranger));
    }

    @Test
    void markAllReadScopedToViewer() {
        notificationService.notify("document", "t1", "c1", 100L, "alice", null);
        notificationService.notify("document", "t2", "c2", 200L, "bob", "alice");
        notificationService.notify("document", "t3", "c3", 200L, "bob", null);

        notificationService.markAllRead(alice);

        // alice 可见的 2 条已读，bob 的那条仍未读（对 admin 而言）
        assertEquals(0, notificationService.unreadCount(alice));
        assertEquals(1, notificationService.unreadCount(admin));
    }

    @Test
    void adminMarkAllReadClearsEverything() {
        notificationService.notify("document", "t1", "c1", 100L, "alice", null);
        notificationService.notify("document", "t2", "c2", 200L, "bob", null);

        notificationService.markAllRead(admin);

        assertEquals(0, notificationService.unreadCount(admin));
        assertEquals(0, notificationService.unreadCount(alice));
    }
}

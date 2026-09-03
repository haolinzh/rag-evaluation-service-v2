package com.rag.eval.integration;

import com.rag.eval.model.AppNotification;
import com.rag.eval.repository.AppNotificationRepo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppNotificationRepoIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AppNotificationRepo repo;

    @Autowired
    private PlatformTransactionManager txManager;

    @BeforeEach
    void clean() {
        repo.deleteAll();
    }

    private AppNotification n(Long actorId, String targetUsername, boolean read) {
        AppNotification n = new AppNotification();
        n.setType("document");
        n.setTitle("t");
        n.setContent("c");
        n.setActorId(actorId);
        n.setTargetUsername(targetUsername);
        n.setRead(read);
        return repo.save(n);
    }

    @Test
    void findVisibleToMatchesActorOrTarget() {
        n(100L, null, false);
        n(200L, "alice", false);
        n(200L, null, false);
        n(null, "bob", false);

        List<AppNotification> visible = repo.findVisibleTo(100L, "alice", PageRequest.of(0, 20));
        assertEquals(2, visible.size());
        assertTrue(visible.stream().allMatch(x ->
            (x.getActorId() != null && x.getActorId().equals(100L)) || "alice".equals(x.getTargetUsername())));
    }

    @Test
    void countUnreadVisibleToRespectsReadFlagAndScope() {
        n(100L, null, false);
        n(100L, null, true);
        n(200L, "alice", false);
        n(200L, null, false);

        assertEquals(2, repo.countUnreadVisibleTo(100L, "alice"));
    }

    @Test
    void markVisibleReadOnlyAffectsVisible() {
        n(100L, null, false);
        n(200L, "alice", false);
        n(200L, null, false);

        new TransactionTemplate(txManager).executeWithoutResult(s -> repo.markVisibleRead(100L, "alice"));

        assertEquals(0, repo.countUnreadVisibleTo(100L, "alice"));
        assertEquals(1, repo.countByReadFalse());
    }
}

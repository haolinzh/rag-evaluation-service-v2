package com.rag.eval.service;

import com.rag.eval.model.AppNotification;
import com.rag.eval.model.AuthenticatedUser;
import com.rag.eval.repository.AppNotificationRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final AppNotificationRepo repo;

    public NotificationService(AppNotificationRepo repo) {
        this.repo = repo;
    }

    public void notify(String type, String title, String content,
                       Long actorId, String actorName, String targetUsername) {
        AppNotification n = new AppNotification();
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setActorId(actorId);
        n.setActorName(actorName);
        n.setTargetUsername(targetUsername);
        n.setRead(false);
        repo.save(n);
    }

    public void notify(String type, String title, String content,
                       AuthenticatedUser actor, String targetUsername) {
        notify(type, title, content,
            actor != null ? actor.id() : null,
            actor != null ? actor.username() : null,
            targetUsername);
    }

    public List<AppNotification> listFor(AuthenticatedUser viewer, int limit) {
        int size = limit > 0 && limit <= MAX_LIMIT ? limit : DEFAULT_LIMIT;
        Pageable pageable = PageRequest.of(0, size);
        if (isAdmin(viewer)) {
            return repo.findAllByOrderByIdDesc(pageable);
        }
        return repo.findVisibleTo(viewer.id(), viewer.username(), pageable);
    }

    public long unreadCount(AuthenticatedUser viewer) {
        if (isAdmin(viewer)) {
            return repo.countByReadFalse();
        }
        return repo.countUnreadVisibleTo(viewer.id(), viewer.username());
    }

    @Transactional
    public void markAllRead(AuthenticatedUser viewer) {
        if (isAdmin(viewer)) {
            repo.markAllRead();
        } else {
            repo.markVisibleRead(viewer.id(), viewer.username());
        }
    }

    private boolean isAdmin(AuthenticatedUser viewer) {
        return viewer != null && viewer.permissions() != null
            && (viewer.permissions().contains("user:manage") || viewer.permissions().contains("role:manage"));
    }
}

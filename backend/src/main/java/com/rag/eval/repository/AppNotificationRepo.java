package com.rag.eval.repository;

import com.rag.eval.model.AppNotification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AppNotificationRepo extends JpaRepository<AppNotification, Long> {

    List<AppNotification> findAllByOrderByIdDesc(Pageable pageable);

    @Query("select n from AppNotification n where n.actorId = :actorId or n.targetUsername = :username order by n.id desc")
    List<AppNotification> findVisibleTo(@Param("actorId") Long actorId, @Param("username") String username, Pageable pageable);

    long countByReadFalse();

    @Query("select count(n) from AppNotification n where n.read = false and (n.actorId = :actorId or n.targetUsername = :username)")
    long countUnreadVisibleTo(@Param("actorId") Long actorId, @Param("username") String username);

    @Modifying
    @Query("update AppNotification n set n.read = true where n.read = false")
    void markAllRead();

    @Modifying
    @Query("update AppNotification n set n.read = true where n.read = false and (n.actorId = :actorId or n.targetUsername = :username)")
    void markVisibleRead(@Param("actorId") Long actorId, @Param("username") String username);
}

package com.rag.eval.repository;

import com.rag.eval.model.DocumentMeta;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentMetaRepo extends JpaRepository<DocumentMeta, Long> {
    Optional<DocumentMeta> findByFileName(String fileName);

    List<DocumentMeta> findByFileNameIn(Collection<String> fileNames);

    @Query("select m.id from DocumentMeta m where m.status = 'QUEUED' and m.nextRetryAt <= :now order by m.id asc")
    List<Long> findQueuedIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update DocumentMeta m set m.status = 'PROCESSING', m.attemptCount = m.attemptCount + 1, "
         + "m.claimedAt = :now, m.nextRetryAt = null "
         + "where m.id = :id and m.status = 'QUEUED' and m.nextRetryAt <= :now")
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update DocumentMeta m set m.status = 'READY', m.chunkCount = :chunkCount, "
         + "m.errorMessage = null, m.claimedAt = null where m.id = :id and m.status = 'PROCESSING'")
    int markReady(@Param("id") Long id, @Param("chunkCount") int chunkCount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update DocumentMeta m set m.status = 'FAILED', m.errorMessage = :msg, m.claimedAt = null "
         + "where m.id = :id and m.status = 'PROCESSING'")
    int markFailed(@Param("id") Long id, @Param("msg") String msg);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update DocumentMeta m set m.status = 'QUEUED', m.nextRetryAt = :nextRetry, m.errorMessage = :msg "
         + "where m.id = :id and m.status = 'PROCESSING'")
    int markRetry(@Param("id") Long id, @Param("nextRetry") LocalDateTime nextRetry, @Param("msg") String msg);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update DocumentMeta m set m.status = 'QUEUED', m.attemptCount = 0, m.nextRetryAt = :now, "
         + "m.chunkCount = null, m.errorMessage = null "
         + "where m.id = :id and m.status in ('READY', 'FAILED')")
    int requeue(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update DocumentMeta m set m.status = 'QUEUED', m.nextRetryAt = :now, m.claimedAt = null "
         + "where m.status = 'PROCESSING'")
    int resetAllProcessing(@Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("update DocumentMeta m set m.status = 'QUEUED', m.nextRetryAt = :now "
         + "where m.status = 'PROCESSING' and m.claimedAt <= :cutoff")
    int resetStaleProcessing(@Param("now") LocalDateTime now, @Param("cutoff") LocalDateTime cutoff);
}

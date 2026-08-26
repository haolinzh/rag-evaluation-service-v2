package com.rag.eval.repository;

import com.rag.eval.model.RequestLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestLogRepo extends JpaRepository<RequestLog, Long> {
    List<RequestLog> findByOwnerIdOrderByIdDesc(Long ownerId, Pageable pageable);
}

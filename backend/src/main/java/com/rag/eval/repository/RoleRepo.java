package com.rag.eval.repository;

import com.rag.eval.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepo extends JpaRepository<Role, Long> {
    Optional<Role> findByCode(String code);

    boolean existsByCode(String code);
}

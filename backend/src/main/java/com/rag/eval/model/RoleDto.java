package com.rag.eval.model;

import java.util.Set;

public record RoleDto(
    Long id,
    String code,
    String name,
    String description,
    boolean builtin,
    Set<String> permissionCodes
) {}

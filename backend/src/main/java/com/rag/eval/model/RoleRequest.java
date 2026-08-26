package com.rag.eval.model;

import java.util.Set;

public record RoleRequest(
    String code,
    String name,
    String description,
    Set<String> permissionCodes
) {}

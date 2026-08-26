package com.rag.eval.model;

import java.util.Set;

public record UserDto(
    Long id,
    String username,
    String displayName,
    String department,
    boolean enabled,
    Set<String> roleCodes
) {}

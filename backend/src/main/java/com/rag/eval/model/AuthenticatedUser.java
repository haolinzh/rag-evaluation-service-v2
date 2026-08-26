package com.rag.eval.model;

import java.util.Set;

public record AuthenticatedUser(
    Long id,
    String username,
    String displayName,
    String department,
    Set<String> permissions
) {}

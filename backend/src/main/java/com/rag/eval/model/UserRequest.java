package com.rag.eval.model;

import java.util.Set;

public record UserRequest(
    String username,
    String password,
    String displayName,
    String department,
    Boolean enabled,
    Set<String> roleCodes
) {}

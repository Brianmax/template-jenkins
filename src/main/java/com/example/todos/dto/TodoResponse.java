package com.example.todos.dto;

import java.time.OffsetDateTime;

public record TodoResponse(
    Long id,
    String title,
    String description,
    boolean completed,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}

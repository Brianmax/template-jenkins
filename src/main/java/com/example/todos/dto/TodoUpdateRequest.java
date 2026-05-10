package com.example.todos.dto;

import jakarta.validation.constraints.Size;

public record TodoUpdateRequest(
    @Size(min = 1, max = 255, message = "Title must be between 1 and 255 characters")
    String title,

    @Size(max = 5000, message = "Description must be at most 5000 characters")
    String description,

    Boolean completed
) {}

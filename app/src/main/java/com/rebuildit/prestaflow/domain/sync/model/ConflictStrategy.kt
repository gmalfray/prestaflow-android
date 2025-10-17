package com.rebuildit.prestaflow.domain.sync.model

enum class ConflictStrategy {
    LAST_WRITE_WINS,
    MERGE,
    PROMPT_USER
}

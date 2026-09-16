package com.sc.verdict.evidence;

import com.sc.verdict.shared.Ids.DocumentId;

import java.util.Objects;

/**
 * A submitted document, reduced to what the decision needs: its identity, its type, and a content
 * hash. The hash — not the bytes — is what a determination pins, so a replay proves the same
 * evidence was examined without the engine having to hold the document.
 */
public record Document(DocumentId id, String type, String contentHash) {

    public Document {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(contentHash, "contentHash");
    }
}

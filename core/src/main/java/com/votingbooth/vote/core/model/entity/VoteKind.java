package com.votingbooth.vote.core.model.entity;

// What a scrutin is about, derived from its title (see ScrutinKindClassifier).
public enum VoteKind {
    FINAL_TEXT,
    PART,
    ARTICLE,
    AMENDMENT,
    MOTION,
    PROCEDURAL,
    OTHER
}

package com.votingbooth.vote.job.model.scrutin;

// One file of the archive, kept as raw bytes so it can be hashed and stored as it is.
public record ArchiveEntry(String externalId, byte[] content) {
}

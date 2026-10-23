package com.votingbooth.vote.job.dataaccess.client.storage;

public interface RawStorageClient {

    // Returns the version id of the stored object, or null if the bucket isn't versioned.
    String put(String key, byte[] content);

    boolean isVersioningEnabled();

}

package com.votingbooth.vote.job.dataaccess.client.assembleenationale;

import com.votingbooth.vote.job.model.scrutin.ArchiveDownload;

public interface ScrutinArchiveClient {

    // Downloads the archive to a temporary file, which the caller deletes.
    ArchiveDownload download(String url);

}

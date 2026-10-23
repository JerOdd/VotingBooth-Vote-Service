package com.votingbooth.vote.job.dataaccess.client.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketVersioningStatus;
import software.amazon.awssdk.services.s3.model.GetBucketVersioningRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class S3RawStorageClient implements RawStorageClient {

    private final S3Client s3Client;
    private final String bucket;

    public S3RawStorageClient(S3Client s3Client, @Value("${storage.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public String put(String key, byte[] content) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType("application/json")
                .build();
        return s3Client.putObject(request, RequestBody.fromBytes(content)).versionId();
    }

    @Override
    public boolean isVersioningEnabled() {
        GetBucketVersioningRequest request = GetBucketVersioningRequest.builder().bucket(bucket).build();
        return s3Client.getBucketVersioning(request).status() == BucketVersioningStatus.ENABLED;
    }

}

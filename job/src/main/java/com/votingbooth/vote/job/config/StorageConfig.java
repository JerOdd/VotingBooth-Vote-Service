package com.votingbooth.vote.job.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

// Any S3-compatible store (OVH Object Storage in production, SeaweedFS or MinIO locally): only the endpoint and region change.
@Configuration
public class StorageConfig {

    @Bean
    public S3Client s3Client(@Value("${storage.endpoint}") String endpoint,
                             @Value("${storage.region}") String region,
                             @Value("${storage.access-key}") String accessKey,
                             @Value("${storage.secret-key}") String secretKey,
                             @Value("${storage.path-style:false}") boolean pathStyle) {
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(pathStyle)
                // Recent SDKs add checksums to every request by default, which some S3-compatible providers reject.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

}

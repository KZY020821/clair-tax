package com.clairtax.backend.receipt.storage;

import com.clairtax.backend.receipt.config.ReceiptProcessingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.regions.Region;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

@Service
@ConditionalOnExpression("!'${clair.receipts.bucket-name:}'.isEmpty()")
public class AwsReceiptObjectStorageService implements ReceiptObjectStorageService {

    private final ReceiptProcessingProperties properties;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    public AwsReceiptObjectStorageService(ReceiptProcessingProperties properties) {
        Region region = Region.of(properties.getAws().getRegion());
        this.properties = properties;
        this.s3Client = S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
        this.s3Presigner = S3Presigner.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Override
    public ReceiptUploadTarget createUploadTarget(String objectKey, String mimeType) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.getBucketName())
                .key(objectKey)
                .contentType(mimeType)
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build();
        Duration signatureDuration = Duration.parse(properties.getUploadIntentTtl());
        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .putObjectRequest(putObjectRequest)
                        .signatureDuration(signatureDuration)
                        .build()
        );

        return new ReceiptUploadTarget(
                presignedRequest.url().toString(),
                "PUT",
                Map.of("Content-Type", mimeType),
                OffsetDateTime.now().plus(signatureDuration)
        );
    }

    @Override
    public void storeUploadedObject(String objectKey, InputStream inputStream) throws IOException {
        try {
            byte[] body = inputStream.readAllBytes();
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.getBucketName())
                            .key(objectKey)
                            .serverSideEncryption(ServerSideEncryption.AES256)
                            .build(),
                    software.amazon.awssdk.core.sync.RequestBody.fromBytes(body)
            );
        } catch (S3Exception exception) {
            throw new IOException("Unable to store receipt object in S3", exception);
        }
    }

    @Override
    public Resource load(String objectKey) throws IOException {
        try {
            ResponseInputStream<GetObjectResponse> stream = s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(properties.getBucketName())
                            .key(objectKey)
                            .build()
            );
            return new InputStreamResource(stream);
        } catch (NoSuchKeyException exception) {
            throw new IOException("Stored receipt object was not found", exception);
        } catch (S3Exception exception) {
            throw new IOException("Unable to load receipt object from S3", exception);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(properties.getBucketName())
                            .key(objectKey)
                            .build()
            );
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            return false;
        }
    }

    @Override
    public long size(String objectKey) throws IOException {
        try {
            return s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(properties.getBucketName())
                            .key(objectKey)
                            .build()
            ).contentLength();
        } catch (S3Exception exception) {
            throw new IOException("Unable to read receipt object size from S3", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        s3Client.deleteObject(builder -> builder.bucket(properties.getBucketName()).key(objectKey));
    }
}

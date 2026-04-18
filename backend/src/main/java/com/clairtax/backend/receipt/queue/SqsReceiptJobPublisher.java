package com.clairtax.backend.receipt.queue;

import com.clairtax.backend.receipt.config.ReceiptProcessingProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Service
@Profile("!local & !test & !postgres")
public class SqsReceiptJobPublisher implements ReceiptJobPublisher {

    private final ReceiptProcessingProperties properties;
    private final ObjectMapper objectMapper;
    private final SqsClient sqsClient;

    public SqsReceiptJobPublisher(
            ReceiptProcessingProperties properties,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.sqsClient = SqsClient.builder()
                .region(Region.of(properties.getAws().getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Override
    public void publish(ReceiptProcessingJobMessage jobMessage) {
        try {
            SendMessageRequest.Builder requestBuilder = SendMessageRequest.builder()
                    .queueUrl(properties.getQueue().getUrl())
                    .messageBody(objectMapper.writeValueAsString(jobMessage));

            if (properties.getQueue().getUrl().endsWith(".fifo")) {
                requestBuilder.messageGroupId(jobMessage.receiptId().toString());
                requestBuilder.messageDeduplicationId(jobMessage.jobId().toString());
            }

            sqsClient.sendMessage(requestBuilder.build());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize receipt processing job", exception);
        }
    }
}

package com.clairtax.backend.receipt.queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"test", "postgres"})
public class NoopReceiptJobPublisher implements ReceiptJobPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoopReceiptJobPublisher.class);

    @Override
    public void publish(ReceiptProcessingJobMessage jobMessage) {
        LOGGER.info(
                "Skipping receipt job publish for local/test execution: jobId={}, receiptId={}",
                jobMessage.jobId(),
                jobMessage.receiptId()
        );
    }
}

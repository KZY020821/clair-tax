package com.clairtax.backend.receipt.queue;

public interface ReceiptJobPublisher {

    void publish(ReceiptProcessingJobMessage jobMessage);
}

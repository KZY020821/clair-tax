package com.clairtax.backend.receipt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "clair.receipts")
public class ReceiptProcessingProperties {

    private String bucketName;
    private String uploadIntentTtl = "PT15M";
    private String internalApiToken;
    private Queue queue = new Queue();
    private Aws aws = new Aws();

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public String getUploadIntentTtl() {
        return uploadIntentTtl;
    }

    public void setUploadIntentTtl(String uploadIntentTtl) {
        this.uploadIntentTtl = uploadIntentTtl;
    }

    public String getInternalApiToken() {
        return internalApiToken;
    }

    public void setInternalApiToken(String internalApiToken) {
        this.internalApiToken = internalApiToken;
    }

    public Queue getQueue() {
        return queue;
    }

    public void setQueue(Queue queue) {
        this.queue = queue;
    }

    public Aws getAws() {
        return aws;
    }

    public void setAws(Aws aws) {
        this.aws = aws;
    }

    public static class Queue {

        private String url = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class Aws {

        private String region = "ap-southeast-1";

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }
    }
}

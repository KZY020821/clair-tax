package com.clairtax.backend.receipt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "clair.receipts")
public class ReceiptProcessingProperties {

    private String storagePath = "/tmp/clair-tax-receipts";
    private String uploadPath = "/tmp/clair-tax-receipt-uploads";
    private String bucketName = "clair-tax-receipts";
    private String uploadIntentTtl = "PT15M";
    private String internalApiToken = "dev-internal-token";
    private Queue queue = new Queue();
    private Aws aws = new Aws();

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getUploadPath() {
        return uploadPath;
    }

    public void setUploadPath(String uploadPath) {
        this.uploadPath = uploadPath;
    }

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

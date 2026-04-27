package com.clairtax.backend.receipt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "clair.ai-service")
public class AiServiceProperties {

    private String baseUrl = "http://localhost:8000";
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 30;
    private double amountConfidenceThreshold = 0.5;
    private double dateConfidenceThreshold = 0.4;
    private double merchantConfidenceThreshold = 0.3;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public double getAmountConfidenceThreshold() {
        return amountConfidenceThreshold;
    }

    public void setAmountConfidenceThreshold(double amountConfidenceThreshold) {
        this.amountConfidenceThreshold = amountConfidenceThreshold;
    }

    public double getDateConfidenceThreshold() {
        return dateConfidenceThreshold;
    }

    public void setDateConfidenceThreshold(double dateConfidenceThreshold) {
        this.dateConfidenceThreshold = dateConfidenceThreshold;
    }

    public double getMerchantConfidenceThreshold() {
        return merchantConfidenceThreshold;
    }

    public void setMerchantConfidenceThreshold(double merchantConfidenceThreshold) {
        this.merchantConfidenceThreshold = merchantConfidenceThreshold;
    }
}

package com.clairtax.backend.receipt.service;

import com.clairtax.backend.receipt.config.AiServiceProperties;
import com.clairtax.backend.receipt.dto.AiExtractionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Service
public class AiExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(AiExtractionService.class);

    private final AiServiceProperties properties;
    private final RestTemplate restTemplate;

    public AiExtractionService(AiServiceProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutSeconds() * 1000);
        factory.setReadTimeout(properties.getReadTimeoutSeconds() * 1000);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Calls POST /api/demo-summary on the AI service with the file bytes.
     * Returns null on any failure. Never throws.
     */
    public AiExtractionResult extractFields(byte[] fileBytes, String fileName, String mimeType) {
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            };
            body.add("file", fileResource);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            String url = properties.getBaseUrl() + "/api/demo-summary";

            ResponseEntity<AiExtractionResult> response = restTemplate.postForEntity(
                    url, request, AiExtractionResult.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                logger.warn("AI service returned non-2xx or empty body: {}", response.getStatusCode());
                return null;
            }
            return response.getBody();
        } catch (RestClientException e) {
            logger.warn("AI service unreachable or returned error during extraction: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            logger.warn("Unexpected error calling AI extraction service: {}", e.getMessage());
            return null;
        }
    }

    public record FilteredExtractionFields(
            String merchantName,
            LocalDate receiptDate,
            BigDecimal amount,
            String currency
    ) {}

    /**
     * Applies per-field confidence thresholds. Returns all-null record on failed/no-text status.
     */
    public FilteredExtractionFields applyThresholds(AiExtractionResult raw) {
        if (raw == null) {
            return new FilteredExtractionFields(null, null, null, null);
        }

        String status = raw.extractionStatus();
        if ("failed".equals(status) || "no_text_detected".equals(status)) {
            return new FilteredExtractionFields(null, null, null, null);
        }

        String merchantName = null;
        if (raw.merchantName() != null
                && raw.merchantConfidence() != null
                && raw.merchantConfidence() >= properties.getMerchantConfidenceThreshold()) {
            merchantName = raw.merchantName();
        }

        LocalDate receiptDate = null;
        if (raw.date() != null
                && raw.dateConfidence() != null
                && raw.dateConfidence() >= properties.getDateConfidenceThreshold()) {
            try {
                receiptDate = LocalDate.parse(raw.date());
            } catch (DateTimeParseException e) {
                logger.debug("Could not parse AI-extracted date '{}': {}", raw.date(), e.getMessage());
            }
        }

        BigDecimal amount = null;
        String currency = null;
        if (raw.amount() != null
                && raw.amountConfidence() != null
                && raw.amountConfidence() >= properties.getAmountConfidenceThreshold()) {
            try {
                amount = new BigDecimal(raw.amount());
                currency = raw.currency();
            } catch (NumberFormatException e) {
                logger.debug("Could not parse AI-extracted amount '{}': {}", raw.amount(), e.getMessage());
            }
        }

        return new FilteredExtractionFields(merchantName, receiptDate, amount, currency);
    }
}

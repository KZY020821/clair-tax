package com.clairtax.backend.receipt.service;

import com.clairtax.backend.calculator.exception.ResourceNotFoundException;
import com.clairtax.backend.receipt.config.ReceiptProcessingProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InternalReceiptApiAccessVerifier {

    public static final String INTERNAL_TOKEN_HEADER = "X-Clair-Internal-Token";

    private final ReceiptProcessingProperties properties;

    public InternalReceiptApiAccessVerifier(ReceiptProcessingProperties properties) {
        this.properties = properties;
    }

    public void verify(HttpServletRequest request) {
        String providedToken = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (providedToken == null || !providedToken.equals(properties.getInternalApiToken())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal API token is invalid");
        }
    }
}

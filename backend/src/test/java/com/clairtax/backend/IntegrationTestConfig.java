package com.clairtax.backend;

import com.clairtax.backend.receipt.queue.ReceiptJobPublisher;
import com.clairtax.backend.receipt.storage.ReceiptObjectStorageService;
import com.clairtax.backend.receipt.storage.ReceiptUploadTarget;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.repository.AppUserRepository;
import com.clairtax.backend.user.service.CurrentUser;
import com.clairtax.backend.user.service.CurrentUserProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Map;

@TestConfiguration
public class IntegrationTestConfig {

    static final String TEST_USER_EMAIL = "dev@taxrelief.local";
    private static final String TEST_UPLOAD_BASE = "/api/internal/test-uploads";

    @Bean
    public ApplicationRunner testUserBootstrap(AppUserRepository appUserRepository) {
        return args -> appUserRepository.findByEmail(TEST_USER_EMAIL)
                .orElseGet(() -> appUserRepository.save(new AppUser(TEST_USER_EMAIL)));
    }

    @Bean
    @Primary
    public CurrentUserProvider testCurrentUserProvider(AppUserRepository appUserRepository) {
        return () -> {
            AppUser user = appUserRepository.findByEmail(TEST_USER_EMAIL)
                    .orElseThrow(() -> new IllegalStateException("Test user not bootstrapped"));
            return new CurrentUser(user.getId(), user.getEmail());
        };
    }

    @Bean
    @Primary
    public ReceiptObjectStorageService testReceiptObjectStorageService() {
        return new ReceiptObjectStorageService() {
            @Override
            public ReceiptUploadTarget createUploadTarget(String objectKey, String mimeType) {
                return new ReceiptUploadTarget(
                        TEST_UPLOAD_BASE + "/" + objectKey,
                        "PUT",
                        Map.of("Content-Type", mimeType),
                        OffsetDateTime.now().plusMinutes(15)
                );
            }

            @Override
            public void storeUploadedObject(String objectKey, InputStream inputStream) {
            }

            @Override
            public Resource load(String objectKey) {
                return new ByteArrayResource("test-receipt-content".getBytes());
            }

            @Override
            public boolean exists(String objectKey) {
                return true;
            }

            @Override
            public long size(String objectKey) {
                return 0L;
            }

            @Override
            public void delete(String objectKey) {
            }
        };
    }

    @Bean
    @Primary
    public ReceiptJobPublisher testReceiptJobPublisher() {
        return jobMessage -> {};
    }

    @RestController
    @RequestMapping(TEST_UPLOAD_BASE)
    static class TestUploadEndpoint {
        @PutMapping("/{objectKey}")
        public ResponseEntity<Void> handleUpload(@PathVariable String objectKey) {
            return ResponseEntity.noContent().build();
        }
    }
}

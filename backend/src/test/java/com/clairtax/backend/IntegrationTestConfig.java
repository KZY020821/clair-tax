package com.clairtax.backend;

import com.clairtax.backend.receipt.queue.ReceiptJobPublisher;
import com.clairtax.backend.receipt.storage.ReceiptObjectStorageService;
import com.clairtax.backend.receipt.storage.ReceiptUploadTarget;
import com.clairtax.backend.user.entity.AppUser;
import com.clairtax.backend.user.repository.AppUserRepository;
import com.clairtax.backend.user.service.CurrentUser;
import com.clairtax.backend.user.service.CurrentUserProvider;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@TestConfiguration
public class IntegrationTestConfig {

    static final String TEST_USER_EMAIL = "dev@taxrelief.local";
    private static final String TEST_UPLOAD_BASE = "/api/internal/test-uploads";

    private final Map<String, byte[]> uploadedObjects = new ConcurrentHashMap<>();

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
                byte[] data = uploadedObjects.getOrDefault(objectKey, "test-receipt-content".getBytes());
                return new ByteArrayResource(data);
            }

            @Override
            public boolean exists(String objectKey) {
                return uploadedObjects.containsKey(objectKey);
            }

            @Override
            public long size(String objectKey) throws IOException {
                byte[] data = uploadedObjects.get(objectKey);
                if (data == null) {
                    throw new IOException("Object not found in test store: " + objectKey);
                }
                return data.length;
            }

            @Override
            public void delete(String objectKey) {
                uploadedObjects.remove(objectKey);
            }
        };
    }

    @Bean
    @Primary
    public ReceiptJobPublisher testReceiptJobPublisher() {
        return jobMessage -> {};
    }

    @Bean
    public FilterRegistrationBean<TestUploadFilter> testUploadFilter() {
        FilterRegistrationBean<TestUploadFilter> reg = new FilterRegistrationBean<>(
                new TestUploadFilter(uploadedObjects, TEST_UPLOAD_BASE)
        );
        reg.addUrlPatterns("/*");
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return reg;
    }

    static class TestUploadFilter implements Filter {

        private final Map<String, byte[]> uploadedObjects;
        private final String basePath;

        TestUploadFilter(Map<String, byte[]> uploadedObjects, String basePath) {
            this.uploadedObjects = uploadedObjects;
            this.basePath = basePath;
        }

        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) res;
            String uri = request.getRequestURI();
            if ("PUT".equals(request.getMethod()) && uri.startsWith(basePath + "/")) {
                String objectKey = uri.substring(basePath.length() + 1);
                byte[] body = request.getInputStream().readAllBytes();
                uploadedObjects.put(objectKey, body);
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }
            chain.doFilter(req, res);
        }
    }
}

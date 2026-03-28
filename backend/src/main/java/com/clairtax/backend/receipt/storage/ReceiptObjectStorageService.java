package com.clairtax.backend.receipt.storage;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

public interface ReceiptObjectStorageService {

    ReceiptUploadTarget createUploadTarget(String objectKey, String mimeType) throws IOException;

    void storeUploadedObject(String objectKey, InputStream inputStream) throws IOException;

    Resource load(String objectKey) throws IOException;

    boolean exists(String objectKey) throws IOException;

    long size(String objectKey) throws IOException;

    void delete(String objectKey) throws IOException;
}

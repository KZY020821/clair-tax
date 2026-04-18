package com.clairtax.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "clair.auth")
public class AuthProperties {

    private String frontendBaseUrl = "http://localhost:3000";
    private String publicBaseUrl = "";
    private String mobileAppScheme = "clair-tax";
    private String magicLinkTtl = "PT15M";
    private String otpTtl = "PT10M";
    private String otpResendCooldown = "PT30S";
    private String otpRateLimitWindow = "PT1H";
    private int otpMaxAttempts = 5;
    private int otpMaxRequestsPerEmail = 5;
    private int otpMaxRequestsPerIp = 20;
    private int otpMaxRequestsPerDevice = 10;
    private String sessionTtl = "P30D";
    private String cookieName = "clair_tax_session";

    public String getFrontendBaseUrl() {
        return frontendBaseUrl;
    }

    public void setFrontendBaseUrl(String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    public String getMobileAppScheme() {
        return mobileAppScheme;
    }

    public void setMobileAppScheme(String mobileAppScheme) {
        this.mobileAppScheme = mobileAppScheme;
    }

    public String getMagicLinkTtl() {
        return magicLinkTtl;
    }

    public void setMagicLinkTtl(String magicLinkTtl) {
        this.magicLinkTtl = magicLinkTtl;
    }

    public String getOtpTtl() {
        return otpTtl;
    }

    public void setOtpTtl(String otpTtl) {
        this.otpTtl = otpTtl;
    }

    public String getOtpResendCooldown() {
        return otpResendCooldown;
    }

    public void setOtpResendCooldown(String otpResendCooldown) {
        this.otpResendCooldown = otpResendCooldown;
    }

    public String getOtpRateLimitWindow() {
        return otpRateLimitWindow;
    }

    public void setOtpRateLimitWindow(String otpRateLimitWindow) {
        this.otpRateLimitWindow = otpRateLimitWindow;
    }

    public int getOtpMaxAttempts() {
        return otpMaxAttempts;
    }

    public void setOtpMaxAttempts(int otpMaxAttempts) {
        this.otpMaxAttempts = otpMaxAttempts;
    }

    public int getOtpMaxRequestsPerEmail() {
        return otpMaxRequestsPerEmail;
    }

    public void setOtpMaxRequestsPerEmail(int otpMaxRequestsPerEmail) {
        this.otpMaxRequestsPerEmail = otpMaxRequestsPerEmail;
    }

    public int getOtpMaxRequestsPerIp() {
        return otpMaxRequestsPerIp;
    }

    public void setOtpMaxRequestsPerIp(int otpMaxRequestsPerIp) {
        this.otpMaxRequestsPerIp = otpMaxRequestsPerIp;
    }

    public int getOtpMaxRequestsPerDevice() {
        return otpMaxRequestsPerDevice;
    }

    public void setOtpMaxRequestsPerDevice(int otpMaxRequestsPerDevice) {
        this.otpMaxRequestsPerDevice = otpMaxRequestsPerDevice;
    }

    public String getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(String sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public String getCookieName() {
        return cookieName;
    }

    public void setCookieName(String cookieName) {
        this.cookieName = cookieName;
    }
}

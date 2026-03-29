package com.clairtax.backend.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "clair.auth")
public class AuthProperties {

    private String frontendBaseUrl = "http://localhost:3000";
    private String magicLinkTtl = "PT15M";
    private String sessionTtl = "P30D";
    private String cookieName = "clair_tax_session";

    public String getFrontendBaseUrl() {
        return frontendBaseUrl;
    }

    public void setFrontendBaseUrl(String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public String getMagicLinkTtl() {
        return magicLinkTtl;
    }

    public void setMagicLinkTtl(String magicLinkTtl) {
        this.magicLinkTtl = magicLinkTtl;
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

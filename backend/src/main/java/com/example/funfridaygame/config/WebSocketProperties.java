package com.example.funfridaygame.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketProperties {

    private String endpoint = "/ws";
    private String allowedOrigins = "http://localhost:4200";
    private int messageSizeLimit = 512 * 1024;
    private int sendBufferSize = 1024 * 1024;
    private int sendTimeLimit = 20000;

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public String[] getAllowedOriginsArray() {
        return allowedOrigins.split(",");
    }

    public int getMessageSizeLimit() {
        return messageSizeLimit;
    }

    public void setMessageSizeLimit(int messageSizeLimit) {
        this.messageSizeLimit = messageSizeLimit;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public void setSendBufferSize(int sendBufferSize) {
        this.sendBufferSize = sendBufferSize;
    }

    public int getSendTimeLimit() {
        return sendTimeLimit;
    }

    public void setSendTimeLimit(int sendTimeLimit) {
        this.sendTimeLimit = sendTimeLimit;
    }
}

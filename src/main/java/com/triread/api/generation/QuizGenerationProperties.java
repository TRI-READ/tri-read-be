package com.triread.api.generation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.quiz-generation")
public class QuizGenerationProperties {
    private boolean enabled;
    private boolean autoPublish;
    private int maxAttempts = 3;
    private int passScore = 90;
    private int variantsPerDate = 3;
    private long retryDelayMs = 10_000;
    private String cron = "0 10 3 * * *";
    private final Gemini gemini = new Gemini();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isAutoPublish() { return autoPublish; }
    public void setAutoPublish(boolean autoPublish) { this.autoPublish = autoPublish; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public int getPassScore() { return passScore; }
    public void setPassScore(int passScore) { this.passScore = passScore; }
    public int getVariantsPerDate() { return variantsPerDate; }
    public void setVariantsPerDate(int variantsPerDate) { this.variantsPerDate = variantsPerDate; }
    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public Gemini getGemini() { return gemini; }

    public static class Gemini {
        private String apiKey = "";
        private String baseUrl = "https://generativelanguage.googleapis.com";
        private String generationModel = "gemini-3.1-flash-lite";
        private String validationModel = "gemini-3.1-flash-lite";
        private String promptVersion = "v2";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getGenerationModel() { return generationModel; }
        public void setGenerationModel(String generationModel) { this.generationModel = generationModel; }
        public String getValidationModel() { return validationModel; }
        public void setValidationModel(String validationModel) { this.validationModel = validationModel; }
        public String getPromptVersion() { return promptVersion; }
        public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    }
}

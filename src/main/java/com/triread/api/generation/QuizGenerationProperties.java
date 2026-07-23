package com.triread.api.generation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.quiz-generation")
public class QuizGenerationProperties {
    private boolean enabled;
    private boolean autoPublish;
    private int maxAttempts = 2;
    private int passScore = 90;
    private int variantsPerDate = 3;
    private int inventoryDays = 3;
    private int maxJobsPerRun = 3;
    private int maxJobsPerDay = 3;
    private int maxApiCallsPerDay = 6;
    private boolean aiValidationEnabled;
    private boolean sourceGroundingEnabled = true;
    private long retryDelayMs = 10_000;
    private String cron = "0 10 3 * * *";
    private String recoveryCron = "0 30 5 * * *";
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
    public int getInventoryDays() { return inventoryDays; }
    public void setInventoryDays(int inventoryDays) { this.inventoryDays = inventoryDays; }
    public int getMaxJobsPerRun() { return maxJobsPerRun; }
    public void setMaxJobsPerRun(int maxJobsPerRun) { this.maxJobsPerRun = maxJobsPerRun; }
    public int getMaxJobsPerDay() { return maxJobsPerDay; }
    public void setMaxJobsPerDay(int maxJobsPerDay) { this.maxJobsPerDay = maxJobsPerDay; }
    public int getMaxApiCallsPerDay() { return maxApiCallsPerDay; }
    public void setMaxApiCallsPerDay(int maxApiCallsPerDay) { this.maxApiCallsPerDay = maxApiCallsPerDay; }
    public boolean isAiValidationEnabled() { return aiValidationEnabled; }
    public void setAiValidationEnabled(boolean aiValidationEnabled) { this.aiValidationEnabled = aiValidationEnabled; }
    public boolean isSourceGroundingEnabled() { return sourceGroundingEnabled; }
    public void setSourceGroundingEnabled(boolean sourceGroundingEnabled) { this.sourceGroundingEnabled = sourceGroundingEnabled; }
    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = retryDelayMs; }
    public String getCron() { return cron; }
    public void setCron(String cron) { this.cron = cron; }
    public String getRecoveryCron() { return recoveryCron; }
    public void setRecoveryCron(String recoveryCron) { this.recoveryCron = recoveryCron; }
    public Gemini getGemini() { return gemini; }

    public static class Gemini {
        private String apiKey = "";
        private String baseUrl = "https://generativelanguage.googleapis.com";
        private String generationModel = "gemini-3.1-flash-lite";
        private String validationModel = "gemini-3.1-flash-lite";
        private String sourceModel = "gemini-2.5-flash-lite";
        private String promptVersion = "v2";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getGenerationModel() { return generationModel; }
        public void setGenerationModel(String generationModel) { this.generationModel = generationModel; }
        public String getValidationModel() { return validationModel; }
        public void setValidationModel(String validationModel) { this.validationModel = validationModel; }
        public String getSourceModel() { return sourceModel; }
        public void setSourceModel(String sourceModel) { this.sourceModel = sourceModel; }
        public String getPromptVersion() { return promptVersion; }
        public void setPromptVersion(String promptVersion) { this.promptVersion = promptVersion; }
    }
}

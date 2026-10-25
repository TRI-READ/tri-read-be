package com.triread.api.operations;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.content-inventory")
public class ContentInventoryProperties {
    @Min(1) private int activeWindowDays = 30;
    @Min(1) private int warningThreshold = 14;
    @Min(1) private int criticalThreshold = 7;
    @Min(1) private int reviewWaitHours = 48;

    public int getActiveWindowDays() { return activeWindowDays; }
    public void setActiveWindowDays(int value) { activeWindowDays = value; }
    public int getWarningThreshold() { return warningThreshold; }
    public void setWarningThreshold(int value) { warningThreshold = value; }
    public int getCriticalThreshold() { return criticalThreshold; }
    public void setCriticalThreshold(int value) { criticalThreshold = value; }
    public int getReviewWaitHours() { return reviewWaitHours; }
    public void setReviewWaitHours(int value) { reviewWaitHours = value; }

    @AssertTrue(message = "critical-threshold must not exceed warning-threshold")
    public boolean isThresholdOrderValid() { return criticalThreshold <= warningThreshold; }
}

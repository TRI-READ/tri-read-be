package com.triread.api.orbit;

import com.triread.api.auth.AuthPrincipal;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orbit")
public class OrbitController {
    private final OrbitService orbitService;

    public OrbitController(OrbitService orbitService) {
        this.orbitService = orbitService;
    }

    @GetMapping
    public OrbitService.OrbitResponse orbit(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(defaultValue = "WEEK") String period,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate anchor
    ) {
        return orbitService.getOrbit(principal.userId(), period, anchor);
    }

    @GetMapping("/streak")
    public OrbitService.StreakResponse streak(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return orbitService.getStreak(principal.userId());
    }
}

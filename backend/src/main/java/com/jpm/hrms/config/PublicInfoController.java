package com.jpm.hrms.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated, non-sensitive app information for the login screen. */
@RestController
@RequestMapping("/api/public")
public class PublicInfoController {

    public record PublicInfo(String appName, boolean demoMode, String timezone) {
    }

    private final AppProperties props;

    public PublicInfoController(AppProperties props) {
        this.props = props;
    }

    @GetMapping("/info")
    public PublicInfo info() {
        return new PublicInfo("JPM HR Portal", props.demoMode(), props.timezone());
    }
}

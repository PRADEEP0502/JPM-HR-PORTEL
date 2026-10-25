package com.jpm.hrms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    /** Business time zone used for "today", monthly buckets and interview display. */
    @Bean
    public ZoneId businessZone(AppProperties props) {
        return ZoneId.of(props.timezone());
    }

    @Bean
    public Clock clock(ZoneId businessZone) {
        return Clock.system(businessZone);
    }
}

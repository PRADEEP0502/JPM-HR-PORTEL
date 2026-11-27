package com.jpm.hrms.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class KeepAwakeTest {
    private final ZoneId ist = ZoneId.of("Asia/Kolkata");

    @Test
    void awakeOnlyInsideOfficeHours() {
        KeepAwake k = new KeepAwake("https://example.onrender.com", "08-21", Clock.systemUTC(), ist);
        assertThat(k.awakeAt(LocalTime.of(7, 59))).isFalse();
        assertThat(k.awakeAt(LocalTime.of(8, 0))).isTrue();
        assertThat(k.awakeAt(LocalTime.of(20, 59))).isTrue();
        assertThat(k.awakeAt(LocalTime.of(21, 0))).isFalse();
        assertThat(k.awakeAt(LocalTime.of(2, 0))).isFalse();
    }

    @Test
    void readsTheHoursSetting() {
        assertThat(KeepAwake.parseHours("00-24")).containsExactly(0, 24);
        assertThat(KeepAwake.parseHours(" 9 - 18 ")).containsExactly(9, 18);
        // unreadable or backwards values fall back to office hours
        assertThat(KeepAwake.parseHours("")).containsExactly(8, 21);
        assertThat(KeepAwake.parseHours("20-8")).containsExactly(8, 21);
        assertThat(KeepAwake.parseHours("abc")).containsExactly(8, 21);
    }
}

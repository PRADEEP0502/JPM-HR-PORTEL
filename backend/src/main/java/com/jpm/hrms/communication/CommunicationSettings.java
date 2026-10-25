package com.jpm.hrms.communication;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/** Company identity shared by every WhatsApp and email template (single row, admin-managed). */
@Entity
@Table(name = "communication_settings")
public class CommunicationSettings {
    public static final long SINGLETON_ID = 1L;
    public static final String DEFAULT_COMPANY = "JPM – Junior Processing Mill";

    @Id
    private Long id = SINGLETON_ID;
    @Column(nullable = false)
    private String companyName = DEFAULT_COMPANY;
    @Column(nullable = false)
    private String defaultVenue = DEFAULT_COMPANY;
    @Column(nullable = false)
    private String hrSignature = "HR Department\n" + DEFAULT_COMPANY;
    /** Country calling code (digits only) assumed for 10-digit mobile numbers. */
    @Column(nullable = false)
    private String defaultCountryCode = "91";
    private String updatedBy;
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getDefaultVenue() { return defaultVenue; }
    public void setDefaultVenue(String defaultVenue) { this.defaultVenue = defaultVenue; }
    public String getHrSignature() { return hrSignature; }
    public void setHrSignature(String hrSignature) { this.hrSignature = hrSignature; }
    public String getDefaultCountryCode() { return defaultCountryCode; }
    public void setDefaultCountryCode(String defaultCountryCode) { this.defaultCountryCode = defaultCountryCode; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}

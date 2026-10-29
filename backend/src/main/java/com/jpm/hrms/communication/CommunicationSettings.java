package com.jpm.hrms.communication;

import com.jpm.hrms.common.SequencedEntity;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


import java.time.Instant;

/** Company identity shared by every WhatsApp and email template (single row, admin-managed). */
@Document("communication_settings")
public class CommunicationSettings implements SequencedEntity {
    public static final long SINGLETON_ID = 1L;
    public static final String DEFAULT_COMPANY = "JPM – Junior Processing Mill";
    public static final String DEFAULT_LOCATION_LINK = "https://maps.app.goo.gl/ZXe9kpP8ytAnNGGa9";

    @Id
    private Long id = SINGLETON_ID;
    private String companyName = DEFAULT_COMPANY;
    private String defaultVenue = DEFAULT_COMPANY;
    private String hrSignature = "HR Department\n" + DEFAULT_COMPANY;
    /** Google Maps link to the company, sent in interview invitations. */
    private String locationLink = DEFAULT_LOCATION_LINK;
    /** Country calling code (digits only) assumed for 10-digit mobile numbers. */
    private String defaultCountryCode = "91";
    private String updatedBy;
    private Instant updatedAt;

    /** Settings documents keep their fixed id; every save refreshes updatedAt. */
    @Override
    public void assignId(Long id) {
        this.id = id;
    }

    @Override
    public void onCreate() {
        updatedAt = Instant.now();
    }

    @Override
    public void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getDefaultVenue() { return defaultVenue; }
    public void setDefaultVenue(String defaultVenue) { this.defaultVenue = defaultVenue; }
    public String getLocationLink() { return locationLink; }
    public void setLocationLink(String locationLink) { this.locationLink = locationLink; }
    public String getHrSignature() { return hrSignature; }
    public void setHrSignature(String hrSignature) { this.hrSignature = hrSignature; }
    public String getDefaultCountryCode() { return defaultCountryCode; }
    public void setDefaultCountryCode(String defaultCountryCode) { this.defaultCountryCode = defaultCountryCode; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public Instant getUpdatedAt() { return updatedAt; }
}

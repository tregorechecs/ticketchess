package com.github.gcolin.membership;

import com.github.gcolin.platform.TimedEntity;

public class MembershipOptionFile extends TimedEntity {

    private Integer id;

    private MembershipOption membershipOption;

    private String originalName;

    private String storedName;

    private String contentType;

    private Long sizeBytes;

    private String uploadedBy;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public MembershipOption getMembershipOption() {
        return membershipOption;
    }

    public void setMembershipOption(MembershipOption membershipOption) {
        this.membershipOption = membershipOption;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getStoredName() {
        return storedName;
    }

    public void setStoredName(String storedName) {
        this.storedName = storedName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}

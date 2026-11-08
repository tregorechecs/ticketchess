package com.github.gcolin.membership;

import com.github.gcolin.club.ClubSeason;
import com.github.gcolin.payment.Payment;
import java.time.LocalDateTime;
import java.util.Locale;
import com.github.gcolin.platform.TimedEntity;

public class Membership extends TimedEntity {

    public static final String DEFAULT_LICENSE_TYPE = "A";

    private Integer id;

    private String user;

    private String nrFfe;

    private String lastname;

    private String firstname;

    private String birthDate;

    private int clubRef;

    private MembershipStatus status;

    private int amountCents;

    private String licenseType;

    private ClubSeason season;

    private Payment payment;

    public Membership() {}

    public Membership(
            String user,
            String nrFfe,
            String lastname,
            String firstname,
            String birthDate,
            int clubRef,
            LocalDateTime create,
            LocalDateTime update,
            MembershipStatus status,
            int amountCents) {
        this.user = user;
        this.nrFfe = nrFfe;
        this.lastname = lastname;
        this.firstname = firstname;
        this.birthDate = birthDate;
        this.clubRef = clubRef;
        this.createdAt = create;
        this.updatedAt = update;
        this.status = status;
        this.amountCents = amountCents;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getNrFfe() {
        return nrFfe;
    }

    public void setNrFfe(String nrFfe) {
        this.nrFfe = nrFfe;
    }

    public String getLastname() {
        return lastname;
    }

    public void setLastname(String lastname) {
        this.lastname = lastname;
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public int getClubRef() {
        return clubRef;
    }

    public void setClubRef(int clubRef) {
        this.clubRef = clubRef;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public void setStatus(MembershipStatus status) {
        this.status = status;
    }

    public int getAmountCents() {
        return amountCents;
    }

    public void setAmountCents(int amountCents) {
        this.amountCents = amountCents;
    }

    public String getLicenseType() {
        return licenseType;
    }

    public void setLicenseType(String licenseType) {
        this.licenseType = licenseType;
    }

    public ClubSeason getSeason() {
        return season;
    }

    public void setSeason(ClubSeason season) {
        this.season = season;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public static String normalizeLicenseType(String licenseType) {
        if (licenseType == null || licenseType.isBlank()) {
            return DEFAULT_LICENSE_TYPE;
        }
        return licenseType.trim().toUpperCase(Locale.ROOT);
    }
}

package com.github.gcolin.player;

import com.github.gcolin.player.IPlayer;
import com.github.gcolin.platform.ModelUtils;
import java.time.LocalDate;

public class CustomPlayer implements IPlayer {

    private Integer id;

    private String firstname;

    private String name;

    private String licence;

    private String birthDate;

    private String creationUser;

    private Boolean gender;

    private String elo;

    // =========================
    // Constructeurs
    // =========================
    public CustomPlayer() {}

    // =========================
    // Getters et Setters
    // =========================
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getFirstname() {
        return firstname;
    }

    public void setFirstname(String firstname) {
        this.firstname = firstname;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLicence() {
        return licence;
    }

    public void setLicence(String licence) {
        this.licence = licence;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    @Override
    public String getBirthDate() {
        return birthDate;
    }

    @Override
    public String getClubRef() {
        return null;
    }

    public String getCreationUser() {
        return creationUser;
    }

    public void setCreationUser(String creationUser) {
        this.creationUser = creationUser;
    }

    public Boolean getGender() {
        return gender;
    }

    public void setGender(Boolean gender) {
        this.gender = gender;
    }

    public String getElo() {
        return elo;
    }

    public void setElo(String elo) {
        this.elo = elo;
    }

    @Override
    public boolean isYoung() {
        return ModelUtils.isYoung(getCategory());
    }

    @Override
    public String getNrffe() {
        return "@" + id;
    }

    public String getCategory() {
        Boolean female = Boolean.FALSE.equals(gender);
        if (birthDate == null || birthDate.length() < 4) {
            return female ? "SenF" : "SenM";
        }
        try {
            int year = Integer.parseInt(birthDate.substring(0, 4));
            return ModelUtils.getCategory(LocalDate.now(), year, Boolean.TRUE.equals(gender));
        } catch (RuntimeException e) {
            return female ? "SenF" : "SenM";
        }
    }

    @Override
    public String getRating() {
        return elo;
    }

    @Override
    public String getBlitzRating() {
        return elo;
    }

    @Override
    public String getRapidRating() {
        return elo;
    }

    @Override
    public boolean isEditable() {
        return true;
    }

    @Override
    public String getFideTitre() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getFideCode() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getAffType() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getFederation() {
        return "FRA";
    }

    @Override
    public String getClub() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getFide() {
        // TODO Auto-generated method stub
        return "0";
    }
}

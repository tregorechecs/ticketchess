package com.github.gcolin.membership;

import com.github.gcolin.platform.TimedEntity;

public class MembershipOptionSubscription extends TimedEntity {

    private Integer id;

    private String nrFfe;

    private Membership membership;

    private MembershipOption membershipOption;

    public MembershipOptionSubscription() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getNrFfe() {
        return nrFfe;
    }

    public void setNrFfe(String nrFfe) {
        this.nrFfe = nrFfe;
    }


    public Membership getMembership() {
        return membership;
    }

    public void setMembership(Membership membership) {
        this.membership = membership;
    }

    public MembershipOption getMembershipOption() {
        return membershipOption;
    }

    public void setMembershipOption(MembershipOption membershipOption) {
        this.membershipOption = membershipOption;
    }

}

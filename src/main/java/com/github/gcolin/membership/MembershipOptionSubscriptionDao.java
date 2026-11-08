package com.github.gcolin.membership;

import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MembershipOptionSubscriptionDao extends AbstractDao<MembershipOptionSubscription> {

    public MembershipOptionSubscriptionDao() {
        super(MembershipOptionSubscription.class, "membership_option_subscription");
    }

    @Override
    protected List<MembershipOptionSubscription> select(String sql, Object... params) {
        List<MembershipOptionSubscription> list = selectWith(
                (sub, rs) -> {
                    Integer membershipId = intOrNull(rs, "membership_id");
                    if (membershipId != null) {
                        Membership membership = new Membership();
                        membership.setId(membershipId);
                        sub.setMembership(membership);
                    }
                    Integer optionId = intOrNull(rs, "membership_option_id");
                    if (optionId != null) {
                        MembershipOption option = new MembershipOption();
                        option.setId(optionId);
                        sub.setMembershipOption(option);
                    }
                },
                sql,
                params);
        if (list.isEmpty()) {
            return list;
        }
        Set<Integer> membershipIds = new LinkedHashSet<>();
        Set<Integer> optionIds = new LinkedHashSet<>();
        for (MembershipOptionSubscription sub : list) {
            if (sub.getMembership() != null) {
                membershipIds.add(sub.getMembership().getId());
            }
            if (sub.getMembershipOption() != null) {
                optionIds.add(sub.getMembershipOption().getId());
            }
        }
        Map<Integer, Membership> memberships = new MembershipDao().findByIds(membershipIds);
        Map<Integer, MembershipOption> options = new MembershipOptionDao().findByIds(optionIds);
        for (MembershipOptionSubscription sub : list) {
            if (sub.getMembership() != null) {
                sub.setMembership(memberships.get(sub.getMembership().getId()));
            }
            if (sub.getMembershipOption() != null) {
                sub.setMembershipOption(options.get(sub.getMembershipOption().getId()));
            }
        }
        return list;
    }

    public List<MembershipOptionSubscription> findByMembershipIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return select(
                "SELECT * FROM membership_option_subscription WHERE membership_id IN (" + placeholders(ids) + ")",
                ids.toArray());
    }

    public List<MembershipOptionSubscription> findByOptionId(Integer optionId) {
        if (optionId == null) {
            return Collections.emptyList();
        }
        return select("SELECT * FROM membership_option_subscription WHERE membership_option_id = ?", optionId);
    }

    @Override
    public void persist(MembershipOptionSubscription sub) {
        if (sub.getId() == null) {
            touchCreated(sub);
            int id = Db.insertReturningInt(
                    "INSERT INTO membership_option_subscription (created_at, updated_at, nr_ffe, membership_id,"
                            + " membership_option_id) VALUES (?, ?, ?, ?, ?)",
                    sub.getCreatedAt(),
                    sub.getUpdatedAt(),
                    sub.getNrFfe(),
                    Db.fkId(sub.getMembership()),
                    Db.fkId(sub.getMembershipOption()));
            sub.setId(id);
        } else {
            touchUpdated(sub);
            Db.update(
                    "UPDATE membership_option_subscription SET updated_at = ?, nr_ffe = ?, membership_id = ?,"
                            + " membership_option_id = ? WHERE id = ?",
                    sub.getUpdatedAt(),
                    sub.getNrFfe(),
                    Db.fkId(sub.getMembership()),
                    Db.fkId(sub.getMembershipOption()),
                    sub.getId());
        }
    }

    @Override
    public MembershipOptionSubscription merge(MembershipOptionSubscription sub) {
        persist(sub);
        return sub;
    }
}

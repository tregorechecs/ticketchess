package com.github.gcolin.registration;

import com.github.gcolin.payment.Payment;
import com.github.gcolin.payment.PaymentDao;
import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlayerSubscriptionOptionDao extends AbstractDao<PlayerSubscriptionOption> {

    public PlayerSubscriptionOptionDao() {
        super(PlayerSubscriptionOption.class, "player_subscription_option");
    }

    @Override
    protected List<PlayerSubscriptionOption> select(String sql, Object... params) {
        List<PlayerSubscriptionOption> list = selectWith(
                (option, rs) -> {
                    Integer subId = intOrNull(rs, "player_subscription_id");
                    if (subId != null) {
                        PlayerSubscription sub = new PlayerSubscription();
                        sub.setId(subId);
                        option.setPlayerSubscription(sub);
                    }
                    Long paymentId = longOrNull(rs, "payment_id");
                    if (paymentId != null) {
                        Payment payment = new Payment();
                        payment.setId(paymentId);
                        option.setPayment(payment);
                    }
                },
                sql,
                params);
        hydrate(list);
        return list;
    }

    private void hydrate(List<PlayerSubscriptionOption> list) {
        if (list.isEmpty()) {
            return;
        }
        Set<Integer> subIds = new LinkedHashSet<>();
        Set<Long> paymentIds = new LinkedHashSet<>();
        for (PlayerSubscriptionOption option : list) {
            if (option.getPlayerSubscription() != null) {
                subIds.add(option.getPlayerSubscription().getId());
            }
            if (option.getPayment() != null) {
                paymentIds.add(option.getPayment().getId());
            }
        }
        Map<Integer, PlayerSubscription> subs = new PlayerSubscriptionDao().findByIds(subIds);
        Map<Long, Payment> payments = new PaymentDao().findByLongIds(paymentIds);
        for (PlayerSubscriptionOption option : list) {
            if (option.getPlayerSubscription() != null) {
                option.setPlayerSubscription(subs.get(option.getPlayerSubscription().getId()));
            }
            if (option.getPayment() != null) {
                option.setPayment(payments.get(option.getPayment().getId()));
            }
        }
    }

    public List<PlayerSubscriptionOption> findByPlayerSubscription(Integer subId) {
        return select("SELECT * FROM player_subscription_option WHERE player_subscription_id = ? ORDER BY id", subId);
    }

    public List<PlayerSubscriptionOption> findByPaymentId(Integer paymentId) {
        return select(
                "SELECT * FROM player_subscription_option WHERE payment_id = ?",
                paymentId == null ? null : paymentId.longValue());
    }

    public List<PlayerSubscriptionOption> findByEvent(Integer eventId) {
        return select(
                "SELECT o.* FROM player_subscription_option o"
                        + " JOIN player_subscription ps ON ps.id = o.player_subscription_id"
                        + " WHERE ps.event_id = ? ORDER BY ps.id, o.id",
                eventId);
    }

    public List<PlayerSubscriptionOption> findNotPaidByCreationUser(String email) {
        return select(
                "SELECT o.* FROM player_subscription_option o"
                        + " JOIN player_subscription ps ON ps.id = o.player_subscription_id"
                        + " WHERE ps.creation_user = ? AND o.status = ?",
                email,
                Db.enumOrdinal(PlayerSubscriptionOptionStatus.NOT_PAID));
    }

    public PlayerSubscriptionOption findWithSubscription(Integer id) {
        return find(id);
    }

    public void removeByPlayerSubscription(Integer subId) {
        List<PlayerSubscriptionOption> options = findByPlayerSubscription(subId);
        for (PlayerSubscriptionOption option : options) {
            remove(option);
        }
    }

    @Override
    public void persist(PlayerSubscriptionOption option) {
        if (option.getId() == null) {
            touchCreated(option);
            int id = Db.insertReturningInt(
                    "INSERT INTO player_subscription_option (created_at, updated_at, player_subscription_id,"
                            + " amountcents, description, payment_id, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    option.getCreatedAt(),
                    option.getUpdatedAt(),
                    Db.fkId(option.getPlayerSubscription()),
                    option.getAmountCents(),
                    option.getDescription(),
                    Db.fkLongId(option.getPayment()),
                    Db.enumOrdinal(option.getStatus()));
            option.setId(id);
        } else {
            touchUpdated(option);
            Db.update(
                    "UPDATE player_subscription_option SET updated_at = ?, player_subscription_id = ?,"
                            + " amountcents = ?, description = ?, payment_id = ?, status = ? WHERE id = ?",
                    option.getUpdatedAt(),
                    Db.fkId(option.getPlayerSubscription()),
                    option.getAmountCents(),
                    option.getDescription(),
                    Db.fkLongId(option.getPayment()),
                    Db.enumOrdinal(option.getStatus()),
                    option.getId());
        }
    }

    @Override
    public PlayerSubscriptionOption merge(PlayerSubscriptionOption option) {
        persist(option);
        return option;
    }
}

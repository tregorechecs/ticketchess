package com.github.gcolin.membership;

import com.github.gcolin.club.ClubSeason;
import com.github.gcolin.club.ClubSeasonDao;
import com.github.gcolin.club.SeasonScope;
import com.github.gcolin.payment.Payment;
import com.github.gcolin.payment.PaymentDao;
import com.github.gcolin.payment.PaymentStatus;
import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MembershipDao extends AbstractDao<Membership> {

    /** Inscriptions ouvertes avant le début officiel de la saison (été). */
    private static final int PRE_SEASON_MONTHS = 5;

    private static final String COLUMNS = "m.id, m.created_at, m.updated_at, m.creation_user AS \"user\", m.nr_ffe,"
            + " m.lastname, m.firstname, m.birthdate, m.club_ref, m.status, m.amountcents, m.license_type,"
            + " m.season_id, m.payment_id";

    private static final String FROM = "SELECT " + COLUMNS + " FROM membership m";

    public MembershipDao() {
        super(Membership.class, "membership");
    }

    @Override
    protected List<Membership> select(String sql, Object... params) {
        List<Membership> list = selectWith(
                (membership, rs) -> {
                    Integer seasonId = intOrNull(rs, "season_id");
                    if (seasonId != null) {
                        ClubSeason season = new ClubSeason();
                        season.setId(seasonId);
                        membership.setSeason(season);
                    }
                    Long paymentId = longOrNull(rs, "payment_id");
                    if (paymentId != null) {
                        Payment payment = new Payment();
                        payment.setId(paymentId);
                        membership.setPayment(payment);
                    }
                },
                sql,
                params);
        hydrate(list);
        return list;
    }

    private void hydrate(List<Membership> list) {
        if (list.isEmpty()) {
            return;
        }
        Set<Integer> seasonIds = new LinkedHashSet<>();
        Set<Long> paymentIds = new LinkedHashSet<>();
        for (Membership membership : list) {
            if (membership.getSeason() != null) {
                seasonIds.add(membership.getSeason().getId());
            }
            if (membership.getPayment() != null) {
                paymentIds.add(membership.getPayment().getId());
            }
        }
        Map<Integer, ClubSeason> seasons = new ClubSeasonDao().findByIds(seasonIds);
        Map<Long, Payment> payments = new PaymentDao().findByLongIds(paymentIds);
        for (Membership membership : list) {
            if (membership.getSeason() != null) {
                membership.setSeason(seasons.get(membership.getSeason().getId()));
            }
            if (membership.getPayment() != null) {
                membership.setPayment(payments.get(membership.getPayment().getId()));
            }
        }
    }

    /** SQL condition (without leading keyword) for the season scope, or {@code null} when not filtered. */
    private static String seasonCondition(SeasonScope scope, List<Object> args) {
        String created = "COALESCE(m.created_at, m.updated_at)";
        String dateRange = "((" + created + " >= ? AND " + created + " <= ?)"
                + " OR (" + created + " >= ? AND " + created + " < ?))";
        if (scope.isSeasonIdFiltered() && scope.isFiltered()) {
            args.add(scope.getSeasonId());
            addDateRange(scope, args);
            return "(m.season_id = ? OR (m.season_id IS NULL AND " + dateRange + "))";
        } else if (scope.isSeasonIdFiltered()) {
            args.add(scope.getSeasonId());
            return "m.season_id = ?";
        } else if (scope.isFiltered()) {
            addDateRange(scope, args);
            return dateRange;
        }
        return null;
    }

    private static void addDateRange(SeasonScope scope, List<Object> args) {
        args.add(scope.getStart());
        args.add(scope.getEnd());
        args.add(scope.getStart().minusMonths(PRE_SEASON_MONTHS));
        args.add(scope.getStart());
    }

    public List<Membership> all(SeasonScope scope) {
        StringBuilder sql = new StringBuilder(FROM);
        List<Object> args = Db.args();
        String condition = seasonCondition(scope, args);
        if (condition != null) {
            sql.append(" WHERE ").append(condition);
        }
        sql.append(" ORDER BY m.id DESC");
        return select(sql.toString(), args.toArray());
    }

    public List<Membership> findByUser(String user) {
        return findByUser(user, SeasonScope.all());
    }

    public List<Membership> findByUser(String user, SeasonScope scope) {
        StringBuilder sql = new StringBuilder(FROM).append(" WHERE m.creation_user = ?");
        List<Object> args = Db.args(user);
        String condition = seasonCondition(scope, args);
        if (condition != null) {
            sql.append(" AND ").append(condition);
        }
        sql.append(" ORDER BY m.id DESC");
        return select(sql.toString(), args.toArray());
    }

    public List<Membership> findApprovedUnpaidByUser(String user, SeasonScope scope) {
        StringBuilder sql = new StringBuilder(FROM)
                .append(" WHERE m.creation_user = ? AND m.status = ? AND m.amountcents > 0")
                .append(" AND NOT EXISTS (SELECT 1 FROM payment p WHERE p.id = m.payment_id AND p.status = ?)");
        List<Object> args = Db.args(user, MembershipStatus.APPROVED.name(), PaymentStatus.PAID.name());
        String condition = seasonCondition(scope, args);
        if (condition != null) {
            sql.append(" AND ").append(condition);
        }
        sql.append(" ORDER BY m.id ASC");
        return select(sql.toString(), args.toArray());
    }

    public List<Membership> findByPaymentId(int paymentId) {
        return select(FROM + " WHERE m.payment_id = ? ORDER BY m.id", (long) paymentId);
    }

    @Override
    public void persist(Membership membership) {
        if (membership.getId() == null) {
            touchCreated(membership);
            int id = Db.insertReturningInt(
                    "INSERT INTO membership (created_at, updated_at, creation_user, nr_ffe, lastname, firstname,"
                            + " birthdate, club_ref, status, amountcents, license_type, season_id, payment_id)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    membership.getCreatedAt(),
                    membership.getUpdatedAt(),
                    membership.getUser(),
                    membership.getNrFfe(),
                    membership.getLastname(),
                    membership.getFirstname(),
                    membership.getBirthDate(),
                    membership.getClubRef(),
                    Db.enumName(membership.getStatus()),
                    membership.getAmountCents(),
                    membership.getLicenseType(),
                    Db.fkId(membership.getSeason()),
                    Db.fkLongId(membership.getPayment()));
            membership.setId(id);
        } else {
            touchUpdated(membership);
            Db.update(
                    "UPDATE membership SET updated_at = ?, creation_user = ?, nr_ffe = ?, lastname = ?,"
                            + " firstname = ?, birthdate = ?, club_ref = ?, status = ?, amountcents = ?,"
                            + " license_type = ?, season_id = ?, payment_id = ? WHERE id = ?",
                    membership.getUpdatedAt(),
                    membership.getUser(),
                    membership.getNrFfe(),
                    membership.getLastname(),
                    membership.getFirstname(),
                    membership.getBirthDate(),
                    membership.getClubRef(),
                    Db.enumName(membership.getStatus()),
                    membership.getAmountCents(),
                    membership.getLicenseType(),
                    Db.fkId(membership.getSeason()),
                    Db.fkLongId(membership.getPayment()),
                    membership.getId());
        }
    }

    @Override
    public Membership merge(Membership membership) {
        persist(membership);
        return membership;
    }
}

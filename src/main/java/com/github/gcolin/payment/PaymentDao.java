package com.github.gcolin.payment;

import com.github.gcolin.club.SeasonScope;
import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import com.github.gcolin.platform.PagedList;
import java.util.List;
import org.apache.commons.dbutils.handlers.ColumnListHandler;

public class PaymentDao extends AbstractDao<Payment> {

    private static final String SEARCH_CONDITION = "(LOWER(p.useremail) LIKE ?"
            + " OR EXISTS (SELECT 1 FROM player_subscription s WHERE s.payment_id = p.id AND LOWER(s.nr_ffe) LIKE ?)"
            + " OR EXISTS (SELECT 1 FROM membership m WHERE m.payment_id = p.id AND (LOWER(m.nr_ffe) LIKE ?"
            + " OR LOWER(m.lastname) LIKE ? OR LOWER(m.firstname) LIKE ?)))";

    public PaymentDao() {
        super(Payment.class, "payment");
    }

    @Override
    public PagedList<Payment> page(int start, int pageSize) {
        return page(start, pageSize, SeasonScope.all());
    }

    public PagedList<Payment> page(int start, int pageSize, SeasonScope scope) {
        List<Object> args = Db.args();
        String where = seasonClause(scope, args, " WHERE ");
        long total = Db.scalarLongOrZero("SELECT COUNT(*) FROM payment p" + where, args.toArray());

        args.add(pageSize);
        args.add(start);
        List<Payment> list = select("SELECT p.* FROM payment p" + where + " ORDER BY p.id DESC LIMIT ? OFFSET ?", args.toArray());
        return new PagedList<>(list, start, total);
    }

    public Payment findPendingByUser(String email) {
        return selectOne(
                "SELECT * FROM payment WHERE useremail = ? AND status = ? ORDER BY id LIMIT 1",
                email,
                PaymentStatus.PENDING);
    }

    public Payment findPendingEventPayment(String email) {
        return selectOne(
                "SELECT p.* FROM payment p WHERE p.useremail = ? AND p.status = ?"
                        + " AND EXISTS (SELECT 1 FROM player_subscription s WHERE s.payment_id = p.id)"
                        + " ORDER BY p.id LIMIT 1",
                email,
                PaymentStatus.PENDING);
    }

    public Payment findPendingMembershipPayment(String email) {
        return selectOne(
                "SELECT p.* FROM payment p WHERE p.useremail = ? AND p.status = ?"
                        + " AND EXISTS (SELECT 1 FROM membership m WHERE m.payment_id = p.id)"
                        + " ORDER BY p.id LIMIT 1",
                email,
                PaymentStatus.PENDING);
    }

    public List<Payment> findAllPaidNotFreeByUser(String email) {
        return select(
                "SELECT * FROM payment WHERE useremail = ? AND status = ? AND type <> ? ORDER BY id DESC",
                email,
                PaymentStatus.PAID,
                PaymentType.FREE);
    }

    public List<Payment> findAllPaidWithMembershipsByUser(String email) {
        return select(
                "SELECT p.* FROM payment p WHERE p.useremail = ? AND p.status = ? AND p.type <> ?"
                        + " AND EXISTS (SELECT 1 FROM membership m WHERE m.payment_id = p.id)"
                        + " ORDER BY p.id DESC",
                email,
                PaymentStatus.PAID,
                PaymentType.FREE);
    }

    public List<Payment> findAllPaidDonationsByUser(String email) {
        return select(
                "SELECT * FROM payment WHERE useremail = ? AND status = ? AND donation = TRUE ORDER BY id DESC",
                email,
                PaymentStatus.PAID);
    }

    public Payment findBySessionId(String sessionId) {
        return selectOne("SELECT * FROM payment WHERE stripesessionid = ? ORDER BY id LIMIT 1 FOR UPDATE", sessionId);
    }

    public long sumAllPayments() {
        return sumAllPayments(SeasonScope.all());
    }

    public long sumAllPayments(SeasonScope scope) {
        List<Object> args = Db.args(PaymentStatus.PAID);
        String and = seasonClause(scope, args, " AND ");
        return Db.scalarLongOrZero(
                "SELECT COALESCE(SUM(p.amountcents), 0) FROM payment p WHERE p.status = ?" + and,
                args.toArray());
    }

    public List<Payment> all(SeasonScope scope) {
        List<Object> args = Db.args();
        String where = seasonClause(scope, args, " WHERE ");
        return select("SELECT p.* FROM payment p" + where + " ORDER BY p.id DESC", args.toArray());
    }

    public List<Payment> findPaid(SeasonScope scope) {
        List<Object> args = Db.args(PaymentStatus.PAID);
        String and = seasonClause(scope, args, " AND ");
        return select(
                "SELECT p.* FROM payment p WHERE p.status = ?"
                        + and
                        + " ORDER BY COALESCE(p.updated_at, p.created_at), p.id",
                args.toArray());
    }

    public List<String> findDistinctUserEmails() {
        List<String> emails = Db.query(
                "SELECT DISTINCT useremail FROM payment WHERE useremail IS NOT NULL AND useremail <> ''"
                        + " ORDER BY useremail",
                new ColumnListHandler<String>());
        return emails == null ? List.of() : emails;
    }

    public PagedList<Payment> pageSearch(String search, int start, int pageSize) {
        return pageSearch(search, start, pageSize, SeasonScope.all());
    }

    public PagedList<Payment> pageSearch(String search, int start, int pageSize, SeasonScope scope) {
        String term = "%" + search.toLowerCase() + "%";
        List<Object> args = Db.args(term, term, term, term, term);
        String and = seasonClause(scope, args, " AND ");
        long total = Db.scalarLongOrZero(
                "SELECT COUNT(*) FROM payment p WHERE " + SEARCH_CONDITION + and, args.toArray());

        args.add(pageSize);
        args.add(start);
        List<Payment> list = select(
                "SELECT p.* FROM payment p WHERE " + SEARCH_CONDITION + and + " ORDER BY p.id DESC LIMIT ? OFFSET ?",
                args.toArray());
        return new PagedList<>(list, start, total);
    }

    private static String seasonClause(SeasonScope scope, List<Object> args, String keyword) {
        if (scope == null || !scope.isFiltered()) {
            return "";
        }
        args.add(scope.getStart());
        args.add(scope.getEnd());
        return keyword + "p.created_at >= ? AND p.created_at <= ?";
    }

    @Override
    public void persist(Payment payment) {
        if (payment.getId() == null) {
            touchCreated(payment);
            long id = Db.insertReturningLong(
                    "INSERT INTO payment (created_at, updated_at, stripesessionid, stripeintent, useremail,"
                            + " payername, payeraddress, donation, status, type, amountcents)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    payment.getCreatedAt(),
                    payment.getUpdatedAt(),
                    payment.getStripeSessionId(),
                    payment.getStripeIntent(),
                    payment.getUserEmail(),
                    payment.getPayerName(),
                    payment.getPayerAddress(),
                    payment.isDonation(),
                    Db.enumName(payment.getStatus()),
                    Db.enumName(payment.getType()),
                    payment.getAmountCents());
            payment.setId(id);
        } else {
            touchUpdated(payment);
            Db.update(
                    "UPDATE payment SET updated_at = ?, stripesessionid = ?, stripeintent = ?, useremail = ?,"
                            + " payername = ?, payeraddress = ?, donation = ?, status = ?, type = ?,"
                            + " amountcents = ? WHERE id = ?",
                    payment.getUpdatedAt(),
                    payment.getStripeSessionId(),
                    payment.getStripeIntent(),
                    payment.getUserEmail(),
                    payment.getPayerName(),
                    payment.getPayerAddress(),
                    payment.isDonation(),
                    Db.enumName(payment.getStatus()),
                    Db.enumName(payment.getType()),
                    payment.getAmountCents(),
                    payment.getId());
        }
    }

    @Override
    public Payment merge(Payment payment) {
        persist(payment);
        return payment;
    }
}

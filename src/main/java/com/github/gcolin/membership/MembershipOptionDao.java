package com.github.gcolin.membership;

import com.github.gcolin.club.ClubSeason;
import com.github.gcolin.club.ClubSeasonDao;
import com.github.gcolin.club.SeasonScope;
import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MembershipOptionDao extends AbstractDao<MembershipOption> {

    public MembershipOptionDao() {
        super(MembershipOption.class, "membership_option");
    }

    @Override
    protected List<MembershipOption> select(String sql, Object... params) {
        List<MembershipOption> list = selectWith(
                (option, rs) -> {
                    Integer licenseId = intOrNull(rs, "license_id");
                    if (licenseId != null) {
                        License license = new License();
                        license.setId(licenseId);
                        option.setLicense(license);
                    }
                    Integer seasonId = intOrNull(rs, "season_id");
                    if (seasonId != null) {
                        ClubSeason season = new ClubSeason();
                        season.setId(seasonId);
                        option.setSeason(season);
                    }
                },
                sql,
                params);
        if (list.isEmpty()) {
            return list;
        }
        Set<Integer> licenseIds = new LinkedHashSet<>();
        Set<Integer> seasonIds = new LinkedHashSet<>();
        for (MembershipOption option : list) {
            if (option.getLicense() != null) {
                licenseIds.add(option.getLicense().getId());
            }
            if (option.getSeason() != null) {
                seasonIds.add(option.getSeason().getId());
            }
        }
        Map<Integer, License> licenses = new LicenseDao().findByIds(licenseIds);
        Map<Integer, ClubSeason> seasons = new ClubSeasonDao().findByIds(seasonIds);
        for (MembershipOption option : list) {
            if (option.getLicense() != null) {
                option.setLicense(licenses.get(option.getLicense().getId()));
            }
            if (option.getSeason() != null) {
                option.setSeason(seasons.get(option.getSeason().getId()));
            }
        }
        return list;
    }

    public List<MembershipOption> all(SeasonScope scope) {
        StringBuilder sql = new StringBuilder("SELECT * FROM membership_option o");
        List<Object> args = Db.args();
        if (scope.isSeasonIdFiltered() && scope.isFiltered()) {
            sql.append(" WHERE (o.season_id = ?"
                    + " OR (o.season_id IS NULL AND o.created_at >= ? AND o.created_at <= ?))");
            args.add(scope.getSeasonId());
            args.add(scope.getStart());
            args.add(scope.getEnd());
        } else if (scope.isSeasonIdFiltered()) {
            sql.append(" WHERE o.season_id = ?");
            args.add(scope.getSeasonId());
        } else if (scope.isFiltered()) {
            sql.append(" WHERE o.created_at >= ? AND o.created_at <= ?");
            args.add(scope.getStart());
            args.add(scope.getEnd());
        }
        sql.append(" ORDER BY o.id ASC");
        return select(sql.toString(), args.toArray());
    }

    @Override
    public void persist(MembershipOption option) {
        if (option.getId() == null) {
            touchCreated(option);
            int id = Db.insertReturningInt(
                    "INSERT INTO membership_option (created_at, updated_at, option_type, option_value, amount_cents,"
                            + " access_rule, license_id, season_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    option.getCreatedAt(),
                    option.getUpdatedAt(),
                    Db.enumName(option.getOptionType()),
                    option.getOptionValue(),
                    option.getAmountCents(),
                    Db.enumName(option.getAccessRule()),
                    Db.fkId(option.getLicense()),
                    Db.fkId(option.getSeason()));
            option.setId(id);
        } else {
            touchUpdated(option);
            Db.update(
                    "UPDATE membership_option SET updated_at = ?, option_type = ?, option_value = ?,"
                            + " amount_cents = ?, access_rule = ?, license_id = ?, season_id = ? WHERE id = ?",
                    option.getUpdatedAt(),
                    Db.enumName(option.getOptionType()),
                    option.getOptionValue(),
                    option.getAmountCents(),
                    Db.enumName(option.getAccessRule()),
                    Db.fkId(option.getLicense()),
                    Db.fkId(option.getSeason()),
                    option.getId());
        }
    }

    @Override
    public MembershipOption merge(MembershipOption option) {
        persist(option);
        return option;
    }
}

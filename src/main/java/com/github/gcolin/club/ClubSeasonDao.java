package com.github.gcolin.club;

import com.github.gcolin.organisation.OrgEntityListener;
import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import java.util.List;

public class ClubSeasonDao extends AbstractDao<ClubSeason> {

    public ClubSeasonDao() {
        super(ClubSeason.class, "club_season");
    }

    @Override
    protected boolean orgScoped() {
        return true;
    }

    public List<ClubSeason> allOrderByStartDateDesc() {
        return scoped(" ORDER BY start_date DESC", "");
    }

    public ClubSeason findCurrent() {
        List<ClubSeason> list = scoped(" ORDER BY start_date DESC LIMIT 1", " is_current = TRUE");
        return list.isEmpty() ? null : list.get(0);
    }

    private List<ClubSeason> scoped(String tail, String condition) {
        StringBuilder sql = new StringBuilder("SELECT * FROM club_season");
        List<Object> args = Db.args();
        String filter = orgFilterSql(null);
        boolean where = false;
        if (!condition.isEmpty()) {
            sql.append(" WHERE").append(condition);
            where = true;
        }
        if (!filter.isEmpty()) {
            sql.append(where ? " AND " : " WHERE ").append(filter);
            addOrgFilterArgs(args);
        }
        sql.append(tail);
        return select(sql.toString(), args.toArray());
    }

    public void save(ClubSeason season, boolean markCurrent) {
        if (season.getId() == null) {
            persist(season);
        }
        if (markCurrent) {
            Db.update(
                    "UPDATE club_season SET is_current = FALSE WHERE id <> ? AND org_id = ?",
                    season.getId(),
                    season.getOrgId());
            season.setCurrent(true);
        } else {
            season.setCurrent(false);
        }
        merge(season);
    }

    @Override
    public void persist(ClubSeason season) {
        if (season.getId() == null) {
            OrgEntityListener.assignOrg(season);
            touchCreated(season);
            int id = Db.insertReturningInt(
                    "INSERT INTO club_season (created_at, updated_at, org_id, name, start_date, end_date, is_current)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                    season.getCreatedAt(),
                    season.getUpdatedAt(),
                    season.getOrgId(),
                    season.getName(),
                    season.getStartDate(),
                    season.getEndDate(),
                    season.isCurrent());
            season.setId(id);
        } else {
            touchUpdated(season);
            Db.update(
                    "UPDATE club_season SET updated_at = ?, org_id = ?, name = ?, start_date = ?, end_date = ?,"
                            + " is_current = ? WHERE id = ?",
                    season.getUpdatedAt(),
                    season.getOrgId(),
                    season.getName(),
                    season.getStartDate(),
                    season.getEndDate(),
                    season.isCurrent(),
                    season.getId());
        }
    }

    @Override
    public ClubSeason merge(ClubSeason season) {
        persist(season);
        return season;
    }
}
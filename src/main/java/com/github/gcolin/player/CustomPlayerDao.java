package com.github.gcolin.player;

import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.platform.Db;
import com.github.gcolin.platform.PagedList;
import java.util.List;

public class CustomPlayerDao extends AbstractDao<CustomPlayer> {

    public CustomPlayerDao() {
        super(CustomPlayer.class, "custom_player");
    }

    public List<CustomPlayer> findByCreationUser(String email) {
        return select("SELECT * FROM custom_player WHERE creation_user = ?", email);
    }

    public List<CustomPlayer> findWithoutSubscription() {
        return select(
                "SELECT e.* FROM custom_player e WHERE NOT EXISTS"
                        + " (SELECT 1 FROM player_subscription s WHERE s.nr_ffe = CONCAT('@', e.id))");
    }

    public PagedList<CustomPlayer> pageSorted(int start, int pageSize) {
        List<CustomPlayer> list =
                select("SELECT * FROM custom_player ORDER BY name, firstname LIMIT ? OFFSET ?", pageSize, start);
        long total = Db.scalarLongOrZero("SELECT COUNT(*) FROM custom_player");
        return new PagedList<>(list, start, total);
    }

    @Override
    public void persist(CustomPlayer player) {
        if (player.getId() == null) {
            int id = Db.insertReturningInt(
                    "INSERT INTO custom_player (firstname, name, licence, birthdate, creation_user, gender, elo)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                    player.getFirstname(),
                    player.getName(),
                    player.getLicence(),
                    player.getBirthDate(),
                    player.getCreationUser(),
                    player.getGender(),
                    player.getElo());
            player.setId(id);
        } else {
            Db.update(
                    "UPDATE custom_player SET firstname = ?, name = ?, licence = ?, birthdate = ?,"
                            + " creation_user = ?, gender = ?, elo = ? WHERE id = ?",
                    player.getFirstname(),
                    player.getName(),
                    player.getLicence(),
                    player.getBirthDate(),
                    player.getCreationUser(),
                    player.getGender(),
                    player.getElo(),
                    player.getId());
        }
    }

    @Override
    public CustomPlayer merge(CustomPlayer player) {
        persist(player);
        return player;
    }
}

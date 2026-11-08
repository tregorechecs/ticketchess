package com.github.gcolin.event;

import com.github.gcolin.club.SeasonScope;
import com.github.gcolin.event.Event;
import com.github.gcolin.event.EventCollection;
import com.github.gcolin.event.EventCollectionOptionType;
import com.github.gcolin.event.EventGroup;
import com.github.gcolin.event.EventOption;
import com.github.gcolin.event.EventOptionType;
import com.github.gcolin.organisation.OrgEntityListener;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.player.DisplayPlayer;
import com.github.gcolin.event.EventStatus;
import com.github.gcolin.event.EventType;
import com.github.gcolin.player.IPlayer;
import com.github.gcolin.player.Find;
import com.github.gcolin.platform.ServiceUtils;
import com.github.gcolin.event.EventCache;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.github.gcolin.platform.AbstractDao;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import com.github.gcolin.registration.PlayerSubscriptionStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import com.github.gcolin.platform.Db;
import org.apache.commons.dbutils.ResultSetHandler;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class EventDao extends AbstractDao<Event> {

    /** List/detail columns without the markdown {@code description} TEXT. */
    static final String LIST_COLUMNS =
            "id, org_id, name, start_date, end_date, pricecents, youngpricecents, status_id, event_type,"
                    + " event_group_id, big_event_id";

    static final String COLUMNS_WITH_DESCRIPTION = LIST_COLUMNS + ", description";

    private Find finder;
    private Supplier<EventGroupDao> eventGroupService;
    private Supplier<EventOptionDao> eventOptionDao;
    private Supplier<EventCollectionOptionDao> eventCollectionOptionDao;
    private Supplier<PlayerSubscriptionDao> playerSubscriptionDao;

    private static Logger logger = LoggerFactory.getLogger(EventDao.class);

    public EventDao() {
        super(Event.class, "event");
    }

    @Override
    protected boolean orgScoped() {
        return true;
    }

    // ------------------------------------------------------------------
    // Mapping (Event has legacy price/cents setters, so it is mapped by hand)
    // ------------------------------------------------------------------

    static Event mapRow(ResultSet rs) throws SQLException {
        Event event = new Event();
        event.setId(rs.getInt("id"));
        event.setOrgId(intOrNull(rs, "org_id"));
        event.setName(rs.getString("name"));
        event.setStartDate(timestampOrNull(rs, "start_date"));
        event.setEndDate(timestampOrNull(rs, "end_date"));
        Long priceCents = longOrNull(rs, "pricecents");
        if (priceCents != null) {
            event.setPriceCents(priceCents);
        }
        Long youngPriceCents = longOrNull(rs, "youngpricecents");
        if (youngPriceCents != null) {
            event.setYoungPriceCents(youngPriceCents);
        }
        int statusOrdinal = rs.getInt("status_id");
        event.setStatus(EventStatus.values()[statusOrdinal]);
        int typeOrdinal = rs.getInt("event_type");
        event.setEventType(EventType.values()[typeOrdinal]);
        Integer groupId = intOrNull(rs, "event_group_id");
        if (groupId != null) {
            EventGroup group = new EventGroup();
            group.setId(groupId);
            event.setEventGroup(group);
        }
        Integer collectionId = intOrNull(rs, "big_event_id");
        if (collectionId != null) {
            EventCollection collection = new EventCollection();
            collection.setId(collectionId);
            event.setEventCollection(collection);
        }
        if (hasColumn(rs, "description")) {
            event.setDescription(rs.getString("description"));
        }
        return event;
    }

    private static boolean hasColumn(ResultSet rs, String label) throws SQLException {
        var meta = rs.getMetaData();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            if (label.equalsIgnoreCase(meta.getColumnLabel(i))) {
                return true;
            }
        }
        return false;
    }

    static final ResultSetHandler<List<Event>> LIST_HANDLER = rs -> {
        List<Event> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        return list;
    };

    @Override
    protected List<Event> select(String sql, Object... params) {
        List<Event> events = Db.query(sql, LIST_HANDLER, params);
        hydrate(events);
        return events;
    }

    /** Replaces group / collection id shells by loaded entities and installs the lazy subscription loader. */
    void hydrate(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        Set<Integer> groupIds = new LinkedHashSet<>();
        Set<Integer> collectionIds = new LinkedHashSet<>();
        for (Event event : events) {
            if (event.getEventGroup() != null) {
                groupIds.add(event.getEventGroup().getId());
            }
            if (event.getEventCollection() != null) {
                collectionIds.add(event.getEventCollection().getId());
            }
        }
        Map<Integer, EventGroup> groups = new EventGroupDao().findByIds(groupIds);
        Map<Integer, EventCollection> collections = new EventCollectionDao().findByIds(collectionIds);
        for (Event event : events) {
            if (event.getEventGroup() != null) {
                event.setEventGroup(groups.get(event.getEventGroup().getId()));
            }
            if (event.getEventCollection() != null) {
                event.setEventCollection(collections.get(event.getEventCollection().getId()));
            }
            final Event owner = event;
            event.setSubscriptionsLoader(() -> new PlayerSubscriptionDao().findByEvent(owner));
        }
    }

    @Override
    public Event find(Integer id) {
        if (id == null) {
            return null;
        }
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(COLUMNS_WITH_DESCRIPTION)
                .append(" FROM event WHERE id = ?");
        List<Object> args = Db.args(id);
        appendOrgFilter(sql, args, " AND ");
        return first(select(sql.toString(), args.toArray()));
    }

    @Override
    public java.util.Map<Integer, Event> findByIds(Collection<Integer> ids) {
        java.util.Map<Integer, Event> result = new java.util.LinkedHashMap<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        for (Event event : select(
                "SELECT " + LIST_COLUMNS + " FROM event WHERE id IN (" + placeholders(ids) + ")",
                ids.toArray())) {
            result.put(event.getId(), event);
        }
        return result;
    }

    public void updateDescription(Integer eventId, String description) {
        if (eventId == null) {
            return;
        }
        Db.update("UPDATE event SET description = ? WHERE id = ?", description, eventId);
    }

    public List<Event> findByCollectionId(Integer collectionId) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(LIST_COLUMNS)
                .append(" FROM event WHERE big_event_id = ?");
        List<Object> args = Db.args(collectionId);
        appendOrgFilter(sql, args, " AND ");
        sql.append(" ORDER BY id");
        return select(sql.toString(), args.toArray());
    }

    public List<Event> findByGroupId(Integer groupId) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(LIST_COLUMNS)
                .append(" FROM event WHERE event_group_id = ?");
        List<Object> args = Db.args(groupId);
        appendOrgFilter(sql, args, " AND ");
        sql.append(" ORDER BY id");
        return select(sql.toString(), args.toArray());
    }

    @Override
    public void persist(Event event) {
        if (event.getId() == null) {
            OrgEntityListener.assignOrg(event);
            int id = Db.insertReturningInt(
                    "INSERT INTO event (org_id, name, start_date, end_date, pricecents, youngpricecents,"
                            + " status_id, event_type, event_group_id, big_event_id)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    values(event));
            event.setId(id);
        } else {
            update(event);
        }
    }

    @Override
    public Event merge(Event event) {
        if (event.getId() == null) {
            persist(event);
        } else {
            update(event);
        }
        return event;
    }

    private void update(Event event) {
        Object[] values = values(event);
        Object[] args = new Object[values.length + 1];
        System.arraycopy(values, 0, args, 0, values.length);
        args[values.length] = event.getId();
        Db.update(
                "UPDATE event SET org_id = ?, name = ?, start_date = ?, end_date = ?, pricecents = ?,"
                        + " youngpricecents = ?, status_id = ?, event_type = ?,"
                        + " event_group_id = ?, big_event_id = ? WHERE id = ?",
                args);
    }

    private static Object[] values(Event event) {
        return new Object[] {
            event.getOrgId(),
            event.getName(),
            event.getStartDate(),
            event.getEndDate(),
            event.getPriceCents(),
            event.getYoungPriceCents(),
            Db.enumOrdinal(event.getStatus()),
            Db.enumOrdinal(event.getEventType()),
            Db.fkId(event.getEventGroup()),
            Db.fkId(event.getEventCollection())
        };
    }

    public void setFinder(Find finder) {
        this.finder = finder;
    }

    public void setEventGroupDao(Supplier<EventGroupDao> eventGroupService) {
        this.eventGroupService = eventGroupService;
    }

    public void setEventOptionDao(Supplier<EventOptionDao> eventOptionDao) {
        this.eventOptionDao = eventOptionDao;
    }

    public void setEventCollectionOptionDao(Supplier<EventCollectionOptionDao> eventCollectionOptionDao) {
        this.eventCollectionOptionDao = eventCollectionOptionDao;
    }

    public void setPlayerSubscriptionDao(Supplier<PlayerSubscriptionDao> playerSubscriptionDao) {
        this.playerSubscriptionDao = playerSubscriptionDao;
    }

    public List<Event> findByStatus(EventStatus status) {
        return findByStatus(status, SeasonScope.all());
    }

    /** All events with the given status, including those in an event group. */
    public List<Event> findAllByStatus(EventStatus status) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(LIST_COLUMNS)
                .append(" FROM event WHERE status_id = ?");
        List<Object> args = Db.args(Db.enumOrdinal(status));
        appendOrgFilter(sql, args, " AND ");
        sql.append(" ORDER BY start_date ASC, name ASC");
        return select(sql.toString(), args.toArray());
    }

    public List<Event> findByStatus(EventStatus status, SeasonScope scope) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(LIST_COLUMNS)
                .append(" FROM event WHERE status_id = ? AND event_group_id IS NULL");
        List<Object> args = Db.args(Db.enumOrdinal(status));
        appendOrgFilter(sql, args, " AND ");
        appendSeasonScope(sql, args, scope, "start_date", " AND ");
        sql.append(" ORDER BY start_date ASC, name ASC");
        return select(sql.toString(), args.toArray());
    }

    public List<Event> findByStatus(EventStatus status, EventGroup eventGroup) {
        return findByStatus(status, eventGroup, SeasonScope.all());
    }

    public List<Event> findByStatus(EventStatus status, EventGroup eventGroup, SeasonScope scope) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(LIST_COLUMNS)
                .append(" FROM event WHERE status_id = ? AND event_group_id = ?");
        List<Object> args = Db.args(Db.enumOrdinal(status), Db.fkId(eventGroup));
        appendOrgFilter(sql, args, " AND ");
        appendSeasonScope(sql, args, scope, "start_date", " AND ");
        sql.append(" ORDER BY start_date ASC, name DESC");
        return select(sql.toString(), args.toArray());
    }

    public String buildCsv(Integer eventId) {
        EventCache cache = buildCache(eventId);
        Event event = find(eventId);
        List<PlayerSubscription> subscriptions = event.getSubscriptions();
        Map<Integer, PlayerSubscription> map = new HashMap<Integer, PlayerSubscription>();
        for (var sub : subscriptions) {
            map.put(sub.getId(), sub);
        }
        StringBuilder str = new StringBuilder();
        str.append("licence;name;rating;status;email;subId;club;title;amountPaid;createdAt;updatedAt\n");
        for (var player : cache.players) {
            str.append(player.getNrffe()).append(";");
            str.append(player.getName());
            if (player.getFirstname() != null && !player.getFirstname().isEmpty()) {
                str.append(" ").append(player.getFirstname());
            }
            str.append(";");
            str.append(player.getRating()).append(";");
            str.append(player.getStatus().name()).append(";");
            PlayerSubscription sub = map.get(player.getSubId());
            str.append(sub.getCreationUser()).append(";");
            str.append(player.getSubId() + "").append(";");
            str.append(player.getClubRef()).append(";");
            str.append(player.getFideTitre() == null ? "" : player.getFideTitre().toUpperCase()).append(";");
            str.append(sub.getAmountCents() == null ? "" : ServiceUtils.toEuros(sub.getAmountCents()) + "").append(";");
            str.append(sub.getCreatedAt() == null ? "" : sub.getCreatedAt()).append(";");
            str.append(sub.getUpdatedAt() == null ? "" : sub.getUpdatedAt()).append("\n");
        }
        return str.toString();
    }

    public EventCache buildCache(Integer eventId) {
        EventCache cached = new EventCache();
        Event event = find(eventId);
        fillSubscriptionLimits(event);
        cached.event = event;
        List<PlayerSubscription> subscriptions = playerSubscriptionDao.get().findByEvent(event);
        event.setSubscriptions(subscriptions);
        List<DisplayPlayer> players = new ArrayList<>();
        List<String> missingPlayerCodes = new ArrayList<>();
        for (PlayerSubscription sub : subscriptions) {
            if (sub.getStatus() == com.github.gcolin.registration.PlayerSubscriptionStatus.CANCELLED) {
                continue;
            }
            DisplayPlayer player = resolveDisplayPlayer(sub, event, missingPlayerCodes);
            if (player != null) {
                players.add(player);
            }
        }
        players.sort((p1, p2) -> {
            int nameCompare = p1.getName().compareTo(p2.getName());
            if (nameCompare != 0) {
                return nameCompare;
            }
            return p1.getFirstname().compareTo(p2.getFirstname());
        });
        cached.players = players;
        cached.missingPlayerCodes = missingPlayerCodes;

        if (event.getDescription() != null && !event.getDescription().isBlank()) {
            Parser parser = Parser.builder().build();
            Node document = parser.parse(event.getDescription());
            HtmlRenderer renderer = HtmlRenderer.builder().escapeHtml(true).build();
            cached.eventInfo = renderer.render(document);
        }
        detach(cached.event);
        event.setSubscriptions(new ArrayList<>());

        return cached;
    }

    /**
     * Resolves a subscription to a DisplayPlayer. Custom / amical players (@id) and FFE
     * licences missing from Lucene are still exported as placeholders.
     */
    private DisplayPlayer resolveDisplayPlayer(
            PlayerSubscription sub, Event event, List<String> missingPlayerCodes) {
        String code = sub.getNrFfe();
        IPlayer p = null;
        try {
            p = finder.player(code, event.getEventType());
        } catch (RuntimeException e) {
            logger.error("cannot resolve player with code {}", code, e);
        }
        if (p == null) {
            logger.error("cannot find player with code {} — exporting placeholder", code);
            if (code != null) {
                missingPlayerCodes.add(code);
            }
            return finalizeDisplayPlayer(placeholderPlayer(sub), sub, event, null);
        }
        try {
            DisplayPlayer player = new DisplayPlayer(p);
            return finalizeDisplayPlayer(player, sub, event, p);
        } catch (RuntimeException e) {
            logger.error("cannot map player with code {} — exporting placeholder", code, e);
            if (code != null) {
                missingPlayerCodes.add(code);
            }
            return finalizeDisplayPlayer(placeholderPlayer(sub), sub, event, null);
        }
    }

    private static DisplayPlayer finalizeDisplayPlayer(
            DisplayPlayer player, PlayerSubscription sub, Event event, IPlayer source) {
        player.setStatus(sub.getStatus());
        player.setAttendanceAt(sub.getAttendanceAt());
        player.setSubId(sub.getId());
        if (source != null) {
            player.setBirthDate(source.getBirthDate());
            player.setClubRef(source.getClubRef());
            player.setRating(source, event.getEventType());
        }
        if (player.getFederation() == null || player.getFederation().isBlank()) {
            player.setFederation("FRA");
        }
        return player;
    }

    private static DisplayPlayer placeholderPlayer(PlayerSubscription sub) {
        DisplayPlayer player = new DisplayPlayer();
        String ref = sub.getNrFfe() == null ? "" : sub.getNrFfe().trim();
        if (ref.startsWith("@") && ref.length() > 1) {
            player.setName("JOUEUR");
            player.setFirstname(ref.substring(1));
        } else if (!ref.isEmpty()) {
            player.setName(ref);
            player.setFirstname("");
        } else {
            player.setName("JOUEUR");
            player.setFirstname(String.valueOf(sub.getId()));
        }
        player.setNrffe(ref);
        player.setNrffeId(ref);
        player.setFederation("FRA");
        player.setCategory("SenM");
        player.setRating("1199E");
        player.setStandardRating("1199E");
        player.setRapidRating("1199E");
        player.setBlitzRating("1199E");
        player.setBirthDate("1990-01-01T00:00:00");
        player.setEditable(true);
        return player;
    }

    public Integer saveEvent(
            Integer id,
            String name,
            String startDate,
            String endDate,
            String status,
            String eventType,
            String price,
            String youngprice,
            String eventgroupId,
            String eventCollectionId,
            String rondesStr,
            String cadence,
            String pairing,
            String eventMaxSubscriptionsStr) {
        Event event = new Event();
        event.setName(name);
        LocalDate startDateLocal = LocalDate.parse(startDate);
        event.setStartDate(startDateLocal.atStartOfDay());
        LocalDate endDateLocal = LocalDate.parse(endDate);
        event.setEndDate(endDateLocal.atStartOfDay());
        event.setStatus(EventStatus.valueOf(status));
        event.setEventType(EventType.valueOf(eventType));
        event.setPriceCents(Math.round(Double.parseDouble(price) * 100d));
        event.setYoungPriceCents(Math.round(Double.parseDouble(youngprice) * 100d));
        if (rondesStr != null && !rondesStr.isEmpty()) {
            // Rondes now saved via EventOptionType.ROUNDS
        }
        if (id != null) {
            event.setId(id);
        }
        if (eventgroupId != null && !eventgroupId.isEmpty()) {
            event.setEventGroup(eventGroupService.get().find(Integer.valueOf(eventgroupId)));
        }
        if (eventCollectionId != null && !eventCollectionId.isEmpty()) {
            event.setEventCollection(new EventCollectionDao().find(Integer.valueOf(eventCollectionId)));
        } else {
            event.setEventCollection(null);
        }
        if (event.getId() == null) {
            persist(event);
            flush();
        } else {
            merge(event);
        }
        eventOptionDao.get().setOption(event.getId(), EventOptionType.ROUNDS, rondesStr != null ? rondesStr : "");
        eventOptionDao.get().setOption(event.getId(), EventOptionType.CADENCE, cadence != null ? cadence : "");
        eventOptionDao.get().setOption(event.getId(), EventOptionType.PAIRING, pairing != null ? pairing : "");
        eventOptionDao.get().setOption(
                event.getId(),
                EventOptionType.MAX_SUBSCRIPTIONS,
                eventMaxSubscriptionsStr != null ? eventMaxSubscriptionsStr : "");
        return event.getId();
    }

    public void fillSubscriptionLimits(Event event) {
        if (event == null || event.getId() == null) {
            return;
        }
        // Load all event options into the map
        Map<EventOptionType, EventOption> options = new HashMap<>();
        for (EventOption option : eventOptionDao.get().findByEventId(event.getId())) {
            options.put(option.getOptionType(), option);
        }
        event.setEventOptions(options);
        fillEventCollectionLimits(event.getEventCollection());
    }

    public void fillNbSubscriptions(List<Event> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        List<Integer> eventIds = events.stream()
                .map(Event::getId)
                .filter(id -> id != null)
                .toList();
        if (eventIds.isEmpty()) {
            return;
        }
        Map<Integer, Long> counts = playerSubscriptionDao.get().countByEventIds(eventIds);
        for (Event event : events) {
            if (event.getId() == null) {
                continue;
            }
            event.setNbSubscriptions(counts.getOrDefault(event.getId(), 0L).intValue());
            event.setSubscriptions(new ArrayList<>());
        }
    }

    private void fillEventCollectionLimits(EventCollection eventCollection) {
        if (eventCollection == null || eventCollection.getId() == null) {
            return;
        }
        eventCollection.setMaxSubscribe(eventCollectionOptionDao.get()
                .findIntOptionValue(eventCollection.getId(), EventCollectionOptionType.MAX_SUBSCRIPTIONS));
        eventCollection.setNbSubscriptions((int) playerSubscriptionDao.get().countByEventCollection(eventCollection.getId()));
    }

    public List<Event> findAllForAdmin() {
        return findAllForAdmin(SeasonScope.all());
    }

    public List<Event> findAllForAdmin(SeasonScope scope) {
        StringBuilder sql = new StringBuilder("SELECT ").append(LIST_COLUMNS).append(" FROM event");
        List<Object> args = Db.args();
        appendOrgFilter(sql, args, " WHERE ");
        appendSeasonScope(sql, args, scope, "start_date", args.isEmpty() ? " WHERE " : " AND ");
        sql.append(" ORDER BY start_date ASC, name ASC");
        return select(sql.toString(), args.toArray());
    }

    public List<Event> findClosestEvents(int limit) {
        return findClosestEvents(limit, SeasonScope.all());
    }

    public List<Event> findClosestEvents(int limit, SeasonScope scope) {
        StringBuilder sql = new StringBuilder("SELECT ")
                .append(LIST_COLUMNS)
                .append(" FROM event WHERE start_date > ?");
        List<Object> args = Db.args(LocalDateTime.now());
        appendOrgFilter(sql, args, " AND ");
        appendSeasonScope(sql, args, scope, "start_date", " AND ");
        sql.append(" ORDER BY start_date ASC LIMIT ?");
        args.add(limit);
        return select(sql.toString(), args.toArray());
    }

    public List<Object[]> findTopEventsByParticipants(int limit) {
        return findTopEventsByParticipants(limit, SeasonScope.all());
    }

    public List<Object[]> findTopEventsByParticipants(int limit, SeasonScope scope) {
        String columns = "e.id, e.org_id, e.name, e.start_date, e.end_date, e.pricecents,"
                + " e.youngpricecents, e.status_id, e.event_type, e.event_group_id, e.big_event_id";
        StringBuilder sql = new StringBuilder("SELECT " + columns + ", COUNT(ps.id) AS participant_count FROM event e"
                + " LEFT JOIN player_subscription ps ON ps.event_id = e.id");
        List<Object> args = Db.args();
        appendOrgFilter(sql, args, " WHERE ", "e");
        appendSeasonScope(sql, args, scope, "e.start_date", args.isEmpty() ? " WHERE " : " AND ");
        sql.append(" GROUP BY ").append(columns).append(" ORDER BY participant_count DESC LIMIT ?");
        args.add(limit);
        List<Object[]> rows = Db.query(
                sql.toString(),
                rs -> {
                    List<Object[]> list = new ArrayList<>();
                    while (rs.next()) {
                        list.add(new Object[] {mapRow(rs), rs.getLong("participant_count")});
                    }
                    return list;
                },
                args.toArray());
        List<Event> events = new ArrayList<>();
        for (Object[] row : rows) {
            events.add((Event) row[0]);
        }
        hydrate(events);
        return rows;
    }

    private static void appendSeasonScope(
            StringBuilder sql, List<Object> args, SeasonScope scope, String column, String keyword) {
        if (scope != null && scope.isFiltered()) {
            sql.append(keyword).append(column).append(" >= ? AND ").append(column).append(" <= ?");
            args.add(scope.getStart());
            args.add(scope.getEnd());
        }
    }
}

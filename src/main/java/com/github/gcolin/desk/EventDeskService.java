package com.github.gcolin.desk;

import com.github.gcolin.platform.ServiceUtils;

import com.github.gcolin.auth.RoleCode;
import com.github.gcolin.auth.RoleResolver;
import com.github.gcolin.auth.UserAuthorizationDao;
import com.github.gcolin.platform.AppContext;
import com.github.gcolin.platform.Config;
import com.github.gcolin.event.Event;
import com.github.gcolin.registration.PlayerSubscription;
import com.github.gcolin.registration.PlayerSubscriptionOption;
import com.github.gcolin.player.DisplayPlayer;
import com.github.gcolin.desk.EventDeskEventDto;
import com.github.gcolin.desk.EventDeskOp;
import com.github.gcolin.desk.EventDeskPlayerDto;
import com.github.gcolin.event.EventType;
import com.github.gcolin.player.IPlayer;
import com.github.gcolin.registration.PlayerSubscriptionOptionStatus;
import com.github.gcolin.registration.PlayerSubscriptionStatus;
import com.github.gcolin.payment.Payment;
import com.github.gcolin.payment.PaymentStatus;
import com.github.gcolin.payment.PaymentType;
import com.github.gcolin.payment.PaymentMail;
import com.github.gcolin.platform.Caches;
import com.github.gcolin.platform.RequestContext;
import com.github.gcolin.platform.SendMail;
import com.github.gcolin.event.EventCache;
import com.github.gcolin.event.EventDao;
import com.github.gcolin.payment.PaymentDao;
import com.github.gcolin.registration.PlayerSubscriptionDao;
import com.github.gcolin.registration.PlayerSubscriptionOptionDao;
import java.util.function.Supplier;
import javax.sql.DataSource;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventDeskService {

    private static final Logger logger = LoggerFactory.getLogger(EventDeskService.class);
    private static final DateTimeFormatter ATTENDANCE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private DataSource dataSource;
    private Caches caches;
    private SendMail mail;

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /** Runs {@code action} on its own connection / transaction (fresh view of committed data). */
    private <T> T inContext(Supplier<T> action) {
        return RequestContext.run(dataSource, action);
    }

    public void setCaches(Caches caches) {
        this.caches = caches;
    }

    public void setSendMail(SendMail mail) {
        this.mail = mail;
    }

    public List<EventDeskPlayerDto> snapshot(Integer eventId) {
        return inContext(() -> toDtos(buildCache(eventId)));
    }

    public List<EventDeskEventDto> collectionSnapshots(Integer eventId) {
        return inContext(() -> {
            Event event = new EventDao().find(eventId);
            if (event == null) {
                return List.<EventDeskEventDto>of();
            }
            List<Event> events = new ArrayList<>();
            if (event.getEventCollection() != null) {
                events.addAll(event.getEventCollection().getEvents());
            } else {
                events.add(event);
            }
            events.sort(Comparator.comparing(Event::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Event::getId, Comparator.nullsLast(Comparator.naturalOrder())));
            List<EventDeskEventDto> result = new ArrayList<>();
            for (Event sibling : events) {
                EventDeskEventDto dto = new EventDeskEventDto();
                dto.setId(sibling.getId());
                dto.setName(sibling.getName());
                dto.setFree(sibling.isFree());
                dto.setPlayers(toDtos(buildCache(sibling.getId())));
                result.add(dto);
            }
            return result;
        });
    }

    public List<Integer> collectionEventIds(Integer eventId) {
        return inContext(() -> {
            Event event = new EventDao().find(eventId);
            if (event == null) {
                return List.<Integer>of();
            }
            List<Event> events = new ArrayList<>();
            if (event.getEventCollection() != null) {
                events.addAll(event.getEventCollection().getEvents());
            } else {
                events.add(event);
            }
            events.sort(Comparator.comparing(Event::getStartDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Event::getId, Comparator.nullsLast(Comparator.naturalOrder())));
            List<Integer> ids = new ArrayList<>();
            for (Event sibling : events) {
                ids.add(sibling.getId());
            }
            return ids;
        });
    }

    public List<String> applyOps(Integer eventId, List<EventDeskOp> ops) {
        List<String> acked = new ArrayList<>();
        if (ops == null || ops.isEmpty()) {
            return acked;
        }
        List<EventDeskOp> ordered = new ArrayList<>(ops);
        ordered.sort(Comparator.comparing(op -> op.getClientTs() == null ? 0L : op.getClientTs()));

        inContext(() -> {
            PlayerSubscriptionDao subscriptionDao = new PlayerSubscriptionDao();
            PlayerSubscriptionOptionDao optionDao = new PlayerSubscriptionOptionDao();
            for (EventDeskOp op : ordered) {
                if (op.getOptionId() != null) {
                    PlayerSubscriptionOption option = optionDao.find(op.getOptionId());
                    if (option == null
                            || option.getPlayerSubscription() == null
                            || option.getPlayerSubscription().getEvent() == null
                            || !eventId.equals(option.getPlayerSubscription().getEvent().getId())) {
                        logger.warn("desk op ignored: option {} not in event {}", op.getOptionId(), eventId);
                        if (op.getId() != null) {
                            acked.add(op.getId());
                        }
                        continue;
                    }
                    if (op.getStatus() != null && !op.getStatus().isBlank()) {
                        PlayerSubscriptionOptionStatus previousStatus = option.getStatus();
                        PlayerSubscriptionOptionStatus next = PlayerSubscriptionOptionStatus.valueOf(op.getStatus());
                        option.setStatus(next);
                        if (previousStatus == PlayerSubscriptionOptionStatus.NOT_PAID
                                && next == PlayerSubscriptionOptionStatus.PAID) {
                            attachCashPayment(option);
                            logger.info(
                                    "[email={},player={},event={},option={}] desk option payment marked PAID ({} cents)",
                                    option.getPlayerSubscription().getCreationUser(),
                                    option.getPlayerSubscription().getNrFfe(),
                                    eventId,
                                    option.getId(),
                                    option.getAmountCents());
                        }
                        optionDao.merge(option);
                    }
                    if (op.getId() != null) {
                        acked.add(op.getId());
                    }
                    continue;
                }
                if (op.getSubId() == null) {
                    continue;
                }
                PlayerSubscription sub = subscriptionDao.find(op.getSubId());
                if (sub == null || sub.getEvent() == null || !eventId.equals(sub.getEvent().getId())) {
                    logger.warn("desk op ignored: sub {} not in event {}", op.getSubId(), eventId);
                    if (op.getId() != null) {
                        acked.add(op.getId());
                    }
                    continue;
                }
                PlayerSubscriptionStatus previousStatus = sub.getStatus();
                if (op.getPresent() != null) {
                    sub.setAttendanceAt(op.getPresent() ? LocalDateTime.now() : null);
                    logger.info(
                            "[email={},player={},event={},sub={}] desk attendance marked {}",
                            sub.getCreationUser(),
                            sub.getNrFfe(),
                            eventId,
                            sub.getId(),
                            op.getPresent() ? "PRESENT" : "ABSENT");
                }
                if (op.getStatus() != null && !op.getStatus().isBlank()) {
                    PlayerSubscriptionStatus next = PlayerSubscriptionStatus.valueOf(op.getStatus());
                    sub.setStatus(next);
                    if (previousStatus == PlayerSubscriptionStatus.NOT_PAID && next == PlayerSubscriptionStatus.PAID) {
                        attachCashPayment(sub);
                        logger.info(
                                "[email={},player={},event={},sub={}] desk payment marked PAID ({} cents)",
                                sub.getCreationUser(),
                                sub.getNrFfe(),
                                eventId,
                                sub.getId(),
                                sub.getAmountCents());
                        sendPaymentMail(sub);
                    }
                }
                subscriptionDao.merge(sub);
                if (op.getId() != null) {
                    acked.add(op.getId());
                }
            }
            return null;
        });
        caches.getEvent().invalidate(String.valueOf(eventId));
        caches.getDebtCache().invalidateAll();
        return acked;
    }

    public boolean hasDeskAccess(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        Config config = AppContext.get().config();
        Caches caches = AppContext.get().caches();
        return inContext(() -> {
            RoleResolver roleResolver = new RoleResolver(config, new UserAuthorizationDao(), caches.getRoleCache());
            return roleResolver.hasRole(email, RoleCode.ARBITRE);
        });
    }

    private void attachCashPayment(PlayerSubscription sub) {
        long cents = sub.getAmountCents() == null ? 0L : sub.getAmountCents();
        Payment payment = createCashPayment(sub.getCreationUser(), cents);
        payment.setSubscriptions(List.of(sub));
        sub.setPayment(payment);
    }

    private void attachCashPayment(PlayerSubscriptionOption option) {
        long cents = option.getAmountCents() == null ? 0L : option.getAmountCents();
        PlayerSubscription parent = option.getPlayerSubscription();
        String email = parent == null ? null : parent.getCreationUser();
        Payment payment = createCashPayment(email, cents);
        option.setPayment(payment);
    }

    private Payment createCashPayment(String email, long amountCents) {
        Payment payment = new Payment();
        payment.setUserEmail(email == null || email.isBlank() ? "cash@desk" : email.trim());
        payment.setStatus(PaymentStatus.PAID);
        payment.setType(PaymentType.CASH);
        payment.setAmount(ServiceUtils.toEuros(amountCents));
        new PaymentDao().persist(payment);
        return payment;
    }

    private void sendPaymentMail(PlayerSubscription sub) {
        try {
            Event event = sub.getEvent();
            if (event == null || event.getStartDate() == null) {
                return;
            }
            IPlayer player = RequestContext.require().find().player(sub.getNrFfe(), null);
            if (player == null || sub.getCreationUser() == null || sub.getCreationUser().isBlank()) {
                return;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("EEEE dd MMMM yyyy", Locale.FRANCE);
            PaymentMail registrationMail = new PaymentMail();
            registrationMail.setName(player.getFirstname() + " " + player.getName());
            double eventPrice = ServiceUtils.toEuros(ServiceUtils.calculatePrice(player, event));
            if (eventPrice > 0) {
                registrationMail.setAmount(eventPrice + " Euros");
            }
            registrationMail.setEvenDate(sdf.format(event.getStartDateAsDate()));
            registrationMail.setEventName(event.getName());
            registrationMail.setReference(sub.getCreationUser());
            mail.send(registrationMail, sub.getCreationUser(), "Confirmation paiement " + event.getName());
        } catch (Exception e) {
            logger.error(
                    "[email={},player={},event={}] cannot send payment email. {}",
                    sub.getCreationUser(),
                    sub.getNrFfe(),
                    sub.getEvent() == null ? null : sub.getEvent().getId(),
                    e.toString());
        }
    }

    private EventCache buildCache(Integer eventId) {
        EventCache cached = new EventCache();
        Event event = new EventDao().find(eventId);
        if (event == null) {
            cached.event = null;
            cached.players = List.of();
            return cached;
        }
        List<PlayerSubscription> subscriptions = new PlayerSubscriptionDao().findByEvent(event);
        List<DisplayPlayer> players = new ArrayList<>();
        for (PlayerSubscription sub : subscriptions) {
            if (sub.getStatus() == PlayerSubscriptionStatus.CANCELLED) {
                continue;
            }
            IPlayer p = null;
            try {
                p = RequestContext.require().find().player(sub.getNrFfe(), event.getEventType());
            } catch (RuntimeException e) {
                logger.error("cannot resolve player with code {}", sub.getNrFfe(), e);
            }
            if (p == null) {
                logger.error("cannot find player with code {}", sub.getNrFfe());
                continue;
            }
            DisplayPlayer player;
            try {
                player = new DisplayPlayer(p);
            } catch (RuntimeException e) {
                logger.error("cannot map player with code {}", sub.getNrFfe(), e);
                continue;
            }
            player.setStatus(sub.getStatus());
            player.setAttendanceAt(sub.getAttendanceAt());
            player.setSubId(sub.getId());
            player.setRapidRating(p.getRapidRating());
            player.setBlitzRating(p.getBlitzRating());
            player.setBirthDate(p.getBirthDate());
            player.setClubRef(p.getClubRef());
            if (sub.getAmountCents() != null) {
                player.setPriceCents(sub.getAmountCents());
            } else {
                player.setPriceCents(ServiceUtils.calculatePrice(p, event));
            }
            if (event.getEventType() == EventType.RAPID) {
                player.setRating(p.getRapidRating());
            } else if (event.getEventType() == EventType.BLITZ) {
                player.setRating(p.getBlitzRating());
            } else {
                player.setRating(p.getRating());
            }
            players.add(player);
        }
        players.sort((p1, p2) -> {
            int nameCompare = p1.getName().compareToIgnoreCase(p2.getName());
            if (nameCompare != 0) {
                return nameCompare;
            }
            return p1.getFirstname().compareToIgnoreCase(p2.getFirstname());
        });
        cached.event = event;
        cached.players = players;
        return cached;
    }

    private List<EventDeskPlayerDto> toDtos(EventCache cache) {
        List<EventDeskPlayerDto> result = new ArrayList<>();
        if (cache == null || cache.players == null) {
            return result;
        }
        Map<Integer, List<PlayerSubscriptionOption>> optionsBySubId = new HashMap<>();
        if (cache.event != null && cache.event.getId() != null) {
            for (PlayerSubscriptionOption option : new PlayerSubscriptionOptionDao().findByEvent(cache.event.getId())) {
                if (option.getPlayerSubscription() == null || option.getPlayerSubscription().getId() == null) {
                    continue;
                }
                optionsBySubId
                        .computeIfAbsent(option.getPlayerSubscription().getId(), k -> new ArrayList<>())
                        .add(option);
            }
        }
        for (DisplayPlayer player : cache.players) {
            EventDeskPlayerDto dto = new EventDeskPlayerDto();
            dto.setRowType("SUB");
            dto.setSubId(player.getSubId());
            dto.setName(player.getName());
            dto.setFirstname(player.getFirstname());
            dto.setLicence(player.getLicence());
            dto.setCategory(player.getCategory());
            dto.setRating(player.getRating());
            dto.setClub(player.getClub());
            dto.setFideTitre(player.getFideTitre());
            dto.setStatus(player.getStatus() == null ? null : player.getStatus().name());
            dto.setPresent(player.getAttendanceAt() != null);
            if (player.getAttendanceAt() != null) {
                dto.setAttendanceAt(player.getAttendanceAt().format(ATTENDANCE_FMT));
            }
            if (player.getPriceCents() != null) {
                dto.setAmountCents(player.getPriceCents());
            }
            result.add(dto);

            List<PlayerSubscriptionOption> options = optionsBySubId.getOrDefault(player.getSubId(), List.of());
            for (PlayerSubscriptionOption option : options) {
                EventDeskPlayerDto optionDto = new EventDeskPlayerDto();
                optionDto.setRowType("OPTION");
                optionDto.setSubId(player.getSubId());
                optionDto.setOptionId(option.getId());
                optionDto.setDescription(option.getDescription());
                optionDto.setName(option.getDescription() == null ? "" : option.getDescription());
                optionDto.setFirstname("");
                optionDto.setLicence(player.getLicence());
                optionDto.setCategory(player.getCategory());
                optionDto.setRating(player.getRating());
                optionDto.setClub(player.getClub());
                optionDto.setStatus(option.getStatus() == null ? null : option.getStatus().name());
                optionDto.setPresent(false);
                optionDto.setAmountCents(option.getAmountCents());
                result.add(optionDto);
            }
        }
        return result;
    }
}

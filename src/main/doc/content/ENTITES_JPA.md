# Entités JPA — Ticket Chess

Inventaire des classes annotées `@Entity` / `@MappedSuperclass` et de leurs champs persistés.

## Sommaire

| Entité | Table | Hérite de |
|--------|-------|-----------|
| [TimedEntity](#timedentity) | — (`@MappedSuperclass`) | — |
| [Organisation](#organisation) | `organisation` | — |
| [UserAuthorization](#userauthorization) | `user_authorization` | — |
| [ClubSeason](#clubseason) | `club_season` | TimedEntity |
| [CustomPlayer](#customplayer) | `custom_player` | — |
| [EventGroup](#eventgroup) | `event_group` | — |
| [EventCollection](#eventcollection) | `big_event` | — |
| [EventCollectionOption](#eventcollectionoption) | `eventcollectionoption` | — |
| [Event](#event) | `event` | — |
| [EventInfo](#eventinfo) | `eventinfo` | — |
| [EventOption](#eventoption) | `eventoption` | — |
| [PlayerSubscription](#playersubscription) | `player_subscription` | TimedEntity |
| [PlayerSubscriptionOption](#playersubscriptionoption) | `player_subscription_option` | TimedEntity |
| [PlayerPendingSubscription](#playerpendingsubscription) | `player_pending_subscription` | TimedEntity |
| [Payment](#payment) | `payment` | TimedEntity |
| [License](#license) | `license` | TimedEntity |
| [LicensePrice](#licenseprice) | `license_price` | TimedEntity |
| [Membership](#membership) | `membership` | TimedEntity |
| [MembershipOption](#membershipoption) | `membership_option` | TimedEntity |
| [MembershipOptionSubscription](#membershipoptionsubscription) | `membership_option_subscription` | TimedEntity |
| [MembershipOptionFile](#membershipoptionfile) | `membership_option_file` | TimedEntity |

---

## TimedEntity

`@MappedSuperclass` — `com.github.gcolin.platform.TimedEntity`

Champs communs injectés automatiquement à la création / mise à jour.

| Champ | Colonne | Type | Notes |
|-------|---------|------|-------|
| `createdAt` | `created_at` | `LocalDateTime` | `@PrePersist` |
| `updatedAt` | `updated_at` | `LocalDateTime` | `@PrePersist` / `@PreUpdate` |

---

## Organisation

`com.github.gcolin.organisation.Organisation` → table `organisation`

Contraintes uniques : `slug`, `base_url`. Définit le filtre Hibernate `OrgScope` (`org_id = :orgId`).

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `slug` | `slug` | `String(100)` | non | UK |
| `baseUrl` | `base_url` | `String(500)` | non | UK |
| `name` | `name` | `String(255)` | non | |
| `superUserEmail` | `super_user_email` | `String(320)` | non | |
| `active` | `active` | `boolean` | non | défaut `true` |
| `stripePublic` | `stripe_public` | `String(512)` | oui | |
| `stripeSecret` | `stripe_secret` | `String(512)` | oui | |
| `stripeKeyprefix` | `stripe_keyprefix` | `String(100)` | oui | |
| `stripeSimuled` | `stripe_simuled` | `boolean` | non | |
| `stripeCardEvents` | `stripe_card_events` | `boolean` | non | défaut `true` |
| `stripeCardMemberships` | `stripe_card_memberships` | `boolean` | non | défaut `true` |
| `paymentTransferEvents` | `payment_transfer_events` | `boolean` | non | défaut `true` |
| `paymentTransferMemberships` | `payment_transfer_memberships` | `boolean` | non | défaut `true` |
| `oauthClientId` | `oauth_client_id` | `String(512)` | oui | |
| `oauthClientSecret` | `oauth_client_secret` | `String(512)` | oui | |
| `oauthAuthorizationUrl` | `oauth_authorization_url` | `String(1000)` | oui | |
| `oauthTokenUrl` | `oauth_token_url` | `String(1000)` | oui | |
| `oauthScope` | `oauth_scope` | `String(500)` | oui | |
| `oauthLogoutUrl` | `oauth_logout_url` | `String(1000)` | oui | |
| `oauthAccountUrl` | `oauth_account_url` | `String(1000)` | oui | |
| `oauthUserinfoUrl` | `oauth_userinfo_url` | `String(1000)` | oui | |
| `mailUserName` | `mail_user_name` | `String(320)` | oui | |
| `mailPassword` | `mail_password` | `String(512)` | oui | |
| `settingsJson` | `settings_json` | `text` | oui | |

---

## UserAuthorization

`com.github.gcolin.auth.UserAuthorization` → table `user_authorization`

UK : `(email, role, scope_type, scope_id)`. Index sur `email` et `(email, role, scope_type, scope_id, active)`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `email` | `email` | `String(320)` | non | normalisé lower-case |
| `role` | `role` | `RoleCode` (STRING) | non | `ADMIN`, `TRESORIER`, `ENTRAINEUR`, `ARBITRE`, `EVENT_ADMIN` |
| `scopeType` | `scope_type` | `AuthorizationScopeType` (STRING) | non | défaut `GLOBAL` ; `GLOBAL`, `EVENT`, `EVENT_GROUP` |
| `scopeId` | `scope_id` | `Integer` | oui | |
| `active` | `active` | `boolean` | non | défaut `true` |
| `validUntil` | `valid_until` | `LocalDateTime` | oui | |
| `grantedBy` | `granted_by` | `String(320)` | oui | normalisé lower-case |

---

## ClubSeason

`com.github.gcolin.club.ClubSeason` → table `club_season`

Étend `TimedEntity`, `OrgOwned`. Filtre org + `OrgEntityListener`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `orgId` | `org_id` | `Integer` | non | multi-tenant |
| `name` | `name` | `String(255)` | non | |
| `startDate` | `start_date` | `LocalDate` | non | |
| `endDate` | `end_date` | `LocalDate` | non | |
| `current` | `is_current` | `boolean` | non | défaut `false` |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## CustomPlayer

`com.github.gcolin.player.CustomPlayer` → table `custom_player`

Implémente `IPlayer` (joueurs hors annuaire FFE).

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | `nrffe` calculé = `@` + id |
| `firstname` | `firstname` | `String(255)` | non | |
| `name` | `name` | `String(255)` | non | |
| `licence` | `licence` | `String(255)` | oui | |
| `birthDate` | `birthdate` | `String(255)` | oui | |
| `creationUser` | `creation_user` | `String(255)` | oui | |
| `gender` | `gender` | `Boolean` | oui | |
| `elo` | `elo` | `String(255)` | oui | |

---

## EventGroup

`com.github.gcolin.event.EventGroup` → table `event_group`

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `name` | `name` | `String(255)` | non | |
| `shortname` | `shortname` | `String(255)` | non | |
| `events` | — | `List<Event>` | | `@OneToMany(mappedBy = "eventGroup")` |

---

## EventCollection

`com.github.gcolin.event.EventCollection` → table `big_event`

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `name` | `name` | `String(255)` | non | |
| `events` | — | `List<Event>` | | `@OneToMany(mappedBy = "eventCollection")`, cascade ALL |
| `maxSubscribe` | — | `Integer` | | `@Transient` |
| `nbSubscriptions` | — | `Integer` | | `@Transient` |
| `eventCount` | — | `Integer` | | `@Transient` |

---

## EventCollectionOption

`com.github.gcolin.event.EventCollectionOption` → table `eventcollectionoption`

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `eventCollection` | `big_event_id` | `EventCollection` | oui | `@ManyToOne` LAZY |
| `optionType` | `option_type` | `EventCollectionOptionType` (STRING) | non | `MAX_SUBSCRIPTIONS`, `CHESS_EVENT_ID` |
| `value` | `option_value` | `String(255)` | non | |

---

## Event

`com.github.gcolin.event.Event` → table `event`

Index : `status_id`, `start_date`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `name` | `name` | `String` | non | |
| `startDate` | `start_date` | `LocalDateTime` | non | |
| `endDate` | `end_date` | `LocalDateTime` | non | |
| `price` | `price` | `BigDecimal(10,2)` | oui | sync avec `priceCents` |
| `priceCents` | `pricecents` | `Long` | oui | |
| `youngPrice` | `youngPrice` | `BigDecimal(10,2)` | oui | sync avec `youngPriceCents` |
| `youngPriceCents` | `youngpricecents` | `Long` | oui | |
| `status` | `status_id` | `EventStatus` (ORDINAL) | non | défaut `DRAFT` ; `DRAFT`, `ACTIVE`, `ONGOING`, `COMPLETED` |
| `eventType` | `event_type` | `EventType` (ORDINAL) | non | défaut `STANDARD` ; `STANDARD`, `RAPID`, `BLITZ` |
| `subscriptions` | — | `List<PlayerSubscription>` | | `@OneToMany(mappedBy = "event")` |
| `eventGroup` | `event_group_id` | `EventGroup` | oui | `@ManyToOne` LAZY |
| `eventCollection` | `big_event_id` | `EventCollection` | oui | `@ManyToOne` LAZY |
| `eventInfo` | — | `EventInfo` | | `@OneToOne(mappedBy = "event")` LAZY |
| `eventOptions` | — | `Map<EventOptionType, EventOption>` | | `@Transient` |
| `players` | — | `List<DisplayPlayer>` | | `@Transient` |
| `nbSubscriptions` | — | `Integer` | | `@Transient` |

---

## EventInfo

`com.github.gcolin.event.EventInfo` → table `eventinfo`

PK partagée avec `Event` via `@MapsId`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `event` | `eventid` | `Event` | PK | `@OneToOne` + `@MapsId` |
| `description` | `description` | `TEXT` | oui | |

---

## EventOption

`com.github.gcolin.event.EventOption` → table `eventoption`

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `optionType` | `option_type` | `EventOptionType` (STRING) | non | `FFE_ID`, `FFE_PASSWORD`, `MAX_ELO`, `MIN_ELO`, `MAX_SUBSCRIPTIONS`, `ROUNDS`, `CADENCE`, `CLUB_REF`, `PAIRING`, `POINTAGE`, `CHESS_EVENT_USER`, `CHESS_EVENT_PASSWORD` |
| `event` | `event_id` | `Event` | oui | `@ManyToOne` LAZY |
| `value` | `option_value` | `String(255)` | non | |

---

## PlayerSubscription

`com.github.gcolin.registration.PlayerSubscription` → table `player_subscription`

Étend `TimedEntity`. Index : `event_id`, `creation_user`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `nrFfe` | `nr_ffe` | `String(255)` | oui | |
| `creationUser` | `creation_user` | `String(255)` | oui | |
| `event` | `event_id` | `Event` | non | `@ManyToOne` LAZY |
| `status` | `status` | `PlayerSubscriptionStatus` (ORDINAL) | non | `NOT_PAID`, `PAID`, `CANCELLED` |
| `payment` | `payment_id` | `Payment` | oui | `@ManyToOne` LAZY |
| `amountCents` | `amountcents` | `Long` | oui | |
| `attendanceAt` | `attendance_at` | `LocalDateTime` | oui | pointage |
| `player` | — | `DisplayPlayer` | | `@Transient` |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## PlayerSubscriptionOption

`com.github.gcolin.registration.PlayerSubscriptionOption` → table `player_subscription_option`

Étend `TimedEntity`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `playerSubscription` | `player_subscription_id` | `PlayerSubscription` | non | `@ManyToOne` LAZY |
| `amountCents` | `amountcents` | `Long` | oui | |
| `description` | `description` | `String(512)` | oui | |
| `payment` | `payment_id` | `Payment` | oui | `@ManyToOne` LAZY |
| `status` | `status` | `PlayerSubscriptionOptionStatus` (ORDINAL) | non | `NOT_PAID`, `PAID` |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## PlayerPendingSubscription

`com.github.gcolin.registration.PlayerPendingSubscription` → table `player_pending_subscription`

Étend `TimedEntity`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `nrFfe` | `nr_ffe` | `String(255)` | oui | |
| `creationUser` | `creation_user` | `String(255)` | oui | |
| `event` | `event_id` | `Event` | non | `@ManyToOne` LAZY |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## Payment

`com.github.gcolin.payment.Payment` → table `payment`

Étend `TimedEntity`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Long` | PK, IDENTITY | |
| `stripeSessionId` | `stripeSessionId` | `String` | oui | unique |
| `stripeIntent` | `stripeIntent` | `String` | oui | |
| `userEmail` | `userEmail` | `String` | non | |
| `payerName` | `payerName` | `String(255)` | oui | |
| `payerAddress` | `payerAddress` | `String(1000)` | oui | |
| `donation` | `donation` | `boolean` | non | |
| `status` | `status` | `PaymentStatus` (STRING) | oui | `PENDING`, `PAID`, `EXPIRED` |
| `type` | `type` | `PaymentType` (STRING) | oui | `CARD`, `BANK_TRANSFER`, `FREE`, `CASH`, `CHEQUE` |
| `amount` | `amount` | `Double` | oui | sync `amountCents` |
| `amountCents` | `amountCents` | `Long` | oui | |
| `subscriptions` | `payment_id` | `List<PlayerSubscription>` | | `@OneToMany` + `@JoinColumn` |
| `memberships` | `payment_id` | `List<Membership>` | | `@OneToMany` + `@JoinColumn` |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## License

`com.github.gcolin.membership.License` → table `license`

Étend `TimedEntity`, `OrgOwned`. UK : `(org_id, name)`. Filtre org + `OrgEntityListener`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `orgId` | `org_id` | `Integer` | non | multi-tenant |
| `name` | `name` | `String(50)` | non | |
| `accessRule` | `access_rule` | `MembershipOptionAccessRule` (STRING) | non | défaut `ALL` ; `ALL`, `YOUNG_ONLY`, `NON_YOUNG_ONLY`, `ADMIN` |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## LicensePrice

`com.github.gcolin.membership.LicensePrice` → table `license_price`

Étend `TimedEntity`. UK : `(category, license_id)`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `category` | `category` | `String(10)` | non | |
| `priceCents` | `price_cents` | `Integer` | non | |
| `license` | `license_id` | `License` | non | `@ManyToOne`, FK `fk_license_price_license` |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## Membership

`com.github.gcolin.membership.Membership` → table `membership`

Étend `TimedEntity`. Licence type par défaut : `"A"`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `user` | `creation_user` | `String(255)` | non | |
| `nrFfe` | `nr_ffe` | `String(255)` | non | |
| `lastname` | `lastname` | `String(255)` | oui | |
| `firstname` | `firstname` | `String(255)` | oui | |
| `birthDate` | `birthdate` | `String(255)` | oui | |
| `clubRef` | `club_ref` | `int` | non | |
| `status` | `status` | `MembershipStatus` (STRING) | non | `APPROVED`, `PENDING_APPROVAL`, `PENDING_CONFIRMATION`, `PAID`, `FREE` |
| `amountCents` | `amountcents` | `int` | non | |
| `licenseType` | `license_type` | `String(10)` | oui | |
| `season` | `season_id` | `ClubSeason` | oui | `@ManyToOne`, FK `fk_membership_season` |
| `payment` | `payment_id` | `Payment` | oui | `@ManyToOne`, FK `fk_membership_payment` |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## MembershipOption

`com.github.gcolin.membership.MembershipOption` → table `membership_option`

Étend `TimedEntity`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `optionType` | `option_type` | `MembershipOptionType` (STRING) | non | `COURSES`, `COURSES_VOLUNTEER`, `CLUB_CHAMPIONSHIP` |
| `optionValue` | `option_value` | `String(255)` | non | |
| `amountCents` | `amount_cents` | `Integer` | oui | |
| `accessRule` | `access_rule` | `MembershipOptionAccessRule` (STRING) | oui | défaut `ALL` |
| `license` | `license_id` | `License` | oui | `@ManyToOne`, FK `fk_membership_option_license` |
| `season` | `season_id` | `ClubSeason` | oui | `@ManyToOne`, FK `fk_membership_option_season` |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## MembershipOptionSubscription

`com.github.gcolin.membership.MembershipOptionSubscription` → table `membership_option_subscription`

Étend `TimedEntity`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `nrFfe` | `nr_ffe` | `String(255)` | oui | |
| `membership` | `membership_id` | `Membership` | non | `@ManyToOne` LAZY |
| `membershipOption` | `membership_option_id` | `MembershipOption` | non | `@ManyToOne` LAZY |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## MembershipOptionFile

`com.github.gcolin.membership.MembershipOptionFile` → table `membership_option_file`

Étend `TimedEntity`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `membershipOption` | `membership_option_id` | `MembershipOption` | non | `@ManyToOne` LAZY, FK `fk_membership_option_file_option` |
| `originalName` | `original_name` | `String(255)` | non | |
| `storedName` | `stored_name` | `String(255)` | non | |
| `contentType` | `content_type` | `String(120)` | oui | |
| `sizeBytes` | `size_bytes` | `Long` | oui | |
| `uploadedBy` | `uploaded_by` | `String(255)` | oui | |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## Relations principales

```
Organisation
  └── (filtre org) ClubSeason, License

EventGroup ──< Event >── EventCollection
               │  │
               │  └── EventInfo (1:1)
               │  └── EventOption (*)
               └── PlayerSubscription >── Payment
               └── PlayerPendingSubscription

EventCollection ── EventCollectionOption

ClubSeason ──< Membership >── Payment
           └── MembershipOption >── License
                                └── MembershipOptionSubscription
                                └── MembershipOptionFile

License ── LicensePrice
```

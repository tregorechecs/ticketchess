# Modèle de données — Ticket Chess

Inventaire des entités persistées (POJO + `AbstractDao` / JDBC) et de leurs colonnes SQL.

Source de vérité schéma : `src/main/resources/schema.sql`.

## Sommaire

| Entité | Table | Hérite de |
|--------|-------|-----------|
| [TimedEntity](#timedentity) | — (classe de base) | — |
| [Organisation](#organisation) | `organisation` | — |
| [UserAuthorization](#userauthorization) | `user_authorization` | — |
| [ClubSeason](#clubseason) | `club_season` | TimedEntity |
| [CustomPlayer](#customplayer) | `custom_player` | — |
| [EventGroup](#eventgroup) | `event_group` | — |
| [EventCollection](#eventcollection) | `big_event` | — |
| [EventCollectionOption](#eventcollectionoption) | `eventcollectionoption` | — |
| [Event](#event) | `event` | — |
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

`com.github.gcolin.platform.TimedEntity`

Champs communs renseignés à la création / mise à jour (`prePersist` / `preUpdate`).

| Champ | Colonne | Type | Notes |
|-------|---------|------|-------|
| `createdAt` | `created_at` | `LocalDateTime` | |
| `updatedAt` | `updated_at` | `LocalDateTime` | |

---

## Organisation

`com.github.gcolin.organisation.Organisation` → table `organisation`

Colonnes SQL réduites : identité d’hôte + blob de réglages. Les champs typés (nom, Stripe, OAuth, mail…) sont packés / dépackés via `OrganisationSettings` dans `settings_json`.

UK : `base_url`. Résolution multi-tenant par URL de base / hôte (`OrganisationDao`, `OrgScope`).

| Champ / clé | Stockage | Type | Notes |
|-------------|----------|------|-------|
| `id` | colonne `id` | `Integer` | PK, IDENTITY |
| `baseUrl` | colonne `base_url` | `String(500)` | UK, non null |
| `active` | colonne `active` | `boolean` | défaut `true` |
| `settingsJson` | colonne `settings_json` | `text` | JSON |
| `name` | `settings_json` → `org.name` | `String` | |
| `superUserEmail` | `settings_json` → `org.superUserEmail` | `String` | traité comme `ADMIN` sur l’org courante |
| Stripe / paiements | `stripe.*`, `payment.transfer.*` | | clés / simulation / moyens |
| OAuth | `oauth.*` | | SSO de l’organisation |
| Mail | `mail.USER_NAME`, `mail.PASSWORD` | | |

D’autres clés (facture, identité visuelle, features…) peuvent coexister dans `settings_json` sans champ Java dédié.

---

## UserAuthorization

`com.github.gcolin.auth.UserAuthorization` → table `user_authorization`

UK : `(email, role, scope_type, scope_id)`. Index sur `email` et `(email, role, scope_type, scope_id, active)`.

Pas de `org_id` : les grants sont globaux à l’instance (ou scopés événement / groupe).

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `email` | `email` | `String(320)` | non | normalisé lower-case |
| `role` | `role` | `RoleCode` | non | `ADMIN`, `TRESORIER`, `ENTRAINEUR`, `ARBITRE` |
| `scopeType` | `scope_type` | `AuthorizationScopeType` | non | `GLOBAL`, `EVENT`, `EVENT_GROUP` |
| `scopeId` | `scope_id` | `Integer` | oui | requis si scope ≠ `GLOBAL` |
| `active` | `active` | `boolean` | non | défaut `true` |
| `validUntil` | `valid_until` | `LocalDateTime` | oui | |
| `grantedBy` | `granted_by` | `String(320)` | oui | normalisé lower-case |

Voir aussi [Droits admin (tuiles)](DROITS_ADMIN.md).

---

## ClubSeason

`com.github.gcolin.club.ClubSeason` → table `club_season`

Étend `TimedEntity`. Multi-tenant via `org_id`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `orgId` | `org_id` | `Integer` | non | |
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

`OrgOwned`. Filtré par organisation courante.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `orgId` | `org_id` | `Integer` | non | multi-tenant |
| `name` | `name` | `String(255)` | non | |
| `shortname` | `shortname` | `String(255)` | non | |
| `events` | — | `List<Event>` | | relation logique (`event.event_group_id`) |

---

## EventCollection

`com.github.gcolin.event.EventCollection` → table `big_event`

`OrgOwned`. Filtré par organisation courante.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `orgId` | `org_id` | `Integer` | non | multi-tenant |
| `name` | `name` | `String(255)` | non | |
| `events` | — | `List<Event>` | | relation logique (`event.big_event_id`) |
| `maxSubscribe` | — | `Integer` | | transient |
| `nbSubscriptions` | — | `Integer` | | transient |
| `eventCount` | — | `Integer` | | transient |

---

## EventCollectionOption

`com.github.gcolin.event.EventCollectionOption` → table `eventcollectionoption`

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `eventCollection` | `big_event_id` | `EventCollection` | oui | FK |
| `optionType` | `option_type` | `EventCollectionOptionType` | non | `MAX_SUBSCRIPTIONS` |
| `value` | `option_value` | `String(255)` | non | |

---

## Event

`com.github.gcolin.event.Event` → table `event`

`OrgOwned`. Filtré par organisation courante. Index : `status_id`, `start_date`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `orgId` | `org_id` | `Integer` | non | multi-tenant |
| `name` | `name` | `String` | non | |
| `startDate` | `start_date` | `LocalDateTime` | non | |
| `endDate` | `end_date` | `LocalDateTime` | non | |
| `priceCents` | `pricecents` | `Long` | oui | `price` (BigDecimal) dérivé en Java |
| `youngPriceCents` | `youngpricecents` | `Long` | oui | `youngPrice` dérivé en Java |
| `status` | `status_id` | `EventStatus` (ORDINAL) | non | `DRAFT`, `ACTIVE`, `ONGOING`, `COMPLETED` |
| `eventType` | `event_type` | `EventType` (ORDINAL) | non | `STANDARD`, `RAPID`, `BLITZ` |
| `eventGroup` | `event_group_id` | `EventGroup` | oui | FK |
| `eventCollection` | `big_event_id` | `EventCollection` | oui | FK |
| `subscriptions` | — | `List<PlayerSubscription>` | | chargé à la demande (`EventDao`) |
| `description` | `description` | `TEXT` | oui | markdown ; chargé uniquement en détail/édition (pas dans les listes) |
| `eventOptions` | — | `Map<EventOptionType, EventOption>` | | transient / chargé à part |
| `players` / `nbSubscriptions` | — | | | transient |

---

## EventOption

`com.github.gcolin.event.EventOption` → table `eventoption`

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `optionType` | `option_type` | `EventOptionType` | non | `FFE_ID`, `FFE_PASSWORD`, `MAX_ELO`, `MIN_ELO`, `MAX_SUBSCRIPTIONS`, `ROUNDS`, `CADENCE`, `CLUB_REF`, `PAIRING`, `POINTAGE` |
| `event` | `event_id` | `Event` | oui | FK |
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
| `event` | `event_id` | `Event` | non | FK |
| `status` | `status` | `PlayerSubscriptionStatus` (ORDINAL) | non | `NOT_PAID`, `PAID`, `CANCELLED` |
| `payment` | `payment_id` | `Payment` | oui | FK |
| `amountCents` | `amountcents` | `Long` | oui | |
| `attendanceAt` | `attendance_at` | `LocalDateTime` | oui | pointage |
| `player` | — | `DisplayPlayer` | | transient |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## PlayerSubscriptionOption

`com.github.gcolin.registration.PlayerSubscriptionOption` → table `player_subscription_option`

Étend `TimedEntity`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `playerSubscription` | `player_subscription_id` | `PlayerSubscription` | non | FK |
| `amountCents` | `amountcents` | `Long` | oui | |
| `description` | `description` | `String(512)` | oui | |
| `payment` | `payment_id` | `Payment` | oui | FK |
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
| `event` | `event_id` | `Event` | non | FK |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## Payment

`com.github.gcolin.payment.Payment` → table `payment`

Étend `TimedEntity`. Colonnes SQL en camelCase minuscule (héritage EclipseLink).

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Long` | PK, IDENTITY | |
| `stripeSessionId` | `stripesessionid` | `String` | oui | UK |
| `stripeIntent` | `stripeintent` | `String` | oui | |
| `userEmail` | `useremail` | `String` | non | |
| `payerName` | `payername` | `String` | oui | |
| `payerAddress` | `payeraddress` | `String(1000)` | oui | |
| `donation` | `donation` | `boolean` | non | défaut `false` |
| `status` | `status` | `PaymentStatus` | oui | `PENDING`, `PAID`, `EXPIRED` |
| `type` | `type` | `PaymentType` | oui | `CARD`, `BANK_TRANSFER`, `FREE`, `CASH`, `CHEQUE` |
| `amountCents` | `amountcents` | `Long` | oui | `amount` (Double) dérivé en Java |
| `subscriptions` / `memberships` | — | listes | | relations logiques via `payment_id` |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## License

`com.github.gcolin.membership.License` → table `license`

Étend `TimedEntity`. UK : `(org_id, name)`. Multi-tenant via `org_id`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `orgId` | `org_id` | `Integer` | non | |
| `name` | `name` | `String(50)` | non | |
| `accessRule` | `access_rule` | `MembershipOptionAccessRule` | non | `ALL`, `YOUNG_ONLY`, `NON_YOUNG_ONLY`, `ADMIN` |
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
| `license` | `license_id` | `License` | non | FK |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## Membership

`com.github.gcolin.membership.Membership` → table `membership`

Étend `TimedEntity`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `user` | `creation_user` | `String(255)` | non | |
| `nrFfe` | `nr_ffe` | `String(255)` | non | |
| `lastname` | `lastname` | `String(255)` | oui | |
| `firstname` | `firstname` | `String(255)` | oui | |
| `birthDate` | `birthdate` | `String(255)` | oui | |
| `clubRef` | `club_ref` | `int` | non | |
| `status` | `status` | `MembershipStatus` | non | `APPROVED`, `PENDING_APPROVAL`, `PENDING_CONFIRMATION`, `PAID`, `FREE` |
| `amountCents` | `amountcents` | `int` | non | |
| `licenseType` | `license_type` | `String(10)` | oui | |
| `season` | `season_id` | `ClubSeason` | oui | FK |
| `payment` | `payment_id` | `Payment` | oui | FK |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## MembershipOption

`com.github.gcolin.membership.MembershipOption` → table `membership_option`

Étend `TimedEntity`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `optionType` | `option_type` | `MembershipOptionType` | non | `COURSES`, `COURSES_VOLUNTEER`, `CLUB_CHAMPIONSHIP` |
| `optionValue` | `option_value` | `String(255)` | non | |
| `amountCents` | `amount_cents` | `Integer` | oui | |
| `accessRule` | `access_rule` | `MembershipOptionAccessRule` | oui | défaut `ALL` |
| `license` | `license_id` | `License` | oui | FK |
| `season` | `season_id` | `ClubSeason` | oui | FK |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## MembershipOptionSubscription

`com.github.gcolin.membership.MembershipOptionSubscription` → table `membership_option_subscription`

Étend `TimedEntity`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `nrFfe` | `nr_ffe` | `String(255)` | oui | |
| `membership` | `membership_id` | `Membership` | non | FK |
| `membershipOption` | `membership_option_id` | `MembershipOption` | non | FK |
| + TimedEntity | | | | `created_at`, `updated_at` |

---

## MembershipOptionFile

`com.github.gcolin.membership.MembershipOptionFile` → table `membership_option_file`

Étend `TimedEntity`.

| Champ | Colonne | Type | Nullable | Notes |
|-------|---------|------|----------|-------|
| `id` | `id` | `Integer` | PK, IDENTITY | |
| `membershipOption` | `membership_option_id` | `MembershipOption` | non | FK |
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
  ├── settings_json (identité, Stripe, OAuth, mail, facture…)
  └── (org_id) ClubSeason, License, Event, EventGroup, EventCollection

EventGroup ──< Event >── EventCollection
               │  │
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

package com.github.gcolin.club;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClubMustacheView {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private ClubMustacheView() {}

    public static void enrichSeasonAdmin(Map<String, Object> model, List<ClubSeason> seasons) {
        List<ClubSeason> safe = seasons != null ? seasons : List.of();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ClubSeason season : safe) {
            rows.add(toSeasonRow(season));
        }
        model.put("seasonRows", rows);
        model.put("hasSeasons", !rows.isEmpty());
    }

    public static void enrichSeasonEdit(Map<String, Object> model, ClubSeason season) {
        if (season == null) {
            season = new ClubSeason();
        }
        model.put("seasonIsEdit", season.getId() != null);
        model.put("seasonName", season.getName() != null ? season.getName() : "");
        model.put("seasonCurrent", season.isCurrent());
        if (season.getStartDate() != null) {
            model.put("startDateValue", ISO_DATE.format(season.getStartDate()));
        }
        if (season.getEndDate() != null) {
            model.put("endDateValue", ISO_DATE.format(season.getEndDate()));
        }
        if (season.getId() != null) {
            model.put("formAction", "admin/seasons/" + season.getId());
            model.put("titleKey", "page.seasonEdit");
        } else {
            model.put("formAction", "admin/seasons");
            model.put("titleKey", "page.seasonNew");
        }
    }

    private static Map<String, Object> toSeasonRow(ClubSeason season) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", season.getId());
        row.put("name", season.getName() != null ? season.getName() : "");
        row.put("current", season.isCurrent());
        if (season.getStartDate() != null) {
            row.put("startDate", ISO_DATE.format(season.getStartDate()));
        }
        if (season.getEndDate() != null) {
            row.put("endDate", ISO_DATE.format(season.getEndDate()));
        }
        return row;
    }
}

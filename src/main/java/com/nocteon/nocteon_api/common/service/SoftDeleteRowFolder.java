package com.nocteon.nocteon_api.common.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Folds the rows of a trash-bin native query into one response per entity.
 *
 * <p>Those queries select the entity's own columns and then every translation's
 * {@code language, name, description}, so a row is <em>per entity per language</em>: an item with
 * an English and an Arabic translation comes back twice. The {@code GROUP BY} in those queries
 * cannot collapse it, because the translation columns are in the select list and in the grouping
 * key.
 *
 * <p>Mapping each row to a response directly — which is the obvious thing to write — therefore
 * yields one response per language, and a trash bin shows the same deleted category once per
 * translation it has. Worse than a cosmetic duplicate: {@code totalElements} counts rows rather
 * than entities, so paging splits one item's translations across two pages, and an item can appear
 * on page 1 and page 2 at once.
 *
 * <p>Folding on id fixes both. The entity is built once and its translations accumulate onto it, so
 * the response count equals the entity count and each page holds whole items.
 *
 * <p>Insertion order is preserved by {@link LinkedHashMap}, so the listing keeps the order the
 * query returned rather than whatever the map implementation happens to do — the caller's sort
 * comparator then runs over whole entities instead of interleaved rows.
 *
 * @param <R> the dashboard response type
 */
public final class SoftDeleteRowFolder {

    private SoftDeleteRowFolder() {
    }

    /**
     * Build one response per distinct id, appending each row's translation to the response for that
     * id.
     *
     * @param rows rows from {@code findAllDeletedNative} / {@code findDeletedBySearchNative}
     * @param index reads the id out of a row
     * @param builder builds the entity's response, with its translation list left empty
     * @param translation reads one translation out of the row and attaches it
     * @return one response per distinct id, in first-seen order
     */
    public static <R> List<R> fold(List<Object[]> rows,
            Function<Object[], Long> index,
            Function<Object[], R> builder,
            TranslationAttacher<R> translation) {

        Map<Long, R> byId = new LinkedHashMap<>();
        for (Object[] row : rows) {
                Long id = index.apply(row);
                R response = byId.get(id);
                if (response == null) {
                        response = builder.apply(row);
                        byId.put(id, response);
                }
                translation.attach(response, row);
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * Reads the single translation carried by a row and attaches it to the owning response.
     *
     * @param <R> the dashboard response type
     */
    @FunctionalInterface
    public interface TranslationAttacher<R> {
        void attach(R response, Object[] row);
    }
}
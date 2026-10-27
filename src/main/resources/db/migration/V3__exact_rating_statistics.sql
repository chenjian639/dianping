-- Keep the public average_rating field, but derive every update from exact totals.
ALTER TABLE restaurant ADD COLUMN rating_sum BIGINT NOT NULL DEFAULT 0;
UPDATE restaurant r
LEFT JOIN (
    SELECT restaurant_id, SUM(rating) AS rating_sum, COUNT(*) AS review_count
    FROM review GROUP BY restaurant_id
) s ON s.restaurant_id = r.id
SET r.rating_sum = COALESCE(s.rating_sum, 0),
    r.review_count = COALESCE(s.review_count, 0),
    r.average_rating = CASE WHEN COALESCE(s.review_count, 0) = 0 THEN 0.0
                           ELSE ROUND(s.rating_sum / s.review_count, 1) END;

-- Old cache entries may contain statistics computed with the previous algorithm.
INSERT INTO outbox_event(event_id, event_type, aggregate_id, payload, status, retry_count)
SELECT UUID(), 'RESTAURANT_CACHE_EVICT', id, CAST(id AS CHAR), 'PENDING', 0
FROM restaurant;

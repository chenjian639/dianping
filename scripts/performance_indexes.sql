USE dodo_food;

ALTER TABLE restaurant
    ADD INDEX idx_restaurant_status_created
        (status, created_at DESC, id DESC),
    ADD FULLTEXT INDEX ft_restaurant_name_ngram (name) WITH PARSER ngram;

ALTER TABLE dish
    ADD INDEX idx_dish_restaurant_deleted_status_created
        (restaurant_id, deleted, status, created_at DESC, id DESC);

ALTER TABLE orders
    ADD INDEX idx_orders_user_created
        (user_id, created_at DESC, id DESC),
    ADD INDEX idx_orders_restaurant_status_created
        (restaurant_id, status, created_at DESC, id DESC);

ALTER TABLE review
    ADD INDEX idx_review_user_created
        (user_id, created_at DESC),
    ADD INDEX idx_review_restaurant_created
        (restaurant_id, created_at DESC),
    ADD INDEX idx_review_restaurant_rating
        (restaurant_id, rating);

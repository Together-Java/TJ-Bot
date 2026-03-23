ALTER TABLE metric_events ADD COLUMN dimensions TEXT;

UPDATE metric_events
SET event = 'code_action',
    dimensions = json_object('name', SUBSTR(event, LENGTH('code_action-') + 1))
WHERE event LIKE 'code_action-%';

UPDATE metric_events
SET event = 'autoprune_helper',
    dimensions = json_object('role', SUBSTR(event, LENGTH('autoprune_helper-') + 1))
WHERE event LIKE 'autoprune_helper-%';

UPDATE metric_events
SET event = 'help-category',
    dimensions = json_object('category', SUBSTR(event, LENGTH('help-category-') + 1))
WHERE event LIKE 'help-category-%';

UPDATE metric_events
SET event = 'tag',
    dimensions = json_object('id', SUBSTR(event, LENGTH('tag-') + 1))
WHERE event LIKE 'tag-%';

UPDATE metric_events
SET event = 'top_helper',
    dimensions = json_object('userId', CAST(SUBSTR(event, LENGTH('top_helper-') + 1) AS INTEGER))
WHERE event LIKE 'top_helper-%';
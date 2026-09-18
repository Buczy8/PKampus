-- Remove thematic room regulatory type; capacity and hours are configured explicitly by ADS.
ALTER TABLE thematic_rooms DROP CONSTRAINT IF EXISTS thematic_rooms_room_type_check;
ALTER TABLE thematic_rooms DROP COLUMN IF EXISTS room_type;

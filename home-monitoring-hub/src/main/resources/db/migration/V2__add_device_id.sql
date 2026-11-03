ALTER TABLE readings ADD COLUMN device_id VARCHAR(64);
UPDATE readings SET device_id = 'esp32-1';
ALTER TABLE readings ALTER COLUMN device_id SET NOT NULL;

CREATE INDEX idx_readings_device_timestamp ON readings (device_id, timestamp);
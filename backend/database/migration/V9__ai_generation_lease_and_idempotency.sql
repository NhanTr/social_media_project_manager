-- Migration V9: Thêm hỗ trợ Idempotency, Distributed Worker Lease, Mock Charge và Widget Thumbnail cho AI Generations

ALTER TABLE ai_generations
  ADD COLUMN client_request_id VARCHAR(64),
  ADD COLUMN lease_owner VARCHAR(64),
  ADD COLUMN leased_until TIMESTAMPTZ,
  ADD COLUMN retry_count INT NOT NULL DEFAULT 0,
  ADD COLUMN mock_charge_id VARCHAR(64),
  ADD COLUMN widget_thumbnail_url TEXT;

-- Đảm bảo Idempotency: 1 user không thể tạo 2 job với cùng 1 client_request_id
CREATE UNIQUE INDEX uq_ai_generations_client_request
  ON ai_generations(user_id, client_request_id)
  WHERE client_request_id IS NOT NULL;

-- Index hỗ trợ worker quét và lease các job PENDING hoặc PROCESSING quá hạn (expired lease)
CREATE INDEX idx_ai_generations_worker_lease
  ON ai_generations(status, leased_until);

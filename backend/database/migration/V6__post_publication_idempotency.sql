-- Apply after dev V1-V4 and V5 media lifecycle; never rewrite the separate S2-01 V3 history.
ALTER TABLE posts
  ADD COLUMN client_request_id UUID,
  ADD COLUMN request_fingerprint VARCHAR(64),
  ADD COLUMN upload_id BIGINT REFERENCES media_uploads(id);
CREATE UNIQUE INDEX uq_posts_client_request ON posts (user_id, client_request_id)
  WHERE client_request_id IS NOT NULL;
CREATE UNIQUE INDEX uq_posts_upload ON posts (upload_id) WHERE upload_id IS NOT NULL;
ALTER TABLE posts ADD CONSTRAINT chk_posts_publication_binding CHECK (
  (client_request_id IS NULL AND request_fingerprint IS NULL AND upload_id IS NULL)
  OR (client_request_id IS NOT NULL AND request_fingerprint IS NOT NULL AND upload_id IS NOT NULL)
);

ALTER TABLE media_uploads
  ADD COLUMN media_url TEXT,
  ADD COLUMN file_size_bytes BIGINT,
  ADD COLUMN media_width INT,
  ADD COLUMN media_height INT,
  ADD COLUMN duration_seconds INT,
  ADD COLUMN consumed_post_id BIGINT REFERENCES posts(id);
CREATE UNIQUE INDEX uq_media_uploads_consumed_post ON media_uploads (consumed_post_id)
  WHERE consumed_post_id IS NOT NULL;

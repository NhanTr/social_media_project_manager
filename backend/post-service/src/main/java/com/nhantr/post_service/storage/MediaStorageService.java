package com.nhantr.post_service.storage;

import com.nhantr.common.api.ApiException;
import com.nhantr.post_service.entity.MediaUpload;
import com.nhantr.post_service.entity.Post;
import com.nhantr.post_service.repository.MediaUploadRepository;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.time.Instant;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaStorageService {
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    private static final long IMAGE_LIMIT = 10L * 1024 * 1024;
    private static final long GIF_LIMIT = 20L * 1024 * 1024;
    private static final int UPLOAD_SESSION_TTL_SECONDS = 600;

    private final MinioClient minio;
    /** Client ký presigned URL bằng endpoint PUBLIC để client thật gọi được. */
    private final MinioClient publicMinio;
    private final String bucket;
    private final String publicUrl;
    private final MediaUploadRepository uploads;
    private final MediaCleanupService cleanup;

    public MediaStorageService(
            MinioClient minio,
            @Value("${storage.bucket}") String bucket,
            @Value("${storage.public-url}") String publicUrl,
            @Value("${storage.access-key}") String accessKey,
            @Value("${storage.secret-key}") String secretKey,
            MediaUploadRepository uploads,
            MediaCleanupService cleanup
    ) {
        this.minio = minio;
        this.bucket = bucket;
        this.publicUrl = publicUrl.replaceAll("/$", "");
        this.uploads = uploads;
        this.cleanup = cleanup;
        this.publicMinio = MinioClient.builder()
                .endpoint(this.publicUrl)
                .credentials(accessKey, secretKey)
                // Bỏ qua getBucketLocation (call mạng) khi ký presigned URL
                .region("us-east-1")
                .build();
    }

    @PostConstruct
    void initializeBucket() {
        try {
            if (!minio.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            String policy = """
                    {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},
                    "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}
                    """.formatted(bucket).replace("\n", "");
            minio.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot initialize media bucket.", exception);
        }
    }

    public UploadResponse upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("MEDIA_REQUIRED", "A media file is required.");
        }
        String contentType = file.getContentType();
        Post.MediaType mediaType;
        long maxSize;
        if ("image/gif".equals(contentType)) {
            mediaType = Post.MediaType.GIF;
            maxSize = GIF_LIMIT;
        } else if (IMAGE_TYPES.contains(contentType)) {
            mediaType = Post.MediaType.IMAGE;
            maxSize = IMAGE_LIMIT;
        } else {
            throw ApiException.badRequest("MEDIA_TYPE_UNSUPPORTED", "Only JPEG, PNG and GIF files are supported.");
        }
        if (file.getSize() > maxSize) {
            throw ApiException.badRequest("MEDIA_TOO_LARGE", "Media file exceeds the allowed size.");
        }

        Integer durationSeconds = null;
        Integer mediaWidth = null;
        Integer mediaHeight = null;
        if (mediaType == Post.MediaType.GIF) {
            try {
                GifMeta meta = readGifMeta(file.getInputStream());
                durationSeconds = meta.durationSeconds();
                mediaWidth = meta.width();
                mediaHeight = meta.height();
            } catch (java.io.IOException unreadable) {
                throw ApiException.badRequest("GIF_INVALID", "GIF metadata cannot be read.");
            }
        } else {
            try {
                BufferedImage image = ImageIO.read(file.getInputStream());
                if (image != null) {
                    mediaWidth = image.getWidth();
                    mediaHeight = image.getHeight();
                }
            } catch (Exception ignored) {
                // Metadata là phụ trợ; upload hợp lệ vẫn được chấp nhận nếu không đọc được kích thước
            }
        }
        if (durationSeconds != null && durationSeconds > 5) {
            throw ApiException.badRequest("GIF_TOO_LONG", "GIF duration must not exceed 5 seconds.");
        }

        String extension = extensionFor(contentType);
        String objectName = "users/%d/%s.%s".formatted(userId, UUID.randomUUID(), extension);
        try (InputStream input = file.getInputStream()) {
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(input, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Media upload failed.", exception);
        }
        return new UploadResponse(
                mediaType,
                publicUrl + "/" + bucket + "/" + objectName,
                file.getSize(),
                durationSeconds,
                mediaWidth,
                mediaHeight
        );
    }

    private GifMeta readGifMeta(InputStream input) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) throw ApiException.badRequest("GIF_INVALID", "GIF metadata cannot be read.");
            ImageReader reader = readers.next();
            reader.setInput(stream);
            int totalHundredths = 0;
            for (int i = 0; i < reader.getNumImages(true); i++) {
                var metadata = reader.getImageMetadata(i);
                var tree = (IIOMetadataNode) metadata.getAsTree("javax_imageio_gif_image_1.0");
                var nodes = tree.getElementsByTagName("GraphicControlExtension");
                if (nodes.getLength() > 0) {
                    totalHundredths += Integer.parseInt(((IIOMetadataNode) nodes.item(0)).getAttribute("delayTime"));
                }
            }
            Integer width = reader.getWidth(0) > 0 ? reader.getWidth(0) : null;
            Integer height = reader.getHeight(0) > 0 ? reader.getHeight(0) : null;
            reader.dispose();
            return new GifMeta((int) Math.ceil(totalHundredths / 100.0), width, height);
        } catch (ApiException rejected) {
            throw rejected;
        } catch (Exception exception) {
            throw ApiException.badRequest("GIF_INVALID", "GIF metadata cannot be read.");
        }
    }

    private Post.MediaType validateContentType(String contentType) {
        if ("image/gif".equals(contentType)) return Post.MediaType.GIF;
        if (IMAGE_TYPES.contains(contentType)) return Post.MediaType.IMAGE;
        throw ApiException.badRequest("MEDIA_TYPE_UNSUPPORTED", "Only JPEG, PNG and GIF files are supported.");
    }

    private long sizeLimitFor(Post.MediaType mediaType) {
        return mediaType == Post.MediaType.GIF ? GIF_LIMIT : IMAGE_LIMIT;
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> "gif";
        };
    }

    private String contentTypeFromKey(String objectKey) {
        if (objectKey.endsWith(".jpg") || objectKey.endsWith(".jpeg")) return "image/jpeg";
        if (objectKey.endsWith(".png")) return "image/png";
        if (objectKey.endsWith(".gif")) return "image/gif";
        throw ApiException.badRequest("MEDIA_TYPE_UNSUPPORTED", "Only JPEG, PNG and GIF files are supported.");
    }

    private record GifMeta(Integer durationSeconds, Integer width, Integer height) {}

    // ===== Presigned upload session (LC-S2-01) =====

    /** Tạo session presigned PUT; client upload trực tiếp tới MinIO rồi gọi finalize. */
    public UploadSessionResponse createUploadSession(Long userId, UploadSessionRequest request) {
        String contentType = request.contentType() == null ? "" : request.contentType().trim().toLowerCase(Locale.ROOT);
        Post.MediaType mediaType = validateContentType(contentType);
        if (request.declaredSizeBytes() != null && request.declaredSizeBytes() > sizeLimitFor(mediaType)) {
            throw ApiException.badRequest("MEDIA_TOO_LARGE", "Media file exceeds the allowed size.");
        }
        String objectKey = "users/%d/uploads/%s.%s".formatted(userId, UUID.randomUUID(), extensionFor(contentType));
        MediaUpload session = uploads.save(new MediaUpload(
                userId, objectKey, Instant.now().plusSeconds(UPLOAD_SESSION_TTL_SECONDS)
        ));
        try {
            String presignedUrl = publicMinio.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(UPLOAD_SESSION_TTL_SECONDS)
                            .build()
            );
            return new UploadSessionResponse(session.getId(), presignedUrl, objectKey, UPLOAD_SESSION_TTL_SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot create presigned upload URL.", exception);
        }
    }

    /**
     * Finalize: xác minh object đã upload, kiểm tra MIME/size/GIF duration,
     * trích metadata (width/height) và đánh dấu session FINALIZED.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public UploadResponse finalizeUpload(Long userId, Long uploadId) {
        MediaUpload session = uploads.lockById(uploadId)
                .orElseThrow(() -> ApiException.notFound("UPLOAD_SESSION_NOT_FOUND", "Upload session not found."));
        if (!userId.equals(session.getUserId())) {
            throw ApiException.forbidden("UPLOAD_FORBIDDEN", "Upload session belongs to another user.");
        }
        if (session.getStatus() == MediaUpload.Status.FINALIZED && session.getFileSizeBytes() != null) {
            return finalizedResponse(session);
        }
        if (session.getStatus() != MediaUpload.Status.UPLOADING && session.getStatus() != MediaUpload.Status.FINALIZED) {
            throw ApiException.conflict("UPLOAD_SESSION_CLOSED", "Upload session is no longer active.");
        }
        if (session.getStatus() == MediaUpload.Status.UPLOADING && session.getExpiresAt().isBefore(Instant.now())) {
            session.markExpired();
            uploads.save(session);
            cleanup.enqueue(session.getObjectKey());
            throw ApiException.conflict("UPLOAD_SESSION_EXPIRED", "Upload session has expired.");
        }

        StatObjectResponse stat;
        try {
            stat = minio.statObject(StatObjectArgs.builder()
                    .bucket(bucket).object(session.getObjectKey()).build());
        } catch (Exception missing) {
            throw ApiException.badRequest("MEDIA_NOT_UPLOADED", "Media has not been uploaded for this session.");
        }

        String contentType = contentTypeFromKey(session.getObjectKey());
        Post.MediaType mediaType = validateContentType(contentType);
        if (stat.size() <= 0 || stat.size() > sizeLimitFor(mediaType)) {
            session.markExpired();
            uploads.save(session);
            cleanup.enqueue(session.getObjectKey());
            throw ApiException.badRequest("MEDIA_TOO_LARGE", "Media file exceeds the allowed size.");
        }

        Integer durationSeconds = null;
        Integer mediaWidth = null;
        Integer mediaHeight = null;
        try (InputStream input = minio.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(session.getObjectKey()).matchETag(stat.etag()).build())) {
            if (mediaType == Post.MediaType.GIF) {
                GifMeta meta = readGifMeta(input);
                durationSeconds = meta.durationSeconds();
                mediaWidth = meta.width();
                mediaHeight = meta.height();
                if (durationSeconds != null && durationSeconds > 5) {
                    throw ApiException.badRequest("GIF_TOO_LONG", "GIF duration must not exceed 5 seconds.");
                }
            } else {
                BufferedImage image = ImageIO.read(input);
                if (image == null) {
                    throw ApiException.badRequest("MEDIA_INVALID", "Media content cannot be read as an image.");
                }
                mediaWidth = image.getWidth();
                mediaHeight = image.getHeight();
            }
        } catch (ApiException rejected) {
            throw rejected;
        } catch (Exception exception) {
            throw ApiException.badRequest("MEDIA_INVALID", "Media content cannot be read.");
        }

        // The client PUT URL remains valid until its TTL. Publish a separate object that it cannot overwrite.
        String uploadedObjectKey = session.getObjectKey();
        try {
            minio.copyObject(CopyObjectArgs.builder().bucket(bucket).object(session.publishedObjectKey())
                    .source(CopySource.builder().bucket(bucket).object(uploadedObjectKey).matchETag(stat.etag()).build())
                    .build());
        } catch (Exception exception) {
            throw ApiException.conflict("MEDIA_CHANGED", "Media changed during finalization; retry the upload.");
        }
        session.usePublishedObjectKey();
        cleanup.enqueue(uploadedObjectKey, session.getExpiresAt().plusSeconds(60));
        session.markFinalized(mediaType.name(), publicUrl + "/" + bucket + "/" + session.getObjectKey(),
                stat.size(), mediaWidth, mediaHeight, durationSeconds);
        uploads.save(session);
        return finalizedResponse(session);
    }

    private UploadResponse finalizedResponse(MediaUpload session) {
        return new UploadResponse(Post.MediaType.valueOf(session.getMediaType()), session.getMediaUrl(),
                session.getFileSizeBytes(), session.getDurationSeconds(), session.getMediaWidth(), session.getMediaHeight());
    }

    public record UploadResponse(
            Post.MediaType mediaType,
            String mediaUrl,
            long fileSizeBytes,
            Integer durationSeconds,
            Integer mediaWidth,
            Integer mediaHeight
    ) {}

    public record UploadSessionRequest(@NotBlank String contentType, @Positive Long declaredSizeBytes) {}

    public record UploadSessionResponse(Long uploadId, String presignedUrl, String objectKey, long expiresInSeconds) {}
}

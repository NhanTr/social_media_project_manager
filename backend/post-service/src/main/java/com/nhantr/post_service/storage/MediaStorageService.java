package com.nhantr.post_service.storage;

import com.nhantr.common.api.ApiException;
import com.nhantr.post_service.entity.Post;
import io.minio.*;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaStorageService {
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png");
    private static final long IMAGE_LIMIT = 10L * 1024 * 1024;
    private static final long GIF_LIMIT = 20L * 1024 * 1024;

    private final MinioClient minio;
    private final String bucket;
    private final String publicUrl;

    public MediaStorageService(
            MinioClient minio,
            @Value("${storage.bucket}") String bucket,
            @Value("${storage.public-url}") String publicUrl
    ) {
        this.minio = minio;
        this.bucket = bucket;
        this.publicUrl = publicUrl.replaceAll("/$", "");
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

        Integer durationSeconds = mediaType == Post.MediaType.GIF ? gifDuration(file) : null;
        if (durationSeconds != null && durationSeconds > 5) {
            throw ApiException.badRequest("GIF_TOO_LONG", "GIF duration must not exceed 5 seconds.");
        }

        String extension = switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            default -> "gif";
        };
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
                durationSeconds
        );
    }

    private Integer gifDuration(MultipartFile file) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(file.getInputStream())) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) return null;
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
            reader.dispose();
            return (int) Math.ceil(totalHundredths / 100.0);
        } catch (Exception exception) {
            throw ApiException.badRequest("GIF_INVALID", "GIF metadata cannot be read.");
        }
    }

    public record UploadResponse(Post.MediaType mediaType, String mediaUrl, long fileSizeBytes, Integer durationSeconds) {}
}

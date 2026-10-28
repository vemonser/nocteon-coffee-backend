package com.nocteon.nocteon_api.common.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.common.exception.image.ImageUploadException;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidImageException;
import com.nocteon.nocteon_api.common.exception.upload.ImageSizeExceededException;
import com.nocteon.nocteon_api.product.enums.MediaType;

import lombok.RequiredArgsConstructor;

/**
 * Uploads one image for a rich-text body (the journal editor and the product story).
 *
 * <p>Shared by both editors so the rules cannot drift: a 5 MB cap, an explicit format allowlist
 * and magic-byte verification. The client's Content-Type is a claim, not proof.
 */
@Service
@RequiredArgsConstructor
public class RichTextImageService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    private final LookupServiceHelper helper;

    public String upload(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImageException();
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ImageSizeExceededException();
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new InvalidImageException();
        }
        try (InputStream in = file.getInputStream()) {
            if (!looksLikeImage(in.readNBytes(12))) {
                throw new InvalidImageException();
            }
        } catch (IOException e) {
            throw new ImageUploadException();
        }
        String url = helper.uploadMedia(file, folder, MediaType.IMAGE);
        if (url == null || url.isBlank()) {
            throw new ImageUploadException();
        }
        return url;
    }

    private static boolean looksLikeImage(byte[] header) {
        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF) {
            return true; // JPEG
        }
        if (header.length >= 8
                && (header[0] & 0xFF) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
            return true; // PNG
        }
        if (header.length >= 6 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F') {
            return true; // GIF
        }
        return header.length >= 12
                && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
    }
}

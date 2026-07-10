package com.nocteon.nocteon_api.common.exception;

import java.util.List;
import java.util.Locale;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.ApiFieldError;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

        private final MessageSource messageSource;

        // ─── 400: Bad Request ──────────────────────────────────────────────────

        @ExceptionHandler(MissingServletRequestPartException.class)
        public ResponseEntity<ApiResponse<Void>> handleMissingPart(MissingServletRequestPartException ex) {
                log.warn("Missing request part: {}", ex.getRequestPartName());
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage(
                                "error.missing.part",
                                new Object[] { ex.getRequestPartName() },
                                "Required part '" + ex.getRequestPartName()
                                                + "' is missing. Expected: multipart/form-data",
                                locale);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(message, null));
        }

        @ExceptionHandler(MissingServletRequestParameterException.class)
        public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage(
                                "error.missing.parameter",
                                new Object[] { ex.getParameterName() },
                                "Required parameter '" + ex.getParameterName() + "' is missing",
                                locale);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(message, null));
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
                log.warn("Cannot read request body: {}", ex.getMessage());
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage(
                                "error.invalid.body",
                                null,
                                "Cannot read request body. Expected: multipart/form-data with 'data' and optional 'image' parts",
                                locale);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(message, null));
        }

        @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
        public ResponseEntity<ApiResponse<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
                log.warn("Unsupported media type: {}", ex.getContentType());
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage(
                                "error.unsupported.media.type",
                                new Object[] { ex.getContentType() },
                                "Content-Type '" + ex.getContentType()
                                                + "' not supported. Expected: multipart/form-data or application/json",
                                locale);
                return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                                .body(ApiResponse.error(message, null));
        }

        @ExceptionHandler(MultipartException.class)
        public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex) {
                log.warn("Multipart error: {}", ex.getMessage());
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage(
                                "error.multipart.invalid",
                                null,
                                "Invalid multipart request. Check that 'data' part contains valid JSON",
                                locale);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(message, null));
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(ex.getMessage(), null));
        }

        // ─── 400: Validation ───────────────────────────────────────────────────

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage("error.validation.failed", null, locale);

                List<ApiFieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                                .map(err -> ApiFieldError.builder()
                                                .field(err.getField())
                                                .message(err.getDefaultMessage())
                                                .build())
                                .toList();

                return ResponseEntity.badRequest()
                                .body(ApiResponse.error(message, errors));
        }

        // ─── 401/403: Auth ──────────────────────────────────────────────────────

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage("error.authentication.required", null, locale);

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(ApiResponse.error(message, null));
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage("error.access.denied", null, locale);

                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                                .body(ApiResponse.error(message, null));
        }

        // ─── 404: Not Found ─────────────────────────────────────────────────────

        @ExceptionHandler(NoResourceFoundException.class)
        public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error("Resource not found", null));
        }

        // ─── 409: Conflict ─────────────────────────────────────────────────────

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
                        DataIntegrityViolationException ex) {
                String message = ex.getRootCause() != null
                                ? ex.getRootCause().getMessage()
                                : ex.getMessage();
                String userMessage;
                if (message != null && message.contains("slug")) {
                        userMessage = "error.duplicate.slug";
                } else if (message != null && message.contains("uk_category_name_language")) {
                        userMessage = "error.duplicate.translation";
                } else {
                        userMessage = "error.duplicate.entry";
                }

                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error(userMessage, null));
        }

        // ─── Custom API Exceptions ─────────────────────────────────────────────

        @ExceptionHandler(BaseApiException.class)
        public ResponseEntity<ApiResponse<Void>> handleBaseApiException(BaseApiException ex) {
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage(
                                ex.getMessageKey(),
                                ex.getMessageArgs(),
                                ex.getMessageKey(), // fallback
                                locale);
                return ResponseEntity.status(ex.getStatus())
                                .body(ApiResponse.error(message, null));
        }

        // ─── 500: Catch-all (log and return safe error) ───────────────────────

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
                log.error("Unexpected error occurred", ex);
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage("error.unexpected", null, locale);

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error(message, null));
        }
}
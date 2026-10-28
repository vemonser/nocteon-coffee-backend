package com.nocteon.nocteon_api.common.exception;

import java.util.List;
import java.util.Locale;

import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import jakarta.validation.ConstraintViolationException;
import jakarta.persistence.OptimisticLockException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.exception.invalid.FieldValidationException;
import com.nocteon.nocteon_api.common.exception.invalid.JournalCategoryInUseException;
import com.nocteon.nocteon_api.common.exception.product.OptionInUseException;
import com.nocteon.nocteon_api.common.exception.product.OptionValueInUseException;
import com.nocteon.nocteon_api.common.exception.product.ValueExistsException;
import com.nocteon.nocteon_api.common.exception.product.OptionNotApplicableException;
import com.nocteon.nocteon_api.common.dto.ApiFieldError;

import jakarta.validation.ConstraintViolation;
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
                                "Cannot read request body",
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
                                                + "' not supported",
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
                                "Invalid multipart request",
                                locale);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(message, null));
        }

        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
                log.warn("File upload size exceeded: {}", ex.getMessage());
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage(
                                "error.image.size.exceeded",
                                null,
                                "File size exceeds the maximum allowed limit",
                                locale);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(message, null));
        }

        @ExceptionHandler(MethodArgumentTypeMismatchException.class)
        public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage(
                                "error.missing.parameter",
                                new Object[] { ex.getName() },
                                "Invalid value for parameter '" + ex.getName() + "'",
                                locale);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(ApiResponse.error(message, null));
        }

        // ─── 400: Validation ───────────────────────────────────────────────────

        @ExceptionHandler(BindException.class)
        public ResponseEntity<ApiResponse<Void>> handleBindException(BindException ex) {
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

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage("error.validation.failed", null, locale);

                List<ApiFieldError> errors = ex.getConstraintViolations().stream()
                                .map(violation -> ApiFieldError.builder()
                                                .field(extractFieldPath(violation))
                                                .message(violation.getMessage())
                                                .build())
                                .toList();

                return ResponseEntity.badRequest()
                                .body(ApiResponse.error(message, errors));
        }

        private String extractFieldPath(ConstraintViolation<?> violation) {
                String path = violation.getPropertyPath().toString();
                int lastDot = path.lastIndexOf('.');
                return lastDot >= 0 ? path.substring(lastDot + 1) : path;
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
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage("error.unexpected", null, locale);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(ApiResponse.error(message, null));
        }

        // ─── 405: Method Not Allowed ────────────────────────────────────────────

        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
                        HttpRequestMethodNotSupportedException ex) {
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage("error.method.not.allowed",
                                new Object[] { ex.getMethod() },
                                "HTTP method not supported", locale);
                return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                                .body(ApiResponse.error(message, null));
        }

        // ─── 409: Conflict ─────────────────────────────────────────────────────

        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
                        DataIntegrityViolationException ex) {
                Locale locale = LocaleContextHolder.getLocale();
                String rootMessage = ex.getRootCause() != null
                                ? ex.getRootCause().getMessage()
                                : ex.getMessage();
                String messageKey;
                if (rootMessage != null
                                && rootMessage.toLowerCase(java.util.Locale.ROOT).contains("foreign key")) {
                        // A hard delete refused because something still references the row — most
                        // commonly a permanent delete of a category, origin or farm that products
                        // still point at. This must be checked before the slug/translation branches:
                        // a foreign-key message names tables and constraints, which can contain
                        // those words for unrelated reasons.
                        messageKey = "error.delete.referenced";
                } else if (rootMessage != null && rootMessage.contains("slug")) {
                        messageKey = "error.duplicate.slug";
                } else {
                        messageKey = "error.duplicate.entry";
                }

                String message = messageSource.getMessage(messageKey, null, messageKey, locale);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                                .body(ApiResponse.error(message, null));
        }

        // ─── Custom API Exceptions ─────────────────────────────────────────────

        @ExceptionHandler(JournalCategoryInUseException.class)
        public ResponseEntity<ApiResponse<Object>> handleJournalCategoryInUse(
                        JournalCategoryInUseException ex) {
                java.util.Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage(
                                ex.getMessageKey(), ex.getMessageArgs(), ex.getMessageKey(), locale);
                ApiResponse<Object> body = ApiResponse.builder()
                                .success(false)
                                .code("JOURNAL_CATEGORY_IN_USE")
                                .message(message)
                                .data(java.util.Map.of("postCount", ex.getPostCount()))
                                .timestamp(java.time.Instant.now())
                                .build();
                return ResponseEntity.status(ex.getStatus()).body(body);
        }

        @ExceptionHandler(OptionValueInUseException.class)
        public ResponseEntity<ApiResponse<Object>> handleOptionValueInUse(OptionValueInUseException ex) {
                ApiResponse<Object> body = ApiResponse.builder()
                                .success(false)
                                .code("OPTION_VALUE_IN_USE")
                                .message("error.option.value.in.use")
                                .data(java.util.Map.of("variantCount", ex.getVariantCount()))
                                .timestamp(java.time.Instant.now())
                                .build();
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }

        @ExceptionHandler(OptionInUseException.class)
        public ResponseEntity<ApiResponse<Object>> handleOptionInUse(OptionInUseException ex) {
                ApiResponse<Object> body = ApiResponse.builder()
                                .success(false)
                                .code("OPTION_IN_USE")
                                .message("error.option.in.use")
                                .data(java.util.Map.of("variantCount", ex.getVariantCount()))
                                .timestamp(java.time.Instant.now())
                                .build();
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }

        @ExceptionHandler(ValueExistsException.class)
        public ResponseEntity<ApiResponse<Object>> handleValueExists(ValueExistsException ex) {
                ApiResponse<Object> body = ApiResponse.builder()
                                .success(false)
                                .code("VALUE_EXISTS")
                                .message("error.option.value.exists")
                                .timestamp(java.time.Instant.now())
                                .build();
                return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }

        @ExceptionHandler(FieldValidationException.class)
        public ResponseEntity<ApiResponse<Void>> handleFieldValidation(FieldValidationException ex) {
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage("error.validation.failed", null, locale);
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                                .body(ApiResponse.error(message, ex.getFieldErrors()));
        }

        @ExceptionHandler(OptionNotApplicableException.class)
        public ResponseEntity<ApiResponse<Object>> handleOptionNotApplicable(OptionNotApplicableException ex) {
                ApiResponse<Object> body = ApiResponse.builder()
                                .success(false)
                                .code("OPTION_NOT_APPLICABLE")
                                .message("error.option.not.applicable")
                                .timestamp(java.time.Instant.now())
                                .build();
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
        }

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

        // ─── Concurrency conflicts → 409, not 500 ────────────────────────────

        @ExceptionHandler({ OptimisticLockException.class, OptimisticLockingFailureException.class })
        public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(RuntimeException ex) {
                // A concurrent checkout won the race for the same row (stock,
                // promo usage, …). The buyer's request is fine — someone else
                // just got there first — so answer 409 with the stock message,
                // never a generic 500.
                log.warn("Optimistic lock conflict, likely a lost checkout race: {}", ex.getMessage());
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage(
                                "error.product.insufficientStock",
                                new Object[] { 0 },
                                "error.product.insufficientStock",
                                locale);
                return ResponseEntity.status(HttpStatus.CONFLICT)
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

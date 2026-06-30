package com.nocteon.nocteon_api.common.exception;

import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.ApiFieldError;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

        private final MessageSource messageSource;

        @ExceptionHandler(BaseApiException.class)
        public ResponseEntity<ApiResponse<Void>> handleBaseApiException(BaseApiException ex) {
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage(ex.getMessageKey(), ex.getMessageArgs(), locale);
                return ResponseEntity.status(ex.getStatus())
                                .body(ApiResponse.error(message, null));
        }
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
                                .body(ApiResponse.error(userMessage,null));
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

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
                log.error("Unexpected error occurred", ex);
                Locale locale = LocaleContextHolder.getLocale();
                String message = messageSource.getMessage("error.unexpected", null, locale);

                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ApiResponse.error(message, null));
        }
}
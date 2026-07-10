package com.nocteon.nocteon_api.promoCode.controller;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.auth.security.UserPrincipal;
import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.promoCode.dto.request.PromoCodeCalculationResult;
import com.nocteon.nocteon_api.promoCode.dto.request.PromoCodeRequest;
import com.nocteon.nocteon_api.promoCode.dto.response.PromoCodeResponse;
import com.nocteon.nocteon_api.promoCode.service.PromoCodeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PromoCodeController {

    private final PromoCodeService promoCodeService;

    @PostMapping("/promo-codes/{code}/apply")
    public ResponseEntity<ApiResponse<PromoCodeCalculationResult>> apply(
            @PathVariable String code,
            @AuthenticationPrincipal UserPrincipal principal) {

        PromoCodeCalculationResult result = promoCodeService.previewDiscount(code, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.success(result, "Promo code applied"));
    }

    @GetMapping("/dashboard/promo-codes")
    @PreAuthorize("hasAuthority('promo:read')")
    public ResponseEntity<ApiResponse<PageResponse<PromoCodeResponse>>> getAll(
            @ModelAttribute BaseFilterRequest filter) {
        return ResponseEntity.ok(ApiResponse.success(promoCodeService.getAll(filter), "Promo codes retrieved"));
    }

    @GetMapping("/dashboard/promo-codes/{id}")
    @PreAuthorize("hasAuthority('promo:read')")
    public ResponseEntity<ApiResponse<PromoCodeResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(promoCodeService.getById(id), "Promo code retrieved"));
    }

    @PostMapping("/dashboard/promo-codes")
    @PreAuthorize("hasAuthority('promo:create')")
    public ResponseEntity<ApiResponse<PromoCodeResponse>> create(@RequestBody @Valid PromoCodeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(promoCodeService.create(request), "Promo code created"));
    }

    @PutMapping("/dashboard/promo-codes/{id}")
    @PreAuthorize("hasAuthority('promo:update')")
    public ResponseEntity<ApiResponse<PromoCodeResponse>> update(
            @PathVariable Long id, @RequestBody @Valid PromoCodeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(promoCodeService.update(id, request), "Promo code updated"));
    }

    @DeleteMapping("/dashboard/promo-codes/{id}")
    @PreAuthorize("hasAuthority('promo:delete')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        promoCodeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Promo code deleted"));
    }
}
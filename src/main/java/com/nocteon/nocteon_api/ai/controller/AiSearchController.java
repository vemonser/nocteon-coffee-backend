package com.nocteon.nocteon_api.ai.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nocteon.nocteon_api.ai.dto.response.SimilarProductResponse;
import com.nocteon.nocteon_api.ai.service.SimilarProductService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * Public, storefront-facing AI endpoints.
 *
 * <p>No {@code @PreAuthorize}: these sit next to {@code GET /api/products/{slug}} and are read
 * by the same unauthenticated visitors. The feature flags and the attribute fallback, not
 * authorization, are what keep these cheap and available.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiSearchController {

    private final SimilarProductService similarProductService;

    /**
     * Products similar to the one being viewed.
     *
     * <p>Answers from the vector store when AI is enabled and reachable, and from attribute
     * similarity otherwise — so this returns results in both states and is not a place a
     * downed Ollama can produce a 500.
     *
     * <p>{@code limit} is clamped to {@code [1, 20]} by the service; an unknown {@code slug} is
     * a 404 rather than an empty list.
     */
    @GetMapping("/products/{slug}/similar")
    public ResponseEntity<ApiResponse<List<SimilarProductResponse>>> getSimilarProducts(
            @PathVariable String slug,
            @RequestParam(defaultValue = "8") int limit) {

        return ResponseEntity.ok(ApiResponse.success(
                similarProductService.findSimilar(slug, limit),
                "Similar products retrieved"));
    }
}
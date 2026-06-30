package com.nocteon.nocteon_api.pairing.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.dto.TranslationResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.PairingNotFoundException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.pairing.dto.request.PairingRequest;
import com.nocteon.nocteon_api.pairing.dto.request.PairingTranslationRequest;
import com.nocteon.nocteon_api.pairing.dto.response.PairingResponse;
import com.nocteon.nocteon_api.pairing.dto.response.PairingResponseDashboard;
import com.nocteon.nocteon_api.pairing.entity.Pairing;
import com.nocteon.nocteon_api.pairing.entity.PairingTranslation;
import com.nocteon.nocteon_api.pairing.repository.PairingRepository;
import com.nocteon.nocteon_api.pairing.repository.PairingTranslationRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PairingService {
        private final PairingRepository pairingRepository;
        private final PairingTranslationRepository translationRepository;
        private final LookupServiceHelper helper;

        @Transactional
        public PairingResponse create(PairingRequest request, MultipartFile image) {
                helper.validateTranslations(request.getTranslations());

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(PairingTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                String slug = helper.generateUniqueSlug(englishName, pairingRepository::existsBySlug);
                String imageUrl = helper.uploadImage(image, "pairings");

                Pairing pairing = Pairing.builder()
                                .slug(slug)
                                .imageUrl(imageUrl)
                                .build();

                pairing = pairingRepository.save(pairing);

                final Pairing saved = pairing;
                List<PairingTranslation> translations = new ArrayList<>();
                for (PairingTranslationRequest t : request.getTranslations()) {
                        translations.add(PairingTranslation.builder()
                                        .pairing(saved)
                                        .language(t.getLanguage())
                                        .name(t.getName())
                                        .description(t.getDescription())
                                        .build());
                }
                translationRepository.saveAll(translations);

                return buildResponse(pairing, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public PairingResponse update(String slug, PairingRequest request, MultipartFile image) {
                Pairing pairing = pairingRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(PairingNotFoundException::new);

                if (image != null && !image.isEmpty()) {
                        helper.deleteImageIfExists(pairing.getImageUrl());
                        pairing.setImageUrl(helper.uploadImage(image, "pairings"));
                }

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());
                        request.getTranslations().forEach(t -> translationRepository
                                        .findByPairingIdAndLanguage(pairing.getId(), t.getLanguage())
                                        .ifPresentOrElse(
                                                        existing -> {
                                                                existing.setName(t.getName());
                                                                existing.setDescription(t.getDescription());
                                                                translationRepository.save(existing);
                                                        },
                                                        () -> translationRepository.save(
                                                                        PairingTranslation.builder()
                                                                                        .pairing(pairing)
                                                                                        .language(t.getLanguage())
                                                                                        .name(t.getName())
                                                                                        .description(t.getDescription())
                                                                                        .build())));
                }

                pairingRepository.save(pairing);
                return buildResponse(pairing, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public PairingResponse uploadImage(String slug, MultipartFile file) {
                Pairing pairing = pairingRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(PairingNotFoundException::new);

                helper.deleteImageIfExists(pairing.getImageUrl());
                pairing.setImageUrl(helper.uploadImage(file, "pairings"));
                pairingRepository.save(pairing);

                return buildResponse(pairing, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public void delete(String slug) {
                Pairing pairing = pairingRepository.findBySlugWithTranslations(slug)
                                .orElseThrow(PairingNotFoundException::new);
                helper.deleteImageIfExists(pairing.getImageUrl());
                pairing.softDelete();
                pairingRepository.save(pairing);
        }

        public PairingResponse getBySlug(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Pairing pairing = pairingRepository.findBySlugAndLanguage(slug, language)
                                .orElseThrow(PairingNotFoundException::new);
                return buildResponse(pairing, language);
        }

        public PageResponse<PairingResponse> getAll(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Page<Pairing> page = pairingRepository.findAllPublic(
                                language, filter.getSearch(), filter.toPageable());
                return PageResponse.of(page.map(p -> buildResponse(p, language)));
        }

        public PageResponse<PairingResponseDashboard> getAllDashboard(LookupFilterRequest filter) {
                String search = Objects.requireNonNullElse(
                                filter.getSearch(),
                                "");

                                Page<Pairing> page = pairingRepository.findAllDashboard(
                                search,
                                filter.toPageable());
                return PageResponse.of(
                                page.map(this::buildResponse));
        }

        private PairingResponse buildResponse(Pairing pairing, String language) {
                List<PairingTranslation> translations = translationRepository
                                .findByPairingId(pairing.getId());

                PairingTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                return PairingResponse.builder()
                                .id(pairing.getId())
                                .slug(pairing.getSlug())
                                .name(translation != null ? translation.getName() : null)
                                .description(translation != null ? translation.getDescription() : null)
                                .imageUrl(pairing.getImageUrl())
                                .build();
        }
                private PairingResponseDashboard buildResponse(Pairing pairing) {
                return PairingResponseDashboard.builder()
                                .id(pairing.getId())
                                .slug(pairing.getSlug())
                                .imageUrl(pairing.getImageUrl())
                                .translations(
                                                pairing.getTranslations()
                                                                .stream()
                                                                .map(t -> TranslationResponse.builder()
                                                                                .language(t.getLanguage())
                                                                                .name(t.getName())
                                                                                .description(t.getDescription())
                                                                                .build())
                                                                .toList())
                                .build();
        }
}

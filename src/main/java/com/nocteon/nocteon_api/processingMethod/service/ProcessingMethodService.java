package com.nocteon.nocteon_api.processingMethod.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.common.dto.TranslationResponse;
import com.nocteon.nocteon_api.common.exception.invalid.InvalidTranslationException;
import com.nocteon.nocteon_api.common.exception.notFound.ProcessingMethodNotFoundException;
import com.nocteon.nocteon_api.common.service.LookupServiceHelper;
import com.nocteon.nocteon_api.processingMethod.dto.request.ProcessingMethodRequest;
import com.nocteon.nocteon_api.processingMethod.dto.request.ProcessingMethodTranslationRequest;
import com.nocteon.nocteon_api.processingMethod.dto.response.ProcessingMethodResponse;
import com.nocteon.nocteon_api.processingMethod.dto.response.ProcessingMethodResponseDashboard;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethod;
import com.nocteon.nocteon_api.processingMethod.entity.ProcessingMethodTranslation;
import com.nocteon.nocteon_api.processingMethod.repository.ProcessingMethodRepository;
import com.nocteon.nocteon_api.processingMethod.repository.ProcessingMethodTranslationRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessingMethodService {

        private final ProcessingMethodRepository processingMethodRepository;
        private final ProcessingMethodTranslationRepository translationRepository;
        private final LookupServiceHelper helper;

        public PageResponse<ProcessingMethodResponse> getAll(LookupFilterRequest filter) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                Page<ProcessingMethod> page = processingMethodRepository.findAllPublic(
                                language, filter.getSearch(), filter.toPageable());
                return PageResponse.of(page.map(p -> buildResponse(p, language)));
        }

        public PageResponse<ProcessingMethodResponseDashboard> getAllDashboard(LookupFilterRequest filter) {
                String search = Objects.requireNonNullElse(
                                filter.getSearch(),
                                "");

                Page<ProcessingMethod> page = processingMethodRepository.findAllDashboard(
                                search, filter.toPageable());

                return PageResponse.of(
                                page.map(this::buildResponse));
        }

        public ProcessingMethodResponse getBySlug(String slug) {
                String language = LocaleContextHolder.getLocale().getLanguage();
                ProcessingMethod processingMethod = processingMethodRepository
                                .findBySlugAndLanguage(slug, language)
                                .orElseThrow(ProcessingMethodNotFoundException::new);
                return buildResponse(processingMethod, language);
        }

        public ProcessingMethodResponseDashboard getDashboardBySlug(String slug) {
                ProcessingMethod processingMethod = processingMethodRepository
                                .findBySlugWithTranslations(slug)
                                .orElseThrow(ProcessingMethodNotFoundException::new);
                return buildResponse(processingMethod);
        }

        @Transactional
        public ProcessingMethodResponse create(ProcessingMethodRequest request) {
                helper.validateTranslations(request.getTranslations());

                String englishName = request.getTranslations().stream()
                                .filter(t -> t.getLanguage().equals("en"))
                                .findFirst()
                                .map(ProcessingMethodTranslationRequest::getName)
                                .orElseThrow(InvalidTranslationException::new);

                String slug = helper.generateUniqueSlug(
                                englishName, processingMethodRepository::existsBySlug);

                ProcessingMethod processingMethod = ProcessingMethod.builder()
                                .slug(slug)
                                .build();

                processingMethod = processingMethodRepository.save(processingMethod);

                final ProcessingMethod saved = processingMethod;
                List<ProcessingMethodTranslation> translations = new ArrayList<>();
                for (ProcessingMethodTranslationRequest t : request.getTranslations()) {
                        translations.add(ProcessingMethodTranslation.builder()
                                        .processingMethod(saved)
                                        .language(t.getLanguage())
                                        .name(t.getName())
                                        .description(t.getDescription())
                                        .build());
                }
                translationRepository.saveAll(translations);

                log.info("ProcessingMethod created with slug: {}", slug);
                return buildResponse(processingMethod, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public ProcessingMethodResponse update(String slug, ProcessingMethodRequest request) {
                ProcessingMethod processingMethod = processingMethodRepository
                                .findBySlugWithTranslations(slug)
                                .orElseThrow(ProcessingMethodNotFoundException::new);

                if (request.getTranslations() != null && !request.getTranslations().isEmpty()) {
                        helper.validateTranslations(request.getTranslations());
                        request.getTranslations().forEach(t -> translationRepository
                                        .findByProcessingMethodIdAndLanguage(processingMethod.getId(), t.getLanguage())
                                        .ifPresentOrElse(
                                                        existing -> {
                                                                existing.setName(t.getName());
                                                                existing.setDescription(t.getDescription());
                                                                translationRepository.save(existing);
                                                        },
                                                        () -> translationRepository.save(
                                                                        ProcessingMethodTranslation.builder()
                                                                                        .processingMethod(
                                                                                                        processingMethod)
                                                                                        .language(t.getLanguage())
                                                                                        .name(t.getName())
                                                                                        .description(t.getDescription())
                                                                                        .build())));
                }

                processingMethodRepository.save(processingMethod);
                return buildResponse(processingMethod, LocaleContextHolder.getLocale().getLanguage());
        }

        @Transactional
        public void delete(String slug) {
                ProcessingMethod processingMethod = processingMethodRepository
                                .findBySlugWithTranslations(slug)
                                .orElseThrow(ProcessingMethodNotFoundException::new);
                processingMethod.softDelete();
                processingMethodRepository.save(processingMethod);
                log.info("ProcessingMethod soft deleted: {}", slug);
        }

        private ProcessingMethodResponse buildResponse(ProcessingMethod processingMethod, String language) {
                List<ProcessingMethodTranslation> translations = translationRepository
                                .findByProcessingMethodId(processingMethod.getId());

                ProcessingMethodTranslation translation = translations.stream()
                                .filter(t -> t.getLanguage().equals(language))
                                .findFirst()
                                .orElse(translations.isEmpty() ? null : translations.get(0));

                return ProcessingMethodResponse.builder()
                                .id(processingMethod.getId())
                                .slug(processingMethod.getSlug())
                                .name(translation != null ? translation.getName() : null)
                                .description(translation != null ? translation.getDescription() : null)
                                .build();
        }

        private ProcessingMethodResponseDashboard buildResponse(ProcessingMethod processingMethod) {
                return ProcessingMethodResponseDashboard.builder()
                                .id(processingMethod.getId())
                                .slug(processingMethod.getSlug())
                                .createdAt(processingMethod.getCreatedAt())
                                .translations(
                                                processingMethod.getTranslations()
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
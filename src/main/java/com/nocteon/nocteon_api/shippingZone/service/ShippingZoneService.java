package com.nocteon.nocteon_api.shippingZone.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.LookupFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.shippingZone.dto.request.ShippingZoneRequest;
import com.nocteon.nocteon_api.shippingZone.dto.response.ShippingZoneResponse;
import com.nocteon.nocteon_api.shippingZone.entity.ShippingZone;
import com.nocteon.nocteon_api.shippingZone.exception.CityAlreadyAssignedException;
import com.nocteon.nocteon_api.shippingZone.exception.ShippingNotAvailableException;
import com.nocteon.nocteon_api.shippingZone.exception.ShippingZoneNotFoundException;
import com.nocteon.nocteon_api.shippingZone.repository.ShippingZoneRepository;
import com.nocteon.nocteon_api.common.repository.SearchPatterns;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingZoneService {

    private final ShippingZoneRepository shippingZoneRepository;

    public BigDecimal calculateShippingCost(String city) {
        ShippingZone zone = shippingZoneRepository.findActiveZoneByCity(city.trim())
                .orElseThrow(ShippingNotAvailableException::new);

        return zone.getShippingCost();
    }

    public PageResponse<ShippingZoneResponse> getAll(BaseFilterRequest filter) {
        Page<ShippingZone> page = shippingZoneRepository.findAll(filter.toPageable());
        return PageResponse.of(page.map(this::buildResponse));
    }

    @Transactional
    public ShippingZoneResponse create(ShippingZoneRequest request) {
        validateNoCityConflict(request.getCities(), null);

        ShippingZone zone = ShippingZone.builder()
                .name(request.getName())
                .shippingCost(request.getShippingCost())
                .cities(new HashSet<>(request.getCities()))
                .active(request.isActive())
                .build();

        shippingZoneRepository.save(zone);
        return buildResponse(zone);
    }

    public ShippingZoneResponse getById(Long id) {
        ShippingZone zone = shippingZoneRepository.findById(id)
                .orElseThrow(ShippingZoneNotFoundException::new);
        return buildResponse(zone);
    }

    @Transactional
    public ShippingZoneResponse update(Long id, ShippingZoneRequest request) {
        ShippingZone zone = shippingZoneRepository.findById(id)
                .orElseThrow(ShippingZoneNotFoundException::new);

        validateNoCityConflict(request.getCities(), id);

        zone.setName(request.getName());
        zone.setShippingCost(request.getShippingCost());
        zone.setCities(new HashSet<>(request.getCities()));
        zone.setActive(request.isActive());

        shippingZoneRepository.save(zone);
        return buildResponse(zone);
    }

    @Transactional
    public void delete(Long id) {
        ShippingZone zone = shippingZoneRepository.findById(id)
                .orElseThrow(ShippingZoneNotFoundException::new);
        zone.softDelete();
        shippingZoneRepository.save(zone);
    }

    /**
     * Permanently deletes a trashed shipping zone: the row itself (its cities go with it — they
     * are an element collection owned by the zone).
     *
     * <p>Trash-only: reads through {@code findDeletedById}, so a live or unknown id raises
     * {@link ShippingZoneNotFoundException} instead of deleting anything. Soft-delete first —
     * this never touches live rows.
     */
    @Transactional
    public void permanentDelete(Long id) {
        ShippingZone zone = shippingZoneRepository.findDeletedById(id)
                .orElseThrow(ShippingZoneNotFoundException::new);
        shippingZoneRepository.delete(zone);
        log.info("Shipping zone permanently deleted: {}", zone.getName());
    }

    @Transactional(readOnly = true)
    public PageResponse<ShippingZoneResponse> getAllDeleted(LookupFilterRequest filter) {
        String search = SearchPatterns.like(filter.getSearch());
        int pageNum = filter.getPage();
        int size = filter.getSize();
        String sort = filter.getSort();
        String direction = filter.getDirection();

        List<ShippingZone> entities;
        if (search != null && !search.isEmpty()) {
            entities = shippingZoneRepository.findDeletedBySearchNative(search);
        } else {
            entities = shippingZoneRepository.findAllDeletedNative();
        }

        List<ShippingZoneResponse> allResults = entities.stream()
                .map(this::buildResponse)
                .collect(Collectors.toList());

        if (sort != null) {
            allResults.sort((a, b) -> {
                int result = 0;
                switch (sort) {
                    case "name":
                        result = a.getName().compareToIgnoreCase(b.getName());
                        break;
                    case "createdAt":
                        result = a.getCreatedAt().compareTo(b.getCreatedAt());
                        break;
                    default:
                        result = 0;
                }
                return direction.equalsIgnoreCase("desc") ? -result : result;
            });
        }

        int fromIndex = pageNum * size;
        // Clamp: a caller who paged past the end would otherwise get an IndexOutOfBoundsException
        // from subList, which surfaces as a 500 rather than an empty page.
        if (fromIndex > allResults.size()) {
            fromIndex = allResults.size();
        }
        int toIndex = Math.min(fromIndex + size, allResults.size());
        List<ShippingZoneResponse> pagedResults = allResults.subList(fromIndex, toIndex);

        Page<ShippingZoneResponse> pageObj = new PageImpl<>(pagedResults, PageRequest.of(pageNum, size), allResults.size());
        return PageResponse.of(pageObj);
    }

    @Transactional
    public void restore(Long id) {
        ShippingZone zone = shippingZoneRepository.findDeletedById(id)
                .orElseThrow(ShippingZoneNotFoundException::new);
        zone.setDeletedAt(null);
        shippingZoneRepository.save(zone);
        log.info("ShippingZone restored: {}", zone.getName());
    }

    private void validateNoCityConflict(Set<String> cities, Long excludeZoneId) {
        for (String city : cities) {
            boolean exists = excludeZoneId == null
                    ? shippingZoneRepository.existsByCitiesContaining(city)
                    : shippingZoneRepository.existsByCitiesContainingAndIdNot(city, excludeZoneId);

            if (exists) {
                throw new CityAlreadyAssignedException(city);
            }
        }
    }

    private ShippingZoneResponse buildResponse(ShippingZone zone) {
        return ShippingZoneResponse.builder()
                .id(zone.getId())
                .name(zone.getName())
                .shippingCost(zone.getShippingCost())
                .cities(zone.getCities())
                .active(zone.isActive())
                .createdAt(zone.getCreatedAt())
                .updatedAt(zone.getUpdatedAt())
                .build();
    }
}
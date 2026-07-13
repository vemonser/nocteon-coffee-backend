package com.nocteon.nocteon_api.shippingZone.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.common.dto.BaseFilterRequest;
import com.nocteon.nocteon_api.common.dto.PageResponse;
import com.nocteon.nocteon_api.shippingZone.dto.request.ShippingZoneRequest;
import com.nocteon.nocteon_api.shippingZone.dto.response.ShippingZoneResponse;
import com.nocteon.nocteon_api.shippingZone.entity.ShippingZone;
import com.nocteon.nocteon_api.shippingZone.exception.CityAlreadyAssignedException;
import com.nocteon.nocteon_api.shippingZone.exception.ShippingNotAvailableException;
import com.nocteon.nocteon_api.shippingZone.exception.ShippingZoneNotFoundException;
import com.nocteon.nocteon_api.shippingZone.repository.ShippingZoneRepository;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
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
                .build();
    }
}
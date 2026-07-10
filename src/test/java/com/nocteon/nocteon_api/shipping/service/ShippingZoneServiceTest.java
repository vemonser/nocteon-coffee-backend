package com.nocteon.nocteon_api.shipping.service;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.shippingZone.dto.request.ShippingZoneRequest;
import com.nocteon.nocteon_api.shippingZone.entity.ShippingZone;
import com.nocteon.nocteon_api.shippingZone.exception.CityAlreadyAssignedException;
import com.nocteon.nocteon_api.shippingZone.exception.ShippingNotAvailableException;
import com.nocteon.nocteon_api.shippingZone.exception.ShippingZoneNotFoundException;
import com.nocteon.nocteon_api.shippingZone.repository.ShippingZoneRepository;
import com.nocteon.nocteon_api.shippingZone.service.ShippingZoneService;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Edge-case coverage for ShippingZoneService: city coverage lookups,
 * case/whitespace handling, and cross-zone city conflict prevention.
 */
@ExtendWith(MockitoExtension.class)
class ShippingZoneServiceTest {

    @Mock private ShippingZoneRepository shippingZoneRepository;

    @InjectMocks
    private ShippingZoneService shippingZoneService;

    @Nested
    class CalculateShippingCost {

        @Test
        void returnsZoneCostWhenCityIsCovered() {
            ShippingZone zone = ShippingZone.builder()
                    .id(1L)
                    .name("Cairo & Giza")
                    .shippingCost(BigDecimal.valueOf(50))
                    .cities(Set.of("Cairo", "Giza"))
                    .active(true)
                    .build();

            when(shippingZoneRepository.findActiveZoneByCity("Cairo")).thenReturn(Optional.of(zone));

            BigDecimal cost = shippingZoneService.calculateShippingCost("Cairo");

            assertThat(cost).isEqualByComparingTo(BigDecimal.valueOf(50));
        }

        @Test
        void throwsShippingNotAvailableWhenCityIsNotCoveredByAnyZone() {
            when(shippingZoneRepository.findActiveZoneByCity("Riyadh")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shippingZoneService.calculateShippingCost("Riyadh"))
                    .isInstanceOf(ShippingNotAvailableException.class);
        }

        @Test
        void throwsShippingNotAvailableWhenZoneCoveringCityIsInactive() {
            // Repository query already filters on active=true, so an inactive zone
            // covering the city should behave identically to "no zone found".
            when(shippingZoneRepository.findActiveZoneByCity("Cairo")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shippingZoneService.calculateShippingCost("Cairo"))
                    .isInstanceOf(ShippingNotAvailableException.class);
        }

        @Test
        void trimsWhitespaceBeforeLookup() {
            ShippingZone zone = ShippingZone.builder()
                    .id(1L)
                    .shippingCost(BigDecimal.valueOf(50))
                    .cities(Set.of("Cairo"))
                    .active(true)
                    .build();

            when(shippingZoneRepository.findActiveZoneByCity("Cairo")).thenReturn(Optional.of(zone));

            BigDecimal cost = shippingZoneService.calculateShippingCost("  Cairo  ");

            assertThat(cost).isEqualByComparingTo(BigDecimal.valueOf(50));
        }
    }

    @Nested
    class CityConflictValidation {

        @Test
        void createThrowsWhenCityAlreadyBelongsToAnotherZone() {
            ShippingZoneRequest request = new ShippingZoneRequest();
            request.setName("Alexandria & Delta");
            request.setShippingCost(BigDecimal.valueOf(70));
            request.setCities(Set.of("Cairo")); // already taken by another zone
            request.setActive(true);

            when(shippingZoneRepository.existsByCitiesContaining("Cairo")).thenReturn(true);

            assertThatThrownBy(() -> shippingZoneService.create(request))
                    .isInstanceOf(CityAlreadyAssignedException.class);
        }

        @Test
        void createSucceedsWhenAllCitiesAreUnclaimed() {
            ShippingZoneRequest request = new ShippingZoneRequest();
            request.setName("Cairo & Giza");
            request.setShippingCost(BigDecimal.valueOf(50));
            request.setCities(Set.of("Cairo", "Giza"));
            request.setActive(true);

            when(shippingZoneRepository.existsByCitiesContaining("Cairo")).thenReturn(false);
            when(shippingZoneRepository.existsByCitiesContaining("Giza")).thenReturn(false);
            when(shippingZoneRepository.save(org.mockito.ArgumentMatchers.any(ShippingZone.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            var response = shippingZoneService.create(request);

            assertThat(response.getName()).isEqualTo("Cairo & Giza");
        }

        @Test
        void updateExcludesOwnZoneIdFromConflictCheck() {
            // Zone #1 already owns "Cairo" - updating zone #1 itself to still include
            // "Cairo" must NOT be treated as a conflict with itself.
            ShippingZone existingZone = ShippingZone.builder()
                    .id(1L)
                    .name("Cairo & Giza")
                    .shippingCost(BigDecimal.valueOf(50))
                    .cities(Set.of("Cairo"))
                    .active(true)
                    .build();

            ShippingZoneRequest request = new ShippingZoneRequest();
            request.setName("Cairo & Giza");
            request.setShippingCost(BigDecimal.valueOf(60));
            request.setCities(Set.of("Cairo", "Giza"));
            request.setActive(true);

            when(shippingZoneRepository.findById(1L)).thenReturn(Optional.of(existingZone));
            when(shippingZoneRepository.existsByCitiesContainingAndIdNot("Cairo", 1L)).thenReturn(false);
            when(shippingZoneRepository.existsByCitiesContainingAndIdNot("Giza", 1L)).thenReturn(false);
            when(shippingZoneRepository.save(org.mockito.ArgumentMatchers.any(ShippingZone.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            var response = shippingZoneService.update(1L, request);

            assertThat(response.getShippingCost()).isEqualByComparingTo(BigDecimal.valueOf(60));
        }

        @Test
        void updateThrowsWhenAddingACityOwnedByADifferentZone() {
            ShippingZone existingZone = ShippingZone.builder()
                    .id(1L)
                    .name("Cairo & Giza")
                    .shippingCost(BigDecimal.valueOf(50))
                    .cities(Set.of("Cairo"))
                    .active(true)
                    .build();

            ShippingZoneRequest request = new ShippingZoneRequest();
            request.setName("Cairo & Giza");
            request.setShippingCost(BigDecimal.valueOf(50));
            request.setCities(Set.of("Cairo", "Alexandria")); // Alexandria owned by another zone
            request.setActive(true);

            when(shippingZoneRepository.findById(1L)).thenReturn(Optional.of(existingZone));
            when(shippingZoneRepository.existsByCitiesContainingAndIdNot("Cairo", 1L)).thenReturn(false);
            when(shippingZoneRepository.existsByCitiesContainingAndIdNot("Alexandria", 1L)).thenReturn(true);

            assertThatThrownBy(() -> shippingZoneService.update(1L, request))
                    .isInstanceOf(CityAlreadyAssignedException.class);
        }

        @Test
        void updateThrowsWhenZoneNotFound() {
            ShippingZoneRequest request = new ShippingZoneRequest();
            request.setName("Ghost Zone");
            request.setShippingCost(BigDecimal.TEN);
            request.setCities(Set.of("Nowhere"));

            when(shippingZoneRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> shippingZoneService.update(999L, request))
                    .isInstanceOf(ShippingZoneNotFoundException.class);
        }
    }
}
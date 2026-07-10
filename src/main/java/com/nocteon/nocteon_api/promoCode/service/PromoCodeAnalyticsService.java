package com.nocteon.nocteon_api.promoCode.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.nocteon.nocteon_api.promoCode.dto.response.PromoCodeAnalyticsResponse;
import com.nocteon.nocteon_api.promoCode.dto.response.RedemptionsByDayDto;
import com.nocteon.nocteon_api.promoCode.dto.response.TopPerformingPromoCodeDto;
import com.nocteon.nocteon_api.promoCode.repository.PromoCodeRedemptionRepository;
import com.nocteon.nocteon_api.promoCode.repository.PromoCodeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PromoCodeAnalyticsService {

    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeRedemptionRepository redemptionRepository;

    private static final int TOP_CODES_LIMIT = 5;

    public PromoCodeAnalyticsResponse getAnalytics(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        BigDecimal totalDiscountGivenAllTime = redemptionRepository.sumAllDiscountGiven();
        long totalRedemptionsAllTime = redemptionRepository.count();
        long activeCodesCount = promoCodeRepository.countByActiveTrue();

        List<TopPerformingPromoCodeDto> topPerformingCodes = redemptionRepository
                .getTopPerformingCodes(PageRequest.of(0, TOP_CODES_LIMIT))
                .stream()
                .map(row -> TopPerformingPromoCodeDto.builder()
                        .code((String) row[0])
                        .redemptionCount((Long) row[1])
                        .totalDiscountGiven((BigDecimal) row[2])
                        .build())
                .toList();

        List<RedemptionsByDayDto> redemptionsByDay = buildFilledRedemptionsByDay(
                redemptionRepository.getRedemptionsByDay(since), days);

        return PromoCodeAnalyticsResponse.builder()
                .totalDiscountGivenAllTime(totalDiscountGivenAllTime)
                .totalRedemptionsAllTime(totalRedemptionsAllTime)
                .activeCodesCount(activeCodesCount)
                .topPerformingCodes(topPerformingCodes)
                .redemptionsByDay(redemptionsByDay)
                .build();
    }

    private List<RedemptionsByDayDto> buildFilledRedemptionsByDay(List<Object[]> rawResults, int days) {
        Map<LocalDate, Object[]> resultMap = rawResults.stream()
                .collect(Collectors.toMap(
                        row -> ((java.sql.Date) row[0]).toLocalDate(),
                        row -> row));

        LocalDate today = LocalDate.now();
        List<RedemptionsByDayDto> filled = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Object[] row = resultMap.get(date);

            filled.add(RedemptionsByDayDto.builder()
                    .date(date)
                    .count(row != null ? (Long) row[1] : 0L)
                    .discountAmount(row != null ? (BigDecimal) row[2] : BigDecimal.ZERO)
                    .build());
        }

        return filled;
    }
}
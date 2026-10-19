package com.nocteon.nocteon_api.order.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.nocteon.nocteon_api.common.dto.ApiResponse;
import com.nocteon.nocteon_api.order.dto.response.OrderResponse;
import com.nocteon.nocteon_api.order.service.OrderQueryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderQueryService orderQueryService;

    @InjectMocks
    private OrderController controller;

    @Test
    void getById_existingOrder_returnsOrderDetail() {
        Long id = 1L;
        OrderResponse order = OrderResponse.builder()
                .id(id)
                .status(com.nocteon.nocteon_api.order.enums.OrderStatus.PENDING)
                .totalAmount(new java.math.BigDecimal("150.00"))
                .build();
        when(orderQueryService.getById(id)).thenReturn(order);

        ResponseEntity<ApiResponse<OrderResponse>> response = controller.getById(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getId()).isEqualTo(id);
        verify(orderQueryService).getById(id);
    }

    @Test
    void getById_returnsCorrectOrderData() {
        Long id = 42L;
        OrderResponse order = OrderResponse.builder()
                .id(id)
                .status(com.nocteon.nocteon_api.order.enums.OrderStatus.CONFIRMED)
                .totalAmount(new java.math.BigDecimal("299.99"))
                .build();
        when(orderQueryService.getById(id)).thenReturn(order);

        ResponseEntity<ApiResponse<OrderResponse>> response = controller.getById(id);

        assertThat(response.getBody().getData().getStatus())
                .isEqualTo(com.nocteon.nocteon_api.order.enums.OrderStatus.CONFIRMED);
        assertThat(response.getBody().getData().getTotalAmount())
                .isEqualByComparingTo("299.99");
    }
}

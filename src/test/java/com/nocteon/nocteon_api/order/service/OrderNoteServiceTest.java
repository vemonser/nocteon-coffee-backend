package com.nocteon.nocteon_api.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nocteon.nocteon_api.common.exception.notFound.OrderNotFoundException;
import com.nocteon.nocteon_api.order.dto.request.CreateOrderNoteRequest;
import com.nocteon.nocteon_api.order.entity.Order;
import com.nocteon.nocteon_api.order.entity.OrderNote;
import com.nocteon.nocteon_api.order.repository.OrderNoteRepository;
import com.nocteon.nocteon_api.order.repository.OrderRepository;

/**
 * The internal notes of an order.
 *
 * <p>A note is what the TEAM wrote: it is never the customer's own note
 * (that one is {@code orders.notes}), and it is always attributed.
 */
@ExtendWith(MockitoExtension.class)
class OrderNoteServiceTest {

    @Mock private OrderNoteRepository noteRepository;
    @Mock private OrderRepository orderRepository;

    @InjectMocks
    private OrderNoteService orderNoteService;

    private CreateOrderNoteRequest request(String body) {
        CreateOrderNoteRequest request = new CreateOrderNoteRequest();
        request.setBody(body);
        return request;
    }

    @Test
    void addsANoteWithItsAuthor() {
        Order order = Order.builder().id(1L).build();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(noteRepository.save(any(OrderNote.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderNote note = orderNoteService.addNote(1L, request("Customer asked for a gift note."), "admin@aurum.local");

        assertThat(note.getBody()).isEqualTo("Customer asked for a gift note.");
        assertThat(note.getAuthor()).isEqualTo("admin@aurum.local");
        assertThat(note.isInternal()).isTrue();
        assertThat(note.getOrder()).isSameAs(order);
    }

    @Test
    void readsTheNotesNewestFirst() {
        when(orderRepository.existsById(1L)).thenReturn(true);
        when(noteRepository.findByOrderIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());

        assertThat(orderNoteService.getNotes(1L)).isEmpty();
        verify(noteRepository).findByOrderIdOrderByCreatedAtDesc(1L);
    }

    @Test
    void throwsWhenTheOrderDoesNotExist() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderNoteService.addNote(1L, request("A note"), "admin"))
                        .isInstanceOf(OrderNotFoundException.class);
    }
}

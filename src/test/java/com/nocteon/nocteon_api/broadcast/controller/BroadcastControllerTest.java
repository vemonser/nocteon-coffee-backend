package com.nocteon.nocteon_api.broadcast.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.nocteon.nocteon_api.broadcast.service.BroadcastCreationService;
import com.nocteon.nocteon_api.common.dto.ApiResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BroadcastControllerTest {

    @Mock
    private BroadcastCreationService broadcastCreationService;

    @InjectMocks
    private BroadcastController controller;

    @Test
    void delete_existingBroadcast_returnsNoContent() {
        Long id = 1L;
        doNothing().when(broadcastCreationService).delete(id);

        ResponseEntity<Void> response = controller.delete(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(broadcastCreationService).delete(id);
    }

    @Test
    void delete_setsNoContentStatus() {
        Long id = 99L;
        doNothing().when(broadcastCreationService).delete(id);

        ResponseEntity<Void> response = controller.delete(id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }
}

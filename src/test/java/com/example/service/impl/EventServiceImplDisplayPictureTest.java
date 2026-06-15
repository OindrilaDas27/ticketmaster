package com.example.service.impl;

import com.example.configuration.S3Properties;
import com.example.dao.impl.EventsDaoImpl;
import com.example.dto.EventsDTO;
import com.example.service.LocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class EventServiceImplDisplayPictureTest {

    private EventServiceImpl service;

    @BeforeEach
    void setUp() {
        S3Properties props = new S3Properties(
                "https://e.r2.cloudflarestorage.com", "auto", "ak", "sk",
                "prozect-media", "https://pub-x.r2.dev", 300, 5_242_880L,
                "image/jpeg,image/png,image/webp"
        );
        service = new EventServiceImpl(mock(EventsDaoImpl.class), mock(LocationService.class), props);
    }

    @Test
    void rejects_event_displayPicture_url_not_from_r2_base() {
        EventsDTO dto = new EventsDTO();
        dto.setName("Test");
        dto.setDisplayPicture("https://evil.example.com/x.jpg");

        assertThatThrownBy(() -> service.createEvent(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayPicture");
    }
}

package com.example.service.impl;

import com.example.configuration.S3Properties;
import com.example.dao.impl.UserDaoImpl;
import com.example.dto.UserDTO;
import com.example.entity.User;
import com.example.validation.UserValidation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceImplDisplayPictureTest {

    private UserServiceImpl service;
    private UserDaoImpl dao;

    @BeforeEach
    void setUp() {
        dao = mock(UserDaoImpl.class);
        UserValidation validation = mock(UserValidation.class);
        S3Properties props = new S3Properties(
                "https://e.r2.cloudflarestorage.com", "auto", "ak", "sk",
                "prozect-media", "https://pub-x.r2.dev", 300, 5_242_880L,
                "image/jpeg,image/png,image/webp"
        );
        service = new UserServiceImpl(dao, validation, props);

        User existing = new User();
        existing.setId(1L);
        existing.setUsername("u");
        existing.setEmail("e@e.com");
        existing.setFirstName("f");
        existing.setLastName("l");
        when(dao.findById(1L)).thenReturn(Optional.of(existing));
        when(dao.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void rejects_displayPicture_url_not_from_r2_base() {
        UserDTO dto = new UserDTO();
        dto.setUsername("u"); dto.setEmail("e@e.com");
        dto.setFirstName("f"); dto.setLastName("l");
        dto.setPhoneNumber("1");
        dto.setDisplayPicture("https://evil.example.com/x.jpg");

        assertThatThrownBy(() -> service.updateUser(1L, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("displayPicture");
    }

    @Test
    void accepts_displayPicture_url_from_r2_base() {
        UserDTO dto = new UserDTO();
        dto.setUsername("u"); dto.setEmail("e@e.com");
        dto.setFirstName("f"); dto.setLastName("l");
        dto.setPhoneNumber("1");
        dto.setDisplayPicture("https://pub-x.r2.dev/users/2026/06/a.jpg");

        UserDTO result = service.updateUser(1L, dto);
        // No exception = pass; sanity-check the field flowed through:
        org.assertj.core.api.Assertions.assertThat(result.getDisplayPicture())
                .isEqualTo("https://pub-x.r2.dev/users/2026/06/a.jpg");
    }
}

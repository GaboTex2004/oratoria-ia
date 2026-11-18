package com.oratoria.backend.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class UploadLimitMessageTests {
    final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    String message(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        return handler.maxUploadSize(new MaxUploadSizeExceededException(1), request).getBody().message();
    }

    @Test void backupUploadsGetTheirOwnMessage() {
        assertThat(message("/api/admin/backups/restore")).contains("respaldo").doesNotContain("audio");
    }

    @Test void audioUploadsKeepTheAudioMessage() {
        assertThat(message("/api/sessions/1/complete")).contains("audio");
    }
}

package com.example.picturezipupload;

import com.example.picturezipupload.importing.ImageTypeDetector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageTypeDetectorTest {

    @Test
    void acceptsPngByExtensionAndMagicHeader() {
        byte[] firstBytes = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };

        assertThat(ImageTypeDetector.isSupportedImage("png", firstBytes)).isTrue();
    }

    @Test
    void rejectsExtensionWhenMagicHeaderDoesNotMatch() {
        byte[] firstBytes = "not an image".getBytes();

        assertThat(ImageTypeDetector.isSupportedImage("jpg", firstBytes)).isFalse();
    }
}

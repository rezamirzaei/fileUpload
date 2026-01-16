package com.example.demo.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class EncryptionServiceTests {

    @Test
    void encryptThenDecrypt_roundTripsBytes() throws Exception {
        // Base64 for 32 bytes: 000102...1f
        String key = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

        EncryptionService encryptionService = new EncryptionService();
        Field f = EncryptionService.class.getDeclaredField("configuredSecretKey");
        f.setAccessible(true);
        f.set(encryptionService, key);
        encryptionService.init();

        byte[] original = "hello encrypted world".getBytes(StandardCharsets.UTF_8);

        Path tmpDir = Files.createTempDirectory("enc-test-");
        Path encFile = tmpDir.resolve("sample.bin.enc");

        encryptionService.encryptToFile(new ByteArrayInputStream(original), encFile, original.length);

        try (InputStream decrypted = encryptionService.decryptFromFile(encFile)) {
            byte[] roundTrip = decrypted.readAllBytes();
            assertArrayEquals(original, roundTrip);
        }
    }
}

package com.example.demo.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class EncryptionServiceTests {

    @Test
    void encryptThenDecrypt_roundTripsBytes() throws Exception {
        // Base64 for 32 bytes (simulating a user's encryption key)
        String userKey = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

        EncryptionService encryptionService = new EncryptionService();

        byte[] original = "hello encrypted world".getBytes(StandardCharsets.UTF_8);

        Path tmpDir = Files.createTempDirectory("enc-test-");
        Path encFile = tmpDir.resolve("sample.bin.enc");

        // Encrypt with user key
        encryptionService.encryptToFile(new ByteArrayInputStream(original), encFile, original.length, userKey);

        // Decrypt with same user key
        try (InputStream decrypted = encryptionService.decryptFromFile(encFile, userKey)) {
            byte[] roundTrip = decrypted.readAllBytes();
            assertArrayEquals(original, roundTrip);
        }
    }
}

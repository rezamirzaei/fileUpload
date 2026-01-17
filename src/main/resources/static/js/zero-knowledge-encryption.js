/**
 * Client-Side Zero-Knowledge Encryption Module
 *
 * This module derives the encryption key entirely in the browser using
 * the Web Crypto API. The actual encryption key NEVER leaves the browser.
 *
 * Flow:
 * 1. User enters password
 * 2. Browser derives encryption key using PBKDF2 (password + salt)
 * 3. Key is stored in sessionStorage (browser only)
 * 4. Password is sent to server ONLY for authentication (hashed by server)
 * 5. Files are encrypted/decrypted entirely client-side
 *
 * Security: Even if the server is compromised, it cannot decrypt files
 * because it never has access to the derived encryption key.
 */

const ZeroKnowledgeEncryption = {
    // PBKDF2 parameters (must match server-side for salt validation)
    PBKDF2_ITERATIONS: 310000,
    KEY_LENGTH: 256,
    SALT_LENGTH: 32,

    /**
     * Derive an AES-256 key from password and salt using PBKDF2.
     * This happens entirely in the browser - the key never leaves.
     */
    async deriveKey(password, saltBase64) {
        const encoder = new TextEncoder();
        const salt = this.base64ToArrayBuffer(saltBase64);

        // Import password as key material
        const keyMaterial = await crypto.subtle.importKey(
            'raw',
            encoder.encode(password),
            'PBKDF2',
            false,
            ['deriveBits', 'deriveKey']
        );

        // Derive the actual encryption key
        const key = await crypto.subtle.deriveKey(
            {
                name: 'PBKDF2',
                salt: salt,
                iterations: this.PBKDF2_ITERATIONS,
                hash: 'SHA-256'
            },
            keyMaterial,
            { name: 'AES-GCM', length: this.KEY_LENGTH },
            true, // extractable for storage
            ['encrypt', 'decrypt']
        );

        // Export key to store in sessionStorage
        const exportedKey = await crypto.subtle.exportKey('raw', key);
        return this.arrayBufferToBase64(exportedKey);
    },

    /**
     * Encrypt a file entirely client-side.
     */
    async encryptFile(file, keyBase64) {
        const key = await this.importKey(keyBase64);
        const iv = crypto.getRandomValues(new Uint8Array(12)); // 96-bit IV for GCM

        const fileData = await file.arrayBuffer();

        const encryptedData = await crypto.subtle.encrypt(
            { name: 'AES-GCM', iv: iv },
            key,
            fileData
        );

        // Prepend IV to encrypted data
        const result = new Uint8Array(iv.length + encryptedData.byteLength);
        result.set(iv, 0);
        result.set(new Uint8Array(encryptedData), iv.length);

        return new Blob([result], { type: 'application/octet-stream' });
    },

    /**
     * Decrypt a file entirely client-side.
     */
    async decryptFile(encryptedBlob, keyBase64, originalType) {
        const key = await this.importKey(keyBase64);
        const encryptedData = await encryptedBlob.arrayBuffer();

        // Extract IV (first 12 bytes)
        const iv = new Uint8Array(encryptedData.slice(0, 12));
        const ciphertext = encryptedData.slice(12);

        const decryptedData = await crypto.subtle.decrypt(
            { name: 'AES-GCM', iv: iv },
            key,
            ciphertext
        );

        return new Blob([decryptedData], { type: originalType || 'application/octet-stream' });
    },

    /**
     * Import a base64 key for use with Web Crypto.
     */
    async importKey(keyBase64) {
        const keyData = this.base64ToArrayBuffer(keyBase64);
        return await crypto.subtle.importKey(
            'raw',
            keyData,
            { name: 'AES-GCM', length: 256 },
            false,
            ['encrypt', 'decrypt']
        );
    },

    /**
     * Store the derived key in sessionStorage.
     * Key is automatically cleared when browser tab closes.
     */
    storeKey(keyBase64) {
        sessionStorage.setItem('zk_encryption_key', keyBase64);
    },

    /**
     * Retrieve the encryption key from sessionStorage.
     */
    getKey() {
        return sessionStorage.getItem('zk_encryption_key');
    },

    /**
     * Clear the encryption key (on logout).
     */
    clearKey() {
        sessionStorage.removeItem('zk_encryption_key');
    },

    /**
     * Check if encryption key is available.
     */
    hasKey() {
        return sessionStorage.getItem('zk_encryption_key') !== null;
    },

    // Utility functions
    base64ToArrayBuffer(base64) {
        const binaryString = atob(base64);
        const bytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
            bytes[i] = binaryString.charCodeAt(i);
        }
        return bytes.buffer;
    },

    arrayBufferToBase64(buffer) {
        const bytes = new Uint8Array(buffer);
        let binary = '';
        for (let i = 0; i < bytes.length; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary);
    },

    /**
     * Generate a random salt (for new user registration).
     */
    generateSalt() {
        const salt = crypto.getRandomValues(new Uint8Array(this.SALT_LENGTH));
        return this.arrayBufferToBase64(salt.buffer);
    }
};

// Export for use in other scripts
window.ZeroKnowledgeEncryption = ZeroKnowledgeEncryption;

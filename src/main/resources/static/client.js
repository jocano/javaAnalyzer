// javascript

/**
 * Unwraps/decrypts a value encrypted with Java Cipher using RSA-OAEP.
 * 
 * This function demonstrates how to unwrap an encrypted value that was encrypted
 * using Java's Cipher with algorithm "RSA/ECB/OAEPWithSHA-256AndMGF1Padding".
 * 
 * @param {string} wrappedKeyB64 - Base64-encoded encrypted data from Java server
 * @param {CryptoKey} privateKey - RSA private key (corresponding to public key used for encryption)
 * @returns {Promise<CryptoKey>} - Unwrapped AES key as a CryptoKey object
 */
async function unwrapEncryptedValue(wrappedKeyB64, privateKey) {
  // Step 1: Decode base64 string to binary data (ArrayBuffer)
  // Java sends base64, we need to convert it to ArrayBuffer for Web Crypto API
  const wrapped = Uint8Array.from(atob(wrappedKeyB64), c => c.charCodeAt(0)).buffer;
  
  console.log("Step 1 - Decoded base64 to ArrayBuffer, length:", wrapped.byteLength);
  
  // Step 2: Unwrap using crypto.subtle.unwrapKey()
  // This decrypts the data using RSA-OAEP with SHA-256 (matching Java's algorithm)
  try {
    const unwrappedKey = await crypto.subtle.unwrapKey(
      // Format of the wrapped key: "raw" means it's raw bytes (AES key bytes)
      "raw",
      
      // The encrypted data (ArrayBuffer)
      wrapped,
      
      // The private key to decrypt with (must match the public key used for encryption)
      privateKey,
      
      // Algorithm used for unwrapping - MUST match Java's algorithm:
      // Java: "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
      // JS:   { name: "RSA-OAEP", hash: "SHA-256" }
      { name: "RSA-OAEP", hash: "SHA-256" },
      
      // The type of key we're unwrapping to (AES-GCM 256-bit)
      { name: "AES-GCM", length: 256 },
      
      // Whether the key can be extracted (true = yes)
      true,
      
      // Key usages (what operations can be performed with this key)
      ["encrypt", "decrypt", "unwrapKey", "wrapKey"]
    );
    
    console.log("Step 2 - Successfully unwrapped key:", unwrappedKey);
    return unwrappedKey;
    
  } catch (error) {
    console.error("Error during unwrapKey:", error);
    throw new Error(`Failed to unwrap encrypted value: ${error.message}`);
  }
}

/**
 * Alternative: Decrypts the value and then imports it as a CryptoKey.
 * This is a two-step process: decrypt then import.
 */
async function decryptEncryptedValue(wrappedKeyB64, privateKey) {
  // Step 1: Decode base64 to ArrayBuffer
  const atobString = atob(wrappedKeyB64);
  console.log("ATOB String:>" + atobString + "<");
  const wrapped = Uint8Array.from(atob(wrappedKeyB64), c => c.charCodeAt(0)).buffer;
  
  // Step 2: Decrypt using RSA-OAEP to get raw AES key bytes
  const decrypted = await crypto.subtle.decrypt(
    { name: "RSA-OAEP", hash: "SHA-256" },
    privateKey,
    wrapped
  );
  
  console.log("Decrypted bytes length:", decrypted.byteLength);
  
  // Step 3: Import the decrypted bytes as an AES-GCM CryptoKey
  const aesKey = await crypto.subtle.importKey(
    "raw",                    // Format: raw key bytes
    decrypted,                // The decrypted AES key bytes
    { name: "AES-GCM", length: 256 },  // Algorithm and key length
    true,                     // Extractable
    ["encrypt", "decrypt", "unwrapKey", "wrapKey"]  // Key usages
  );
  
  // Returns CryptoKey object (same as unwrapKey would return)
  return aesKey;
}

/**
 * Encrypts a string using RSA-OAEP encryption.
 * Note: RSA encryption requires the public key, not the private key.
 * This method accepts the keypair object and uses the public key for encryption.
 * 
 * @param {string} plaintext - The string to encrypt
 * @param {CryptoKeyPair} keypair - The RSA keypair (contains both public and private keys)
 * @returns {Promise<string>} - Base64-encoded encrypted data
 */
async function encryptStringWithPrivateKey(plaintext, keypair) {
  try {
    // Step 1: Convert string to ArrayBuffer
    const encoder = new TextEncoder();
    const data = encoder.encode(plaintext);
    
    // Step 2: Encrypt using RSA-OAEP with the public key
    // Note: RSA encryption always uses the public key, even if we have the keypair
    // The keypair object contains both publicKey and privateKey properties
    const encrypted = await crypto.subtle.encrypt(
      {
        name: "RSA-OAEP",
        hash: "SHA-256"
      },
      keypair.publicKey,  // Use public key for encryption (required for RSA)
      data
    );
    
    // Step 3: Convert encrypted data to base64
    const encryptedArray = new Uint8Array(encrypted);
    const encryptedB64 = btoa(String.fromCharCode(...encryptedArray));
    
    console.log("Encrypted data length:", encrypted.byteLength, "bytes");
    
    return encryptedB64;
    
  } catch (error) {
    console.error("Error encrypting string:", error);
    throw new Error(`Failed to encrypt string: ${error.message}`);
  }
}

/**
 * Encrypts a string using RSA-OAEP with a keypair's public key.
 * This is the recommended approach - use the public key from the keypair.
 * 
 * @param {string} plaintext - The string to encrypt
 * @param {CryptoKeyPair} keypair - The RSA keypair containing both public and private keys
 * @returns {Promise<string>} - Base64-encoded encrypted data
 */
async function encryptStringWithKeyPair(plaintext, keypair) {
  try {
    // Step 1: Convert string to ArrayBuffer
    const encoder = new TextEncoder();
    const data = encoder.encode(plaintext);
    
    // Step 2: Encrypt using RSA-OAEP with the public key
    const encrypted = await crypto.subtle.encrypt(
      {
        name: "RSA-OAEP",
        hash: "SHA-256"
      },
      keypair.publicKey,  // Use public key for encryption
      data
    );
    
    // Step 3: Convert encrypted data to base64
    const encryptedArray = new Uint8Array(encrypted);
    const encryptedB64 = btoa(String.fromCharCode(...encryptedArray));
    
    console.log("Encrypted data length:", encrypted.byteLength, "bytes");
    
    return encryptedB64;
    
  } catch (error) {
    console.error("Error encrypting string:", error);
    throw new Error(`Failed to encrypt string: ${error.message}`);
  }
}

/**
 * Decrypts a string that was encrypted using RSA-OAEP.
 * 
 * @param {string} encryptedMessage - Base64-encoded encrypted data
 * @param {CryptoKeyPair} keypair - The RSA keypair containing both public and private keys
 * @returns {Promise<string>} - Decrypted plaintext string
 */
async function decryptStringWithKeyPair(encryptedMessage, keypair) {
  try {
    // Step 1: Decode base64 string to ArrayBuffer
    const encrypted = Uint8Array.from(atob(encryptedMessage), c => c.charCodeAt(0)).buffer;
    
    console.log("Encrypted data length:", encrypted.byteLength, "bytes");
    
    // Step 2: Decrypt using RSA-OAEP with the private key
    const decrypted = await crypto.subtle.decrypt(
      {
        name: "RSA-OAEP",
        hash: "SHA-256"
      },
      keypair.privateKey,  // Use private key for decryption
      encrypted
    );
    
    // Step 3: Convert decrypted ArrayBuffer back to string
    const decoder = new TextDecoder();
    const plaintext = decoder.decode(decrypted);
    
    console.log("Decrypted data length:", decrypted.byteLength, "bytes");
    
    return plaintext;
    
  } catch (error) {
    console.error("Error decrypting string:", error);
    throw new Error(`Failed to decrypt string: ${error.message}`);
  }
}

async function obtainAesFromServer() {
    // generate ephemeral RSA-OAEP keypair (client-side)
    const kp = await crypto.subtle.generateKey(
      { name: "RSA-OAEP", modulusLength: 2048, publicExponent: new Uint8Array([1,0,1]), hash: "SHA-256" },
      true,
      ["encrypt","decrypt","unwrapKey","wrapKey"]
    );
  
    // test encryption with the keypair
    await testEncryptionDecryption(kp);

    // export public key (SPKI DER -> base64) and send to server
    const spki = await crypto.subtle.exportKey("spki", kp.publicKey);
    const spkiB64 = btoa(String.fromCharCode(...new Uint8Array(spki)));
  
    // POST to your HTTPS endpoint, e.g. /wrap-aes
    const resp = await fetch("/api/wrap-aes", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ clientSpki: spkiB64 })
    });
    const { wrappedKeyB64 } = await resp.json();
  
    console.log("Wrapped Key B64:>" + wrappedKeyB64 + "<");
    // unwrap AES key (server wrapped raw AES bytes)
    const wrapped = Uint8Array.from(atob(wrappedKeyB64), c => c.charCodeAt(0)).buffer;
    const wrappedArray = new Uint8Array(wrapped);
    console.log("Wrapped ArrayBuffer length:", wrapped.byteLength);
    console.log("Wrapped bytes (base64):", btoa(String.fromCharCode(...wrappedArray)));
    console.log("Wrapped bytes (hex):", Array.from(wrappedArray).map(b => b.toString(16).padStart(2, '0')).join(''));
    console.log("Wrapped bytes (decimal):", Array.from(wrappedArray));

    try {
          // const aesKey = await crypto.subtle.unwrapKey(
          //   "raw",
          //   wrapped,
          //   kp.privateKey,
          //   { name: "RSA-OAEP", hash: "SHA-256" },
          //   { name: "AES-GCM", length: 256 },
          //   true,
          //   ["encrypt","decrypt"]
          const aesKey = await decryptEncryptedValue(wrappedKeyB64, kp.privateKey)
          console.log("AES Key:>" + aesKey + "<");
          return aesKey; // CryptoKey usable for AES-GCM in browser
    } catch (error) {
      console.error("Error decrypting value:", error);
      throw new Error(`Failed to decrypt value: ${error.message}`);
    }
  }


async function testEncryptionDecryption(kp) {
  const encryptedMessage = await encryptStringWithKeyPair("Hello from the client!", kp);
  console.log("Encrypted message:>" + encryptedMessage + "<");

  // test decryption with the keypair
  const decryptedMessage = await decryptStringWithKeyPair(encryptedMessage, kp);
  console.log("Decrypted message:>" + decryptedMessage + "<");
  if (decryptedMessage !== "Hello from the client!") {
    throw new Error("Decrypted message does not match the original message");
  } else {
    console.log("Decrypted message matches the original message");
  }
}


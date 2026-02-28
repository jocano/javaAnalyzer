// javascript
async function obtainAesFromServer() {
    // generate ephemeral RSA-OAEP keypair (client-side)
    const kp = await crypto.subtle.generateKey(
      { name: "RSA-OAEP", modulusLength: 2048, publicExponent: new Uint8Array([1,0,1]), hash: "SHA-256" },
      true,
      ["encrypt","decrypt"]
    );
  
    // export public key (SPKI DER -> base64) and send to server
    const spki = await crypto.subtle.exportKey("spki", kp.publicKey);
    const spkiB64 = btoa(String.fromCharCode(...new Uint8Array(spki)));
  
    // POST to your HTTPS endpoint, e.g. /wrap-aes
    const resp = await fetch("http://localhost:8080api/wrap-aes", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      //body: JSON.stringify({ clientSpki: spkiB64 })
      body: spkiB64
    });
    const { wrappedKeyB64 } = await resp.json();
  
    // unwrap AES key (server wrapped raw AES bytes)
    const wrapped = Uint8Array.from(atob(wrappedKeyB64), c => c.charCodeAt(0)).buffer;
    const aesKey = await crypto.subtle.unwrapKey(
      "raw",
      wrapped,
      kp.privateKey,
      { name: "RSA-OAEP" },
      { name: "AES-GCM", length: 256 },
      true,
      ["encrypt","decrypt"]
    );
  
    return aesKey; // CryptoKey usable for AES-GCM in browser
  }

  // Function to send POST request to /api/simple endpoint
  async function sendPostToSimple(message) {
    try {
      const response = await fetch("/api/simple", {
        method: "POST",
        headers: { "Content-Type": "text/plain" },
        body: message
      });
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      const data = await response.json();
      return data;
    } catch (error) {
      console.error("Error sending POST request:", error);
      throw error;
    }
  }
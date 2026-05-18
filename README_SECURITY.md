# 🔒 ChriOnline Security Architecture & Implementation Report

Welcome to the comprehensive technical security documentation of the **ChriOnline E-Commerce Platform**. This document details every single cryptographic, session, network, and application-layer security measure implemented across the codebase. It details **how each feature works**, **how it is implemented**, and provides **exact line-by-line code locations** to ensure auditability.

---

## 🛡️ 1. Layered Defense-in-Depth Architecture

ChriOnline is designed using the **Defense-in-Depth** paradigm. Rather than relying on a single security mechanism, multiple independent barriers protect the system. If one security layer is compromised, subsequent layers prevent exploitation.

```
┌────────────────────────────────────────────────────────────────────────┐
│  LAYER 1: Transport Security  ── SSLSockets (TLS 1.3 / Port 8084)       │
├────────────────────────────────────────────────────────────────────────┤
│  LAYER 2: Network Protection  ── TCP & UDP Rate Limiting, Connection   │
│                                  Handshake Timeouts (30s)              │
├────────────────────────────────────────────────────────────────────────┤
│  LAYER 3: Hybrid Cryptography ── RSA-2048 / AES-256 Hybrid Handshake   │
├────────────────────────────────────────────────────────────────────────┤
│  LAYER 4: Message Channel     ── AES-256-GCM Encrypted Payloads        │
├────────────────────────────────────────────────────────────────────────┤
│  LAYER 5: Integrity & Replay  ── GCM 128-bit Authentication Tags &     │
│                                  Sliding-Window IV Verification        │
├────────────────────────────────────────────────────────────────────────┤
│  LAYER 6: Auth & Credentials  ── Salted jBCrypt Hashing, Passwordless  │
│                                  RSA Challenge-Response Signatures     │
├────────────────────────────────────────────────────────────────────────┤
│  LAYER 7: Session Integrity   ── SHA-256 Token Memory Hashing, AFK     │
│                                  Idle Timeouts, Token Rotation (30m)   │
├────────────────────────────────────────────────────────────────────────┤
│  LAYER 8: Data Privacy        ── AES-256-GCM Payment Encryption &      │
│                                  Strict Binary Decoupling              │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🌐 2. Transport Layer Security & TLS Sockets

All raw TCP network communication is wrapped in a secure socket layer using standard TLS. This ensures confidentiality, authenticity, and server verification at the network boundary.

### How It Works
* The server hosts its certificates in a secure `keystore.p12` using the modern PKCS12 format.
* Clients trust only connection channels that match the signatures contained in their local truststore file (`truststore.p12`). This protects the connection from **Man-in-the-Middle (MitM)** attacks.

### Code References
* **Shared Centralized Configuration Constants**:  
  Located in [`src/Shared/Security/CryptoConfig.java`](src/Shared/Security/CryptoConfig.java#L24-L34)
  ```java
  public static final String KEYSTORE_PATH     = "keystore.p12";
  public static final String KEYSTORE_PASSWORD  = "123456";
  public static final String KEYSTORE_ALIAS     = "ecommerce";
  public static final String TRUSTSTORE_PATH     = "truststore.p12";
  public static final String TRUSTSTORE_PASSWORD  = "123456";
  ```
* **Client TrustStore Registration**:  
  Located in [`src/Client/network/SocketClient.java`](src/Client/network/SocketClient.java#L29-L30)
  ```java
  System.setProperty("javax.net.ssl.trustStore", "truststore.p12");
  System.setProperty("javax.net.ssl.trustStorePassword", "123456");
  System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
  ```
* **Admin TrustStore Registration**:  
  Located in [`src/Admin/network/AdminSocket.java`](src/Admin/network/AdminSocket.java#L29-L30)
  ```java
  System.setProperty("javax.net.ssl.trustStore", "truststore.p12");
  System.setProperty("javax.net.ssl.trustStorePassword", "123456");
  System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
  ```
* **Server KeyStore Loading**:  
  Located in [`src/Server/security/SecureHandshake.java`](src/Server/security/SecureHandshake.java#L85-L103) (`loadKeyPairFromKeyStore()`).

---

## 🔒 3. Inbound DoS, TCP flood & Incomplete Connection Timeout

The server is hardened against **Denial of Service (DoS)**, **TCP Flooding**, and **Slowloris** attacks which attempt to exhaust server thread/connection limits.

### TCP Connection Rate Limiting
* **How It Works**: The server records the timestamp and connection count of each IP address inside a 1-minute window. If an IP exceeds `MAX_CONNECTIONS_PER_MINUTE` (50), the socket is immediately closed.
* **Code Reference**:  
  Located in [`src/Server/Server.java`](src/Server/Server.java#L150-L154) and the `isRateLimited` helper at [`L195-L210`](src/Server/Server.java#L195-L210):
  ```java
  if (isRateLimited(clientIP)) {
      logger.warn("[Server] TCP Flood: Too many connections from " + clientIP + ". Dropping.");
      clientSocket.close();
      continue;
  }
  ```

### Incomplete Connection Timeout (Slowloris Protection)
* **How It Works**: When a client connects, the server immediately sets a socket read timeout of 30 seconds (`setSoTimeout(30_000)`). If a client initiates a TCP handshake but fails to complete the Secure Handshake protocol (sending the encrypted AES key) within 30 seconds, a `SocketTimeoutException` triggers, and the connection is closed.
* **Session Handover**: Once the handshake completes and the first valid, decrypted command is successfully received, the socket's read timeout is set to `0` (disabled) to allow normal long-running active socket states.
* **Code References**:
  * **Initial Socket Timeout Assignment**:  
    Located in [`src/Server/Server.java`](src/Server/Server.java#L156):
    ```java
    clientSocket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
    ```
  * **First Command Handling & Timeout Reset**:  
    Located in [`src/Server/ClientHandler.java`](src/Server/ClientHandler.java#L108-L111):
    ```java
    if (!firstCommandReceived) {
        socket.setSoTimeout(0);
        firstCommandReceived = true;
    }
    ```
  * **Slowloris Catch Block**:  
    Located in [`src/Server/ClientHandler.java`](src/Server/ClientHandler.java#L139-L141):
    ```java
    } catch (java.net.SocketTimeoutException e) {
        logger.warn("[ClientHandler] Dropped incomplete connection from "
                + clientAddress + " (10s handshake timeout)");
    }
    ```

### UDP Packet Flood Protection
* **How It Works**: An in-memory rate-limiter prevents UDP flood attacks by tracking and throttling outgoing notifications per destination IP address.
* **Code Reference**:  
  Located in [`src/Server/UDPServer.java`](src/Server/UDPServer.java#L51-L54) and [`L76-L92`](src/Server/UDPServer.java#L76-L92) (`isUDPRateLimited()`).

---

## 🤝 4. Hybrid Cryptographic Handshake (RSA + AES)

To bridge the gap between asymmetry and speed, ChriOnline utilizes a custom application-layer **hybrid cryptographic handshake** on top of TLS.

```
Client (Customer / Admin)                         Server
   │                                                 │
   ├────────────── 1. Connect (TLS) ────────────────▶│
   │                                                 │
   │◀─── 2. Send RSA Public Key (Base64) ────────────┤ [SecureHandshake L40-44]
   │                                                 │
   │  3. Generate secure AES-256 key                 │
   │  4. Encrypt AES key using Server RSA Public     │
   │                                                 │
   ├────────── 5. Send Encrypted AES Key ───────────▶│ [SecureHandshake L46-55]
   │                                                 │
   │                                                 │ 6. Decrypt AES Key using Private Key
   │◀───────── 7. Handshake Confirmed ───────────────┤ [SecureHandshake L63-68]
   │                                                 │
   ▼ ══ All traffic encrypted via AES-256-GCM ══════ ▼
```

### How It Works
1. Upon connection, the Server loads its RSA keypair from the central keystore and sends its Public Key as a Base64-encoded string (`SERVER_PUBLIC_KEY:`).
2. The Client generates a secure 256-bit AES symmetric key.
3. The Client encrypts the AES key using the server's RSA Public Key and returns it (`AES_KEY:<ciphertext>`).
4. The Server decrypts the symmetric key using its RSA Private Key. Both entities now share the same AES-256 session key.

### Code References
* **Crypto Constants**: [`src/Shared/Security/CryptoConfig.java`](src/Shared/Security/CryptoConfig.java#L20-L22)
  * Mode: `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` (OAEP padding is explicitly enforced to defend against padding oracle attacks).
* **Server-side Handshake Routine**:  
  Located in [`src/Server/security/SecureHandshake.java`](src/Server/security/SecureHandshake.java#L38-L77) (`perform()`).
* **Client-side Handshake Routine**:  
  Located in [`src/Client/network/SecureKeyExchange.java`](src/Client/network/SecureKeyExchange.java#L15-L47).
* **Admin-side Handshake Routine**:  
  Located in [`src/Admin/network/AdminSecureKeyExchange.java`](src/Admin/network/AdminSecureKeyExchange.java#L15-L48).

---

## ✉️ 5. AES-256-GCM Message Encryption & Integrity

Once the session key is established, all packet traffic is encrypted to prevent packet interception or modification.

### How It Works
* **Authenticated Encryption**: We use `AES/GCM/NoPadding` (Galois/Counter Mode). Unlike CBC, GCM provides **AEAD** (Authenticated Encryption with Associated Data), which generates a 128-bit authentication tag with every message. This mathematically proves the packet has not been altered in transit.
* **Initialization Vector (IV)**: Each message uses a fresh, mathematically unique 12-byte (96-bit) IV generated via `SecureRandom` to prevent ciphertext patterns.
* **Wire Format**: Encrypted packets are transmitted in the format `Base64(IV):Base64(Ciphertext)`.

### Code References
* **Cryptographic Core (AES GCM Utility)**:  
  Located in [`src/Shared/Security/AESUtil.java`](src/Shared/Security/AESUtil.java)
  * **Encryption**: [`L33-L45`](src/Shared/Security/AESUtil.java#L33-L45)
  * **Decryption**: [`L51-L65`](src/Shared/Security/AESUtil.java#L51-L65)
* **Server-side Interception**:  
  Located in [`src/Server/ClientHandler.java`](src/Server/ClientHandler.java#L104-L105) (`decryptMessage()`) and [`L131-L133`](src/Server/ClientHandler.java#L131-L133) (`encryptMessage()`).
* **Client-side Encryption Integration**:  
  Located in [`src/Client/network/SocketClient.java`](src/Client/network/SocketClient.java#L56-L76).
* **Admin-side Encryption Integration**:  
  Located in [`src/Admin/network/AdminSocket.java`](src/Admin/network/AdminSocket.java#L56-L76).

---

## 🔄 6. Replay Attack Protection (IV Sliding Window)

A replay attack involves capturing a valid encrypted payload (e.g., checkout transaction request) and submitting it again. Even if encrypted, repeating the payload would cause the server to execute the operation multiple times.

### How It Works
* Since every encrypted message uses a unique, random IV, the server tracks seen IVs.
* The server maintains a `ConcurrentHashMap` of registered IVs paired with their arrival timestamp.
* If an incoming message contains an IV already processed within the **5-minute sliding window**, it is immediately blocked and logged as an attack.
* Old IVs are automatically pruned from memory to ensure bounded memory usage.

### Code References
* **Replay Registry and Verification**:  
  Located in [`src/Server/security/ReplayProtection.java`](src/Server/security/ReplayProtection.java)
  * **Replay Verification**: [`L50-L62`](src/Server/security/ReplayProtection.java#L50-L62)
  * **IV Registration**: [`L74-L83`](src/Server/security/ReplayProtection.java#L74-L83)
  * **Automatic Memory Pruning**: [`L91-L94`](src/Server/security/ReplayProtection.java#L91-L94) (`cleanup()`).

---

## 🔑 7. Session Token Management, Memory Protection & Rotation

Session tokens are highly sensitive credentials. If compromised, they allow a malicious actor to impersonate users or administrators.

### 7.1. Hashed Session Tokens in Memory
* **How It Works**: To mitigate memory-dump attacks (where an attacker scans the RAM of the running server to extract plain-text session tokens), the server **hashes all session tokens** using SHA-256 before using them as keys in the session directory.
* **Plaintext Never Stored**: The plaintext session UUID is compared or retrieved by hashing the input token first. The plaintext token is never cached or stored.
* **Code References**:  
  Located in [`src/Server/SessionManager.java`](src/Server/SessionManager.java)
  * **Hashing Routine**: [`L54-L68`](src/Server/SessionManager.java#L54-L68)
  * **Map Storage (Hashed Keys)**: [`L79-L80`](src/Server/SessionManager.java#L79-L80)
  * **Hashed Session Lookup**: [`L126-L129`](src/Server/SessionManager.java#L126-L129)

### 7.2. Transparent Token Rotation (Regeneration)
* **How It Works**: To prevent session hijacking/fixation, session tokens are regenerated every **30 minutes**.
* **Seamless Update**: During command dispatching, if a session's token is older than 1800 seconds (30 minutes), the server generates a new token, swaps the session metadata, and sends the response prefixed with `"RENEWED_TOKEN:<newSessionToken>|||"`.
* **Client Handover**: Both Client and Admin network sockets transparently intercept this prefix, update their respective application states, and process the payload seamlessly.
* **Code References**:
  * **Server-side Session Token Regeneration**:  
    Located in [`src/Server/SessionManager.java`](src/Server/SessionManager.java#L98-L120) (`regenerateToken()`).
  * **Server Rotation Check & Header Construction**:  
    Located in [`src/Server/ClientHandler.java`](src/Server/ClientHandler.java#L190-L200) and [`L212-L214`](src/Server/ClientHandler.java#L212-L214).
  * **Client Transparent Token Integration**:  
    Located in [`src/Client/network/SocketClient.java`](src/Client/network/SocketClient.java#L78-L87).
  * **Admin Transparent Token Integration**:  
    Located in [`src/Admin/network/AdminSocket.java`](src/Admin/network/AdminSocket.java#L75-L84).

### 7.3. Session Inactivity (AFK) Expiration Timeout
* **How It Works**: Idle sessions are automatically deleted from server memory after **10 minutes** of inactivity to reduce memory usage and limit the exploit window of abandoned terminals.
* **Code References**:  
  Located in [`src/Server/SessionManager.java`](src/Server/SessionManager.java)
  * Constants: [`L21`](src/Server/SessionManager.java#L21) (`MAX_IDLE_TIME_SECONDS = 600`)
  * Scheduled Task: [`L30-L40`](src/Server/SessionManager.java#L30-L40) (`cleanupIdleSessions()`).

---

## 👤 8. Strong Password Hashing (jBCrypt)

Stored credentials must be securely hashed to prevent exposure in the event of a database compromise.

### How It Works
* Client passwords are not stored using fast hashing algorithms like MD5 or SHA-1, which are vulnerable to hardware-accelerated brute-force attacks.
* ChriOnline employs **jBCrypt** (a Blowfish-based adaptive hashing function) to hash credentials.
* BCrypt implements an adaptive cost factor (work factor) and incorporates an explicit cryptographic salt, making rainbow tables and GPU brute-forcing computationally infeasible.

### Code References
* **Password Hashing and Salt Generation**:  
  Located in [`src/Server/service/UserService.java`](src/Server/service/UserService.java#L201):
  ```java
  return BCrypt.hashpw(plainTextPassword, BCrypt.gensalt());
  ```
* **Password Verification**:  
  Located in [`src/Server/service/UserService.java`](src/Server/service/UserService.java#L205):
  ```java
  return BCrypt.checkpw(plainTextPassword, hashedPassword);
  ```

---

## 📧 9. Secure 2FA, OTP & SMTP Mail Communications

A secure **Forgot Password** flow and login mechanism are implemented using secure email verification.

### 9.1. Secure SMTP Email Transmission
* **How It Works**: Communication with the `smtp.gmail.com` mail servers is established using secure configurations over SSL/TLS (port 587) with explicitly declared SSL protocol properties (TLS 1.2).
* **Credential Isolation**: SMTP mail credentials use an App Password, separating the mail dispatch flow from master account credentials.
* **Code References**:  
  Located in [`src/Shared/Security/EmailUtil.java`](src/Shared/Security/EmailUtil.java#L22-L48) (`sendMail()`).

### 9.2. Forgot Password 2FA / OTP Verification Flow
* **Step 1: Code Generation**: When a password reset is requested, the server generates a cryptographically random 6-digit integer and saves the reference in `resetOTPs` mapping to the user's email.
* **Step 2: Secure Delivery**: The server dispatches the code via `EmailUtil.sendMail()`.
* **Step 3: Verification**: The user submits the code along with their new password. The server validates the submitted code against the cache. If correct, the new password is hashed using BCrypt and saved, and the OTP is revoked.
* **Code References**:
  * **OTP Generation & Sending**:  
    Located in [`src/Server/handlers/AuthHandler.java`](src/Server/handlers/AuthHandler.java#L273-L280) (`handleForgotPassword()`).
  * **Verification & Password Reset Commit**:  
    Located in [`src/Server/handlers/AuthHandler.java`](src/Server/handlers/AuthHandler.java#L296-L318) (`handleResetPassword()`).

---

## 🛡️ 10. Administrator Key-Pair passwordless Auth

To secure the platform administration panel against credential leaks, administrators do not use traditional passwords. Instead, they authenticate using asymmetric **RSA Challenge-Response Signatures**.

```
Admin Client                                       Server
   │                                                 │
   ├───────── 1. ADMIN_CHALLENGE(username) ─────────▶│ [AuthHandler L69-81]
   │                                                 │
   │◀──────── 2. Return Random Challenge (Base64) ───┤ [ChallengeGenerator L8-12]
   │                                                 │
   │  3. Unlock PKCS12 admin_keys.p12 using pass     │
   │  4. Sign Challenge using Private Key            │
   │     (SHA256withRSA)                             │
   │                                                 │
   ├───────── 5. ADMIN_VERIFY(Signature) ───────────▶│ [AuthHandler L83-120]
   │                                                 │
   │                                                 │ 6. Fetch Admin Public Key from DB
   │                                                 │ 7. Verifier.verify(challenge, signature)
   │◀──────── 8. Auth Approved (Session Token) ──────┤ [Verifier L7-12]
   │                                                 │
   ▼                                                 ▼
```

### How It Works
1. **Challenge Request**: The admin client requests a challenge from the server (`ADMIN_CHALLENGE|<username>`).
2. **Challenge Generation**: The server verifies that the username matches an administrator account with an active public key registered. It generates a cryptographically random 32-byte challenge using `SecureRandom` and returns it to the client.
3. **Signature**: The admin selects their local PKCS12 keystore (`.p12` file) via the UI file chooser and unlocks it using their personal keystore password. The system extracts the private key from the selected vault and signs the raw challenge using `SHA256withRSA`.
4. **Verification**: The admin client sends the Base64 signature back to the server (`ADMIN_VERIFY|<username>|<signature>`).
5. **Session Creation**: The server retrieves the administrator's registered public key from the database and verifies the signature using `Signature.getInstance("SHA256withRSA")`. If valid, the session is approved.

### Code References
* **Admin Client Signature Generation**:  
  Located in [`src/Admin/Controllers/AdminLoginController.java`](src/Admin/Controllers/AdminLoginController.java#L70-L86).
* **Challenge Processing**:  
  Located in [`src/Server/handlers/AuthHandler.java`](src/Server/handlers/AuthHandler.java#L69-L81) (`handleAdminChallenge()`).
* **Verification Process**:  
  Located in [`src/Server/handlers/AuthHandler.java`](src/Server/handlers/AuthHandler.java#L83-L120) (`handleAdminVerify()`).
* **Crypto Helper Utilities**:
  * **Signing Class**: [`src/Shared/Security/Signer.java`](src/Shared/Security/Signer.java#L7-L12)
  * **Challenge Generator Class**: [`src/Shared/Security/ChallengeGenerator.java`](src/Shared/Security/ChallengeGenerator.java#L8-L12)
  * **Verification Class**: [`src/Shared/Security/Verifier.java`](src/Shared/Security/Verifier.java#L7-L12)

---

## 💳 11. Payment Data Privacy & Application Masking

Credit card information is highly sensitive and requires strict security measures.

### Storing Encrypted Credentials
* **How It Works**: Credit card numbers and CVV codes are **never stored in plaintext** in the database.
* **AES-256-GCM Storage**: Before database insertion, card numbers and CVVs are encrypted using AES-256-GCM. The encrypted ciphertext is stored as `Base64(IV):Base64(Ciphertext)`.
* **Code Reference**:  
  Located in [`src/Server/service/PaymentEncryptionService.java`](src/Server/service/PaymentEncryptionService.java#L23-L41).

### Application-Layer Card Masking
* **How It Works**: For display in UI list views, the system uses a masking routine that restricts visibility to the last 4 digits of the card (`****-****-****-XXXX`).
* **Code Reference**:  
  Located in [`src/Server/service/PaymentEncryptionService.java`](src/Server/service/PaymentEncryptionService.java#L58-L64) (`maskCard()`):
  ```java
  public String maskCard(String cardNumber) {
      if (cardNumber == null || cardNumber.length() < 4) return "****";
      String lastFour = cardNumber.substring(cardNumber.length() - 4);
      return "****-****-****-" + lastFour;
  }
  ```

---

## 🏢 12. Client & Admin Architectural Isolation

To prevent session overlap or unauthorized access to administration assets within user sessions, the **Client** and **Admin** codebases are separated at the structural, network, and package levels.

### How It Works
* **Isolated Networking Sockets**: The standard user application utilizes `Client.network.SocketClient` communicating on port `8085` (UDP notifications). The admin application uses an independent `Admin.network.AdminSocket` communicating on port `8086`. This ensures administration traffic does not interfere with standard user sessions.
* **Binary Separation**: The Maven build configuration builds separate executables. `ChriOnline-Client.jar` excludes admin packages, and `ChriOnline-Admin.jar` excludes user packages, preventing binary exploration or reverse engineering of admin codebases from standard client builds.
* **Separated Application State**: Admin state is tracked inside `Admin.session.AdminAppState`, isolating admin credentials, active profiles, and JWT tokens from standard user storage (`Client.session.AppState`).

### Code References
* **Admin State Isolation**: [`src/Admin/session/AdminAppState.java`](src/Admin/session/AdminAppState.java)
* **Isolated Socket Classes**:
  * Client Socket: [`src/Client/network/SocketClient.java`](src/Client/network/SocketClient.java)
  * Admin Socket: [`src/Admin/network/AdminSocket.java`](src/Admin/network/AdminSocket.java)
* **Separate Shaded Entry-points (Launchers)**:
  * Client entry-point: `ClientLauncher` targeting `ClientMain.java`
  * Admin entry-point: `AdminLauncher` targeting `AdminMain.java`

---

## 🧪 13. Security Verification Suite

Security controls are verified using a dedicated security test suite.

* **Test Suite Location**: [`src/Server/security/ReplayAttackTest.java`](src/Server/security/ReplayAttackTest.java)
* **Execution**: Run the `main()` method in `ReplayAttackTest.java` to perform the following verification checks:
  1. **Test 1**: Verify that `ReplayProtection` blocks repeated IVs within the 5-minute sliding window.
  2. **Test 2**: Verify that `SecureRandom` does not generate duplicate IVs across 1,000 iterations.
  3. **Test 3**: Verify complete AES-GCM encryption and decryption cycles.

---

### Central Cryptographic Parameters

| Paramètre | Algorithme | Taille de clé | Usage | Classe de Configuration |
| :--- | :--- | :--- | :--- | :--- |
| **Transport** | SSL/TLS 1.3 | 256 bits (AES) | Protection réseau | `CryptoConfig.java` |
| **Handshake** | RSA-OAEP-SHA256 | 2048 bits | Échange de clé session | `CryptoConfig.java` |
| **Symmetric** | AES-GCM | 256 bits | Échanges applicatifs | `CryptoConfig.java` |
| **Integrity** | GCM Auth Tag | 128 bits | Intégrité des messages | `CryptoConfig.java` |
| **Credential** | jBCrypt Salted | Cost 10 | Stockage mot de passe | `UserService.java` |
| **Admin Sign** | SHA256withRSA | 2048 bits | Authentification Admin | `AuthHandler.java` |
| **Session** | SHA-256 Hashing | 256 bits | Stockage session RAM | `SessionManager.java` |

---
*Report compiled by the ChriOnline Security Team — Document version 2.4.0.*

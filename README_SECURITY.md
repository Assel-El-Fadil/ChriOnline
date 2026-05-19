# 🔒 Rapport d'Architecture et d'Implémentation de Sécurité ChriOnline

Bienvenue dans la documentation technique exhaustive de la sécurité de la **Plateforme E-Commerce ChriOnline**. Ce document détaille chaque mesure de sécurité cryptographique, de session, réseau et applicative implémentée dans le code source. Il explique **comment fonctionne chaque fonctionnalité**, **comment elle est implémentée**, et fournit **les emplacements exacts du code ligne par ligne** pour garantir l'auditabilité.

---

## 🛡️ 1. Architecture de Défense en Profondeur Multiniveau

ChriOnline est conçu selon le paradigme de **Défense en Profondeur**. Plutôt que de s'appuyer sur un seul mécanisme de sécurité, plusieurs barrières indépendantes protègent le système. Si une couche de sécurité est compromise, les couches suivantes empêchent l'exploitation.

```
┌────────────────────────────────────────────────────────────────────────┐
│  COUCHE 1: Sécurité de Transport ── SSLSockets (TLS 1.3 / Port 8084)   │
├────────────────────────────────────────────────────────────────────────┤
│  COUCHE 2: Protection Réseau     ── Limitation de débit TCP & UDP,     │
│                                     Délais d'attente de connexion (30s)│
├────────────────────────────────────────────────────────────────────────┤
│  COUCHE 3: Cryptographie Hybride ── Échange de clé RSA-2048 / AES-256  │
├────────────────────────────────────────────────────────────────────────┤
│  COUCHE 4: Canal de Messages     ── Charges utiles chiffrées AES-256-GCM│
├────────────────────────────────────────────────────────────────────────┤
│  COUCHE 5: Intégrité & Rejeu     ── Balises d'authentification GCM     │
│                                     128 bits & Fenêtre glissante IV    │
├────────────────────────────────────────────────────────────────────────┤
│  COUCHE 6: Auth. & Identifiants  ── Hachage jBCrypt avec sel, Signatures│
│                                     Challenge-Response RSA sans mot de passe│
├────────────────────────────────────────────────────────────────────────┤
│  COUCHE 7: Intégrité de Session  ── Hachage mémoire de jeton SHA-256,  │
│                                     Déconnexion pour inactivité, Rotation (30m)│
├────────────────────────────────────────────────────────────────────────┤
│  COUCHE 8: Confidentialité Données─ Chiffrement de paiement AES-256-GCM│
│                                     & Découplage binaire strict        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 🌐 2. Sécurité de la Couche Transport & Sockets TLS

Toute communication réseau TCP brute est enveloppée dans une couche de socket sécurisée utilisant le protocole standard TLS. Cela garantit la confidentialité, l'authenticité et la vérification du serveur aux limites du réseau.

### Comment ça marche
* Le serveur héberge ses certificats dans un fichier sécurisé `keystore.p12` utilisant le format moderne PKCS12.
* Les clients ne font confiance qu'aux canaux de connexion qui correspondent aux signatures contenues dans leur fichier truststore local (`truststore.p12`). Cela protège la connexion contre les attaques de l'**Homme du Milieu (MitM)**.

### Références de Code
* **Constantes de Configuration Centralisées Partagées** :  
  Situé dans [`src/Shared/Security/CryptoConfig.java`](src/Shared/Security/CryptoConfig.java#L24-L34)
  ```java
  public static final String KEYSTORE_PATH     = "keystore.p12";
  public static final String KEYSTORE_PASSWORD  = "123456";
  public static final String KEYSTORE_ALIAS     = "ecommerce";
  public static final String TRUSTSTORE_PATH     = "truststore.p12";
  public static final String TRUSTSTORE_PASSWORD  = "123456";
  ```
* **Enregistrement du TrustStore Client** :  
  Situé dans [`src/Client/network/SocketClient.java`](src/Client/network/SocketClient.java#L29-L30)
  ```java
  System.setProperty("javax.net.ssl.trustStore", "truststore.p12");
  System.setProperty("javax.net.ssl.trustStorePassword", "123456");
  System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
  ```
* **Enregistrement du TrustStore Admin** :  
  Situé dans [`src/Admin/network/AdminSocket.java`](src/Admin/network/AdminSocket.java#L29-L30)
  ```java
  System.setProperty("javax.net.ssl.trustStore", "truststore.p12");
  System.setProperty("javax.net.ssl.trustStorePassword", "123456");
  System.setProperty("javax.net.ssl.trustStoreType", "PKCS12");
  ```
* **Chargement du KeyStore Serveur** :  
  Situé dans [`src/Server/security/SecureHandshake.java`](src/Server/security/SecureHandshake.java#L85-L103) (`loadKeyPairFromKeyStore()`).

---

## 🔒 3. Déni de Service Entrant, Inondation TCP & Délai de Connexion Incomplète

Le serveur est renforcé contre le **Déni de Service (DoS)**, l'**Inondation TCP (TCP Flood)**, et les attaques **Slowloris** qui tentent d'épuiser les limites de threads/connexions du serveur.

### Limitation du Débit des Connexions TCP
* **Comment ça marche** : Le serveur enregistre l'horodatage et le nombre de connexions de chaque adresse IP dans une fenêtre d'une minute. Si une IP dépasse `MAX_CONNECTIONS_PER_MINUTE` (50), le socket est immédiatement fermé.
* **Référence de Code** :  
  Situé dans [`src/Server/Server.java`](src/Server/Server.java#L150-L154) et l'aide `isRateLimited` à [`L195-L210`](src/Server/Server.java#L195-L210) :
  ```java
  if (isRateLimited(clientIP)) {
      logger.warn("[Server] TCP Flood: Trop de connexions depuis " + clientIP + ". Rejet.");
      clientSocket.close();
      continue;
  }
  ```

### Délai d'Attente de Connexion Incomplète (Protection Slowloris)
* **Comment ça marche** : Lorsqu'un client se connecte, le serveur définit immédiatement un délai d'attente de lecture de socket de 30 secondes (`setSoTimeout(30_000)`). Si un client initie une poignée de main TCP mais échoue à compléter le protocole Secure Handshake (l'envoi de la clé AES chiffrée) dans les 30 secondes, une exception `SocketTimeoutException` se déclenche et la connexion est fermée.
* **Passation de Session** : Une fois la poignée de main terminée et la première commande valide et déchiffrée reçue avec succès, le délai d'attente de lecture du socket est défini sur `0` (désactivé) pour permettre les états de socket actifs de longue durée normaux.
* **Références de Code** :
  * **Attribution Initiale du Délai d'Attente du Socket** :  
    Situé dans [`src/Server/Server.java`](src/Server/Server.java#L156) :
    ```java
    clientSocket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
    ```
  * **Traitement de la Première Commande & Réinitialisation du Délai d'Attente** :  
    Situé dans [`src/Server/ClientHandler.java`](src/Server/ClientHandler.java#L108-L111) :
    ```java
    if (!firstCommandReceived) {
        socket.setSoTimeout(0);
        firstCommandReceived = true;
    }
    ```
  * **Bloc Catch Slowloris** :  
    Situé dans [`src/Server/ClientHandler.java`](src/Server/ClientHandler.java#L139-L141) :
    ```java
    } catch (java.net.SocketTimeoutException e) {
        logger.warn("[ClientHandler] Connexion incomplète abandonnée de "
                + clientAddress + " (délai de poignée de main de 10s)");
    }
    ```

### Protection contre l'Inondation de Paquets UDP
* **Comment ça marche** : Un limiteur de débit en mémoire prévient les attaques par inondation UDP en suivant et en limitant les notifications sortantes par adresse IP de destination.
* **Référence de Code** :  
  Situé dans [`src/Server/UDPServer.java`](src/Server/UDPServer.java#L51-L54) et [`L76-L92`](src/Server/UDPServer.java#L76-L92) (`isUDPRateLimited()`).

---

## 🤝 4. Poignée de Main Cryptographique Hybride (RSA + AES)

Afin de combler l'écart entre l'asymétrie et la vitesse, ChriOnline utilise une **poignée de main cryptographique hybride** personnalisée de la couche application par-dessus TLS.

```
Client (Client / Admin)                            Serveur
   │                                                 │
   ├───────────── 1. Connexion (TLS) ────────────────▶│
   │                                                 │
   │◀── 2. Envoi de la Clé Publique RSA (Base64) ────┤ [SecureHandshake L40-44]
   │                                                 │
   │  3. Génération d'une clé AES-256 sécurisée      │
   │  4. Chiffrement de la clé AES avec la Clé       │
   │     Publique RSA du Serveur                     │
   │                                                 │
   ├───────── 5. Envoi de la Clé AES Chiffrée ───────▶│ [SecureHandshake L46-55]
   │                                                 │
   │                                                 │ 6. Déchiffrement de la clé AES
   │◀──────── 7. Poignée de Main Confirmée ──────────┤ [SecureHandshake L63-68]
   │                                                 │
   ▼ ══ Tout le trafic est chiffré via AES-256-GCM ══ ▼
```

### Comment ça marche
1. Lors de la connexion, le Serveur charge sa paire de clés RSA depuis le keystore central et envoie sa Clé Publique sous forme de chaîne encodée en Base64 (`SERVER_PUBLIC_KEY:`).
2. Le Client génère une clé symétrique AES sécurisée de 256 bits.
3. Le Client chiffre la clé AES en utilisant la Clé Publique RSA du serveur et la renvoie (`AES_KEY:<texte_chiffré>`).
4. Le Serveur déchiffre la clé symétrique en utilisant sa Clé Privée RSA. Les deux entités partagent désormais la même clé de session AES-256.

### Références de Code
* **Constantes Cryptographiques** : [`src/Shared/Security/CryptoConfig.java`](src/Shared/Security/CryptoConfig.java#L20-L22)
  * Mode : `RSA/ECB/OAEPWithSHA-256AndMGF1Padding` (le rembourrage OAEP est explicitement imposé pour se défendre contre les attaques d'oracle de rembourrage).
* **Routine de Poignée de Main Côté Serveur** :  
  Situé dans [`src/Server/security/SecureHandshake.java`](src/Server/security/SecureHandshake.java#L38-L77) (`perform()`).
* **Routine de Poignée de Main Côté Client** :  
  Situé dans [`src/Client/network/SecureKeyExchange.java`](src/Client/network/SecureKeyExchange.java#L15-L47).
* **Routine de Poignée de Main Côté Admin** :  
  Situé dans [`src/Admin/network/AdminSecureKeyExchange.java`](src/Admin/network/AdminSecureKeyExchange.java#L15-L48).

---

## ✉️ 5. Chiffrement des Messages AES-256-GCM & Intégrité

Une fois la clé de session établie, tout le trafic de paquets est chiffré pour empêcher l'interception ou la modification des paquets.

### Comment ça marche
* **Chiffrement Authentifié** : Nous utilisons `AES/GCM/NoPadding` (Mode Galois/Counter). Contrairement à CBC, GCM fournit **AEAD** (Chiffrement Authentifié avec Données Associées), qui génère une balise d'authentification de 128 bits avec chaque message. Cela prouve mathématiquement que le paquet n'a pas été altéré en transit.
* **Vecteur d'Initialisation (IV)** : Chaque message utilise un nouveau IV de 12 octets (96 bits) mathématiquement unique généré via `SecureRandom` pour éviter les modèles de texte chiffré.
* **Format Réseau** : Les paquets chiffrés sont transmis au format `Base64(IV):Base64(Ciphertext)`.

### Références de Code
* **Noyau Cryptographique (Utilitaire AES GCM)** :  
  Situé dans [`src/Shared/Security/AESUtil.java`](src/Shared/Security/AESUtil.java)
  * **Chiffrement** : [`L33-L45`](src/Shared/Security/AESUtil.java#L33-L45)
  * **Déchiffrement** : [`L51-L65`](src/Shared/Security/AESUtil.java#L51-L65)
* **Interception Côté Serveur** :  
  Situé dans [`src/Server/ClientHandler.java`](src/Server/ClientHandler.java#L104-L105) (`decryptMessage()`) et [`L131-L133`](src/Server/ClientHandler.java#L131-L133) (`encryptMessage()`).
* **Intégration du Chiffrement Côté Client** :  
  Situé dans [`src/Client/network/SocketClient.java`](src/Client/network/SocketClient.java#L56-L76).
* **Intégration du Chiffrement Côté Admin** :  
  Situé dans [`src/Admin/network/AdminSocket.java`](src/Admin/network/AdminSocket.java#L56-L76).

---

## 🔄 6. Protection Contre les Attaques par Rejeu (Fenêtre Glissante IV)

Une attaque par rejeu implique la capture d'une charge utile chiffrée valide (par exemple, une requête de transaction de caisse) et sa soumission à nouveau. Même si elle est chiffrée, répéter la charge utile amènerait le serveur à exécuter l'opération plusieurs fois.

### Comment ça marche
* Puisque chaque message chiffré utilise un IV unique et aléatoire, le serveur suit les IV vus.
* Le serveur maintient une `ConcurrentHashMap` des IV enregistrés associés à leur horodatage d'arrivée.
* Si un message entrant contient un IV déjà traité dans la **fenêtre glissante de 5 minutes**, il est immédiatement bloqué et enregistré comme une attaque.
* Les anciens IV sont automatiquement purgés de la mémoire pour garantir une utilisation limitée de la mémoire.

### Références de Code
* **Registre et Vérification des Rejeux** :  
  Situé dans [`src/Server/security/ReplayProtection.java`](src/Server/security/ReplayProtection.java)
  * **Vérification des Rejeux** : [`L50-L62`](src/Server/security/ReplayProtection.java#L50-L62)
  * **Enregistrement des IV** : [`L74-L83`](src/Server/security/ReplayProtection.java#L74-L83)
  * **Purge Automatique de la Mémoire** : [`L91-L94`](src/Server/security/ReplayProtection.java#L91-L94) (`cleanup()`).

---

## 🔑 7. Gestion des Jetons de Session, Protection Mémoire & Rotation

Les jetons de session (session tokens) sont des informations d'identification hautement sensibles. S'ils sont compromis, ils permettent à un acteur malveillant d'usurper l'identité d'utilisateurs ou d'administrateurs.

### 7.1. Jetons de Session Hachés en Mémoire
* **Comment ça marche** : Pour atténuer les attaques par vidage de mémoire (où un attaquant scanne la RAM du serveur en cours d'exécution pour extraire les jetons de session en clair), le serveur **hache tous les jetons de session** à l'aide de SHA-256 avant de les utiliser comme clés dans le répertoire de session.
* **Texte Clair Jamais Stocké** : Le UUID de session en texte clair est comparé ou récupéré en hachant d'abord le jeton d'entrée. Le jeton en texte clair n'est jamais mis en cache ni stocké.
* **Références de Code** :  
  Situé dans [`src/Server/SessionManager.java`](src/Server/SessionManager.java)
  * **Routine de Hachage** : [`L54-L68`](src/Server/SessionManager.java#L54-L68)
  * **Stockage Map (Clés Hachées)** : [`L79-L80`](src/Server/SessionManager.java#L79-L80)
  * **Recherche de Session Hachée** : [`L126-L129`](src/Server/SessionManager.java#L126-L129)

### 7.2. Rotation Transparente des Jetons (Régénération)
* **Comment ça marche** : Pour éviter le piratage/fixation de session, les jetons de session sont régénérés toutes les **30 minutes**.
* **Mise à Jour Transparente** : Lors de la distribution de commandes, si le jeton d'une session date de plus de 1800 secondes (30 minutes), le serveur génère un nouveau jeton, échange les métadonnées de la session et envoie la réponse préfixée par `"RENEWED_TOKEN:<newSessionToken>|||"`.
* **Passation Client** : Les sockets réseau Client et Admin interceptent de manière transparente ce préfixe, mettent à jour leurs états d'application respectifs, et traitent la charge utile de manière transparente.
* **Références de Code** :
  * **Régénération du Jeton de Session Côté Serveur** :  
    Situé dans [`src/Server/SessionManager.java`](src/Server/SessionManager.java#L98-L120) (`regenerateToken()`).
  * **Vérification de Rotation du Serveur & Construction de l'En-tête** :  
    Situé dans [`src/Server/ClientHandler.java`](src/Server/ClientHandler.java#L190-L200) et [`L212-L214`](src/Server/ClientHandler.java#L212-L214).
  * **Intégration du Jeton Transparent Côté Client** :  
    Situé dans [`src/Client/network/SocketClient.java`](src/Client/network/SocketClient.java#L78-L87).
  * **Intégration du Jeton Transparent Côté Admin** :  
    Situé dans [`src/Admin/network/AdminSocket.java`](src/Admin/network/AdminSocket.java#L75-L84).

### 7.3. Délai d'Expiration d'Inactivité de Session (AFK)
* **Comment ça marche** : Les sessions inactives sont automatiquement supprimées de la mémoire du serveur après **10 minutes** d'inactivité pour réduire l'utilisation de la mémoire et limiter la fenêtre d'exploitation des terminaux abandonnés.
* **Références de Code** :  
  Situé dans [`src/Server/SessionManager.java`](src/Server/SessionManager.java)
  * Constantes : [`L21`](src/Server/SessionManager.java#L21) (`MAX_IDLE_TIME_SECONDS = 600`)
  * Tâche Planifiée : [`L30-L40`](src/Server/SessionManager.java#L30-L40) (`cleanupIdleSessions()`).

---

## 👤 8. Hachage Fort des Mots de Passe (jBCrypt)

Les identifiants stockés doivent être hachés de manière sécurisée pour éviter leur exposition en cas de compromission de la base de données.

### Comment ça marche
* Les mots de passe des clients ne sont pas stockés à l'aide d'algorithmes de hachage rapides comme MD5 ou SHA-1, qui sont vulnérables aux attaques par force brute accélérées par matériel.
* ChriOnline utilise **jBCrypt** (une fonction de hachage adaptative basée sur Blowfish) pour hacher les identifiants.
* BCrypt implémente un facteur de coût adaptatif (facteur de travail) et incorpore un sel cryptographique explicite, rendant les tables arc-en-ciel et la force brute par GPU informatiquement irréalisables.

### Références de Code
* **Hachage de Mot de Passe et Génération de Sel** :  
  Situé dans [`src/Server/service/UserService.java`](src/Server/service/UserService.java#L201) :
  ```java
  return BCrypt.hashpw(plainTextPassword, BCrypt.gensalt());
  ```
* **Vérification du Mot de Passe** :  
  Situé dans [`src/Server/service/UserService.java`](src/Server/service/UserService.java#L205) :
  ```java
  return BCrypt.checkpw(plainTextPassword, hashedPassword);
  ```

---

## 📧 9. 2FA Sécurisé, OTP & Communications Mail SMTP

Un flux de **Mot de passe oublié** et un mécanisme de connexion sécurisés sont mis en œuvre via une vérification par e-mail sécurisée.

### 9.1. Transmission d'E-mail SMTP Sécurisée
* **Comment ça marche** : La communication avec les serveurs de messagerie `smtp.gmail.com` est établie à l'aide de configurations sécurisées sur SSL/TLS (port 587) avec des propriétés de protocole SSL explicitement déclarées (TLS 1.2).
* **Isolation des Identifiants** : Les identifiants de messagerie SMTP utilisent un Mot de passe d'Application, séparant le flux d'expédition des e-mails des identifiants du compte principal.
* **Références de Code** :  
  Situé dans [`src/Shared/Security/EmailUtil.java`](src/Shared/Security/EmailUtil.java#L22-L48) (`sendMail()`).

### 9.2. Flux de Vérification Mot de Passe Oublié 2FA / OTP
* **Étape 1 : Génération du Code** : Lorsqu'une réinitialisation de mot de passe est demandée, le serveur génère un entier à 6 chiffres cryptographiquement aléatoire et enregistre la référence dans `resetOTPs` en la liant à l'e-mail de l'utilisateur.
* **Étape 2 : Livraison Sécurisée** : Le serveur envoie le code via `EmailUtil.sendMail()`.
* **Étape 3 : Vérification** : L'utilisateur soumet le code avec son nouveau mot de passe. Le serveur valide le code soumis par rapport au cache. S'il est correct, le nouveau mot de passe est haché à l'aide de BCrypt et sauvegardé, et le OTP est révoqué.
* **Références de Code** :
  * **Générations & Envoi de l'OTP** :  
    Situé dans [`src/Server/handlers/AuthHandler.java`](src/Server/handlers/AuthHandler.java#L273-L280) (`handleForgotPassword()`).
  * **Vérification & Validation de Réinitialisation du Mot de Passe** :  
    Situé dans [`src/Server/handlers/AuthHandler.java`](src/Server/handlers/AuthHandler.java#L296-L318) (`handleResetPassword()`).

---

## 🛡️ 10. Authentification Admin par Paire de Clés Sans Mot de Passe

Pour sécuriser le panneau d'administration de la plateforme contre les fuites d'identifiants, les administrateurs n'utilisent pas de mots de passe traditionnels. Au lieu de cela, ils s'authentifient à l'aide de **Signatures Challenge-Response RSA** asymétriques.

```
Client Admin                                       Serveur
   │                                                 │
   ├──────── 1. ADMIN_CHALLENGE(nom_utilisateur) ───▶│ [AuthHandler L69-81]
   │                                                 │
   │◀─────── 2. Retour Challenge Aléatoire (Base64) ─┤ [ChallengeGenerator L8-12]
   │                                                 │
   │  3. Déverrouillage PKCS12 (.p12) via UI file    │
   │     chooser et mot de passe de keystore         │
   │  4. Signer Challenge avec la Clé Privée         │
   │     (SHA256withRSA)                             │
   │                                                 │
   ├───────── 5. ADMIN_VERIFY(Signature) ───────────▶│ [AuthHandler L83-120]
   │                                                 │
   │                                                 │ 6. Récup. Clé Publique Admin (DB)
   │                                                 │ 7. Verifier.verify(challenge, signature)
   │◀──────── 8. Auth Approuvée (Jeton de Session) ──┤ [Verifier L7-12]
   │                                                 │
   ▼                                                 ▼
```

### Comment ça marche
1. **Demande de Challenge** : Le client admin demande un challenge au serveur (`ADMIN_CHALLENGE|<nom_utilisateur>`).
2. **Génération du Challenge** : Le serveur vérifie que le nom d'utilisateur correspond à un compte administrateur avec une clé publique active enregistrée. Il génère un challenge cryptographiquement aléatoire de 32 octets à l'aide de `SecureRandom` et le renvoie au client.
3. **Signature** : L'administrateur sélectionne son keystore local PKCS12 (fichier `.p12`) via le sélecteur de fichiers de l'interface utilisateur et le déverrouille en utilisant son mot de passe personnel de keystore. Le système extrait la clé privée du coffre sélectionné et signe le challenge brut à l'aide de `SHA256withRSA`.
4. **Vérification** : Le client admin renvoie la signature en Base64 au serveur (`ADMIN_VERIFY|<nom_utilisateur>|<signature>`).
5. **Création de Session** : Le serveur récupère la clé publique enregistrée de l'administrateur dans la base de données et vérifie la signature à l'aide de `Signature.getInstance("SHA256withRSA")`. Si elle est valide, la session est approuvée.

### Références de Code
* **Génération de Signature du Client Admin** :  
  Situé dans [`src/Admin/Controllers/AdminLoginController.java`](src/Admin/Controllers/AdminLoginController.java#L70-L86).
* **Traitement du Challenge** :  
  Situé dans [`src/Server/handlers/AuthHandler.java`](src/Server/handlers/AuthHandler.java#L69-L81) (`handleAdminChallenge()`).
* **Processus de Vérification** :  
  Situé dans [`src/Server/handlers/AuthHandler.java`](src/Server/handlers/AuthHandler.java#L83-L120) (`handleAdminVerify()`).
* **Utilitaires Assistants Crypto** :
  * **Classe de Signature** : [`src/Shared/Security/Signer.java`](src/Shared/Security/Signer.java#L7-L12)
  * **Classe de Générateur de Challenge** : [`src/Shared/Security/ChallengeGenerator.java`](src/Shared/Security/ChallengeGenerator.java#L8-L12)
  * **Classe de Vérification** : [`src/Shared/Security/Verifier.java`](src/Shared/Security/Verifier.java#L7-L12)

---

## 💳 11. Confidentialité des Données de Paiement & Masquage de l'Application

Les informations de carte de crédit sont hautement sensibles et nécessitent des mesures de sécurité strictes.

### Stockage des Identifiants Chiffrés
* **Comment ça marche** : Les numéros de carte de crédit et les codes CVV ne sont **jamais stockés en clair** dans la base de données.
* **Stockage AES-256-GCM** : Avant l'insertion dans la base de données, les numéros de carte et les CVV sont chiffrés à l'aide d'AES-256-GCM. Le texte chiffré est stocké sous la forme `Base64(IV):Base64(Ciphertext)`.
* **Référence de Code** :  
  Situé dans [`src/Server/service/PaymentEncryptionService.java`](src/Server/service/PaymentEncryptionService.java#L23-L41).

### Masquage de Carte de Couche Application
* **Comment ça marche** : Pour l'affichage dans les vues de liste de l'interface utilisateur, le système utilise une routine de masquage qui restreint la visibilité aux 4 derniers chiffres de la carte (`****-****-****-XXXX`).
* **Référence de Code** :  
  Situé dans [`src/Server/service/PaymentEncryptionService.java`](src/Server/service/PaymentEncryptionService.java#L58-L64) (`maskCard()`) :
  ```java
  public String maskCard(String cardNumber) {
      if (cardNumber == null || cardNumber.length() < 4) return "****";
      String lastFour = cardNumber.substring(cardNumber.length() - 4);
      return "****-****-****-" + lastFour;
  }
  ```

---

## 🧪 12. Suite de Vérification de Sécurité

Les contrôles de sécurité sont vérifiés à l'aide d'une suite de tests de sécurité dédiée.

* **Emplacement de la Suite de Tests** : [`src/Server/security/ReplayAttackTest.java`](src/Server/security/ReplayAttackTest.java)
* **Exécution** : Exécutez la méthode `main()` dans `ReplayAttackTest.java` pour effectuer les vérifications suivantes :
  1. **Test 1** : Vérifiez que `ReplayProtection` bloque les IV répétés dans la fenêtre glissante de 5 minutes.
  2. **Test 2** : Vérifiez que `SecureRandom` ne génère pas de IV en double sur 1 000 itérations.
  3. **Test 3** : Vérifiez les cycles complets de chiffrement et de déchiffrement AES-GCM.

---

### Paramètres Cryptographiques Centraux

| Paramètre | Algorithme | Taille de clé | Usage | Classe de Configuration |
| :--- | :--- | :--- | :--- | :--- |
| **Transport** | SSL/TLS 1.3 | 256 bits (AES) | Protection réseau | `CryptoConfig.java` |
| **Handshake** | RSA-OAEP-SHA256 | 2048 bits | Échange de clé session | `CryptoConfig.java` |
| **Symétrique** | AES-GCM | 256 bits | Échanges applicatifs | `CryptoConfig.java` |
| **Intégrité** | GCM Auth Tag | 128 bits | Intégrité des messages | `CryptoConfig.java` |
| **Identifiant** | jBCrypt Salted | Cost 10 | Stockage mot de passe | `UserService.java` |
| **Admin Sign** | SHA256withRSA | 2048 bits | Authentification Admin | `AuthHandler.java` |
| **Session** | Hachage SHA-256 | 256 bits | Stockage session RAM | `SessionManager.java` |

---
*Rapport compilé par l'Équipe de Sécurité ChriOnline — Version du document 2.4.0.*

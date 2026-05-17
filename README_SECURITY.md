# 🔒 README_SECURITY — ChriOnline : Architecture de Sécurité

> Documentation technique de sécurité du projet **ChriOnline E-Commerce**.
> Rédigée par **Membre 3 (Crypto & Tests)** en coordination avec **Membre 1** et **Membre 2**.

---

## 1. Architecture de Sécurité

ChriOnline adopte une stratégie de **défense en profondeur** combinant plusieurs couches de sécurité indépendantes :

```
┌─────────────────────────────────────────────────────────────┐
│  COUCHE 1 — Transport : SSLSocket (TLS 1.3)                 │
│  COUCHE 2 — Session   : Échange de clé RSA-2048             │
│  COUCHE 3 — Channel   : Chiffrement AES-256-GCM par message │
│  COUCHE 4 — Intégrité : Tag GCM 128-bit + HMAC-SHA256       │
│  COUCHE 5 — Rejeu     : IV unique 12 octets par message     │
│  COUCHE 6 — Mémoire   : Tokens de session hachés (SHA-256)  │
└─────────────────────────────────────────────────────────────┘
```

### Composants cryptographiques principaux

| Composant | Classe | Rôle |
|-----------|--------|------|
| Configuration centrale | `CryptoConfig.java` | Constantes partagées (algorithmes, tailles de clé, séparateur IV) |
| Utilitaire AES | `AESUtil.java` | Chiffrement / déchiffrement AES-GCM, encodage de clé |
| Gestionnaire IV | `IVManager.java` | Génération d'IVs aléatoires, registre thread-safe |
| Handshake sécurisé | `SecureHandshake.java` | Échange RSA → clé AES de session |
| Protection rejeu | `ReplayProtection.java` | Vérification unicité des IVs (fenêtre 5 min) |
| Chiffrement paiement | `PaymentEncryptionService.java` | Chiffrement numéros de carte et CVV avant stockage |
| Gestion session | `SessionManager.java` | Tokens SHA-256 en mémoire, expiration automatique |

---

## 2. Flux de Connexion Sécurisé

Le protocole d'établissement de session suit **6 étapes** :

```
Client JavaFX                          Serveur SSL
     │                                      │
     │──── 1. Connexion SSLSocket ─────────▶│  TLS 1.3 établi (port 8084)
     │                                      │
     │◀─── 2. SERVER_PUBLIC_KEY:<Base64> ───│  Serveur envoie sa clé publique RSA
     │                                      │
     │  3. Client vérifie le certificat     │
     │     via son TrustStore local         │
     │                                      │
     │  4. Client génère une clé AES-256    │
     │     aléatoire (SecureRandom)         │
     │                                      │
     │──── 5. AES_KEY:<Base64(RSA(key))> ──▶│  Clé AES chiffrée avec RSA public
     │                                      │
     │◀─── 6. HANDSHAKE_OK ────────────────│  Serveur déchiffre → session AES établie
     │                                      │
     │    ══ Canal AES-GCM actif ══════════▶│
```

À l'issue du handshake, **les deux parties partagent la même clé AES de session** sans qu'elle ait jamais transité en clair sur le réseau.

---

## 3. Chiffrement des Communications

Chaque commande échangée entre le client et le serveur est intégralement chiffrée avec la clé AES de session.

### Algorithme utilisé
- **Mode** : `AES/GCM/NoPadding`
- **Taille de clé** : 256 bits
- **IV** : 12 octets aléatoires générés par `SecureRandom` — **un IV unique par message**
- **Tag d'authentification** : 128 bits (garantit intégrité + authenticité)

### Format des messages chiffrés

```
Base64(IV) + ":" + Base64(GCM_Ciphertext)
```

Exemple (fictif) :
```
a1B2c3D4e5F6g7H8i9J0kA==:7fHs9XmPqRtVzLwNbYcDkEoUiA...
```

### Commandes protégées

| Commande | Données sensibles chiffrées |
|----------|-----------------------------|
| `LOGIN` | Identifiant, mot de passe |
| `GET_PRODUCTS` | Catalogue (confidentialité B2B) |
| `CART_ADD` | Références produits, quantités |
| `CHECKOUT` | Numéro de carte, CVV, montant, adresse |

---

## 4. Protection Contre le Rejeu

Une attaque par rejeu consiste à intercepter un message chiffré valide et à le renvoyer ultérieurement pour déclencher une action non autorisée (ex. : valider deux fois un paiement).

### Mécanisme de protection

- **IV unique par message** : `SecureRandom.nextBytes(12)` garantit qu'aucun IV n'est jamais réutilisé.
- **Registre des IVs vus** : Le serveur maintient un `ConcurrentHashMap<String, Long>` des IVs déjà traités, avec l'horodatage de réception (`System.currentTimeMillis()`).
- **Fenêtre de protection** : 5 minutes. Tout message dont l'IV a déjà été vu dans les 5 dernières minutes est **rejeté immédiatement**.
- **Nettoyage automatique** : `cleanup()` est appelé à chaque `register()` pour supprimer les entrées expirées et maintenir la mémoire bornée.

### Flux de vérification (côté serveur)

```
Message entrant
       │
       ▼
isReplay(ivBase64) ?
   ├── OUI → Rejeter le message (attaque par rejeu détectée)
   └── NON → register(ivBase64) → déchiffrer → traiter
```

**Classe responsable** : `Server.security.ReplayProtection` (Singleton thread-safe)

---

## 5. Sécurisation des Données de Paiement

Les données de paiement (numéro de carte, CVV) ne sont **jamais stockées en clair** dans la base de données.

### Stockage chiffré

| Donnée | Traitement avant stockage |
|--------|--------------------------|
| Numéro de carte | `AESUtil.encrypt(cardNumber, sessionKey)` → Base64 `IV:CT` |
| CVV | `AESUtil.encrypt(cvv, sessionKey)` → Base64 `IV:CT` |
| Affichage | `maskCardNumber(card)` → `****-****-****-XXXX` (4 derniers chiffres uniquement) |

### Règles de sécurité appliquées

- La clé AES n'est **jamais codée en dur** — elle est injectée uniquement via le constructeur de `PaymentEncryptionService`.
- Chaque chiffrement produit un **IV différent** (fraîcheur garantie par `IVManager.generateIV()`).
- Le masquage (`maskCardNumber`) est **purement applicatif** — aucune donnée chiffrée n'est utilisée pour l'affichage.
- Aucun schéma de base de données n'a été modifié ; les colonnes existantes reçoivent les chaînes Base64 chiffrées.

**Classe responsable** : `Server.service.PaymentEncryptionService`

---

## 6. Choix Techniques et Justifications

### AES-GCM plutôt qu'AES-CBC

| Critère | AES-CBC | AES-GCM ✅ |
|---------|---------|-----------|
| Confidentialité | ✅ | ✅ |
| Intégrité (authentification) | ❌ (nécessite HMAC séparé) | ✅ (tag GCM intégré) |
| Résistance au bit-flipping | ❌ | ✅ |
| Performance | Correcte | Meilleure (parallélisable) |
| Standard NIST | Oui | Oui — recommandé (SP 800-38D) |

AES-GCM est un mode de **chiffrement authentifié** (AEAD) : il garantit à la fois la confidentialité et l'intégrité en une seule opération, sans HMAC externe supplémentaire.

### Taille de clé AES : 256 bits
- Le standard NIST recommande AES-256 pour les données à protéger au-delà de 2030.
- Résistance aux attaques par force brute : 2²⁵⁶ combinaisons possibles.

### IV de 12 octets (96 bits)
- Recommandation explicite du NIST (SP 800-38D) pour GCM : longueur optimale permettant d'éviter le compteur interne de s'approcher du dépassement.
- Toute autre longueur nécessite un calcul supplémentaire (GHASH), réduisant les performances et la sécurité.

### RSA-2048 pour l'échange de clé
- Niveau de sécurité équivalent à ~112 bits symétriques.
- Minimum recommandé par le NIST jusqu'en 2030 pour l'échange de clés asymétriques.
- Utilisé uniquement pour le handshake initial — la charge utile après est exclusivement AES-GCM.

### HMAC-SHA256
- Hash à message authentifié basé sur SHA-256 (résistance à la collision : 2¹²⁸).
- Utilisé comme couche d'intégrité additionnelle pour les tokens de session.
- Déclaré dans `CryptoConfig.HMAC_ALGORITHM`.

---

## 7. Tests de Sécurité Effectués

Les tests sont implémentés dans `Server.security.ReplayAttackTest` — exécutable via `main()`, sans dépendance JUnit.

### TEST 1 — Détection de rejeu basique

**Objectif** : Vérifier que `ReplayProtection` détecte correctement un IV déjà utilisé.

**Étapes** :
1. Génération d'une clé AES-256 aléatoire.
2. Chiffrement de `"CHECKOUT|token|CARD|1234"` → extraction de l'IV (partie avant `:`).
3. Enregistrement de l'IV dans `ReplayProtection`.
4. Nouvelle tentative avec le **même IV** → doit être détectée comme rejeu.
5. Nouveau chiffrement (→ nouvel IV aléatoire) → doit être accepté comme message frais.

**Résultats attendus** :
```
TEST 1 PASSED: Replay detected
TEST 1 PASSED: Fresh message accepted
```

---

### TEST 2 — Unicité des IVs sur 1 000 chiffrements

**Objectif** : Confirmer que `SecureRandom` ne génère jamais deux fois le même IV.

**Étapes** :
1. Chiffrement du même texte clair 1 000 fois avec la même clé.
2. Extraction de chaque IV et insertion dans un `HashSet<String>`.
3. Si `HashSet.add()` retourne `false` → collision détectée → échec.

**Résultat attendu** :
```
TEST 2 PASSED: All 1000 IVs are unique
```

> La probabilité théorique d'une collision pour des IVs de 96 bits est de l'ordre de 2⁻⁹⁶ — négligeable en pratique.

---

### TEST 3 — Cycle chiffrement / déchiffrement AES-GCM

**Objectif** : Valider l'intégrité fonctionnelle complète d'`AESUtil`.

**Données de test** :
```
CHECKOUT|token123|CARD|4111111111111111|123|12/26
```

**Étapes** :
1. `AESUtil.encrypt(plaintext, key)` → chaîne Base64 `IV:CT`.
2. `AESUtil.decrypt(encoded, key)` → texte déchiffré.
3. Comparaison `original.equals(decrypted)`.

**Résultat attendu** :
```
TEST 3 PASSED: Roundtrip successful
  Original : CHECKOUT|token123|CARD|4111111111111111|123|12/26
  Encrypted: <Base64IV>:<Base64CT>
  Decrypted: CHECKOUT|token123|CARD|4111111111111111|123|12/26
```

---

## Récapitulatif des fichiers produits (Membre 3)

| Fichier | Package | Rôle |
|---------|---------|------|
| `AESUtil.java` *(corrigé)* | `Shared.Security` | Chiffrement / déchiffrement AES-GCM complet |
| `CryptoConfig.java` *(complété)* | `Shared.Security` | Ajout de `HMAC_ALGORITHM` et `IV_SEPARATOR` |
| `IVManager.java` | `Shared.Security` | Génération et registre thread-safe des IVs |
| `ReplayProtection.java` | `Server.security` | Détection d'attaques par rejeu |
| `PaymentEncryptionService.java` | `Server.service` | Chiffrement des données de paiement |
| `ReplayAttackTest.java` | `Server.security` | Tests manuels de sécurité (3 tests) |
| `README_SECURITY.md` | Racine du projet | Cette documentation |

---

*Rédigé par **Membre 3 (Crypto & Tests)** — Projet ChriOnline, sécurisation des communications e-commerce.*

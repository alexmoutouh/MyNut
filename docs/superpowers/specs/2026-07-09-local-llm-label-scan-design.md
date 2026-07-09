# Scan d'étiquette nutritionnelle via LLM local (offline)

Date : 2026-07-09
Statut : validé (brainstorming), en attente du plan d'implémentation

## Contexte

MyNut sait déjà photographier une étiquette nutritionnelle et préremplir un
`NutItem` : `FormScreen` capture une photo (caméra système + `FileProvider`),
`FormViewModel.scanPhoto(imageBytes)` l'envoie à `LabelScanApi.scanLabel`, qui
POST l'image en multipart vers `{baseUrl}/scan` (backend externe, actuellement
`http://10.0.2.2:8080`) et parse une réponse JSON en `NutritionalValues`
(`calories`, `fats`, `saturatedFats`, `carbs`, `sugars`, `fiber`, `proteins`,
`sodium`, tous nullable).

Objectif de cette itération : offrir une alternative **entièrement
offline** à ce backend HTTP, en faisant tourner l'inférence directement sur
l'appareil, sans qu'aucune donnée ni requête réseau ne soit nécessaire à
l'usage courant (une seule exception ponctuelle : le téléchargement initial
du modèle, voir plus bas).

## Décisions de cadrage

- **Portée** : on implémente le moteur local **sans toucher à `LabelScanApi`**
  ni au backend HTTP existant. Le choix final (remplacement, cohabitation
  avec bascule dans les réglages, etc.) est explicitement reporté à plus
  tard.
- **Distribution du modèle** : le fichier de poids (`.gguf`) est **téléchargé
  au premier usage** de la fonctionnalité depuis une URL fixe, puis mis en
  cache local (`context.filesDir/models/`). Une alternative "import manuel"
  façon LM Studio a été envisagée et est documentée en fin de spec, mais non
  retenue comme mécanisme principal pour cette itération.
- **Performance** : aucune contrainte forte imposée. On démarre CPU-only
  (pas d'accélération GPU/NNAPI), à réévaluer empiriquement si les temps de
  scan mesurés sont jugés trop longs.
- **Réversibilité de l'approche** : le design doit permettre de basculer plus
  tard vers un pipeline différent (VLM multimodal direct, voir "Approches
  envisagées") sans réécrire l'appelant. Ce besoin explicite justifie
  l'unique abstraction introduite par cette spec (`LocalLabelScanEngine`) —
  en dérogation à la convention du projet ("pas de couche de repository",
  cf. `CLAUDE.md`), strictement limitée à ce point d'extension.

## Approches envisagées

Deux briques sont possibles pour l'extraction : lire la photo directement
avec un modèle multimodal (vision), ou passer par de l'OCR puis un LLM texte.
Trois variantes ont été comparées :

| Approche | Description | Verdict |
|---|---|---|
| **A — OCR + LLM texte** | ML Kit Text Recognition (OCR on-device) extrait le texte brut, puis un petit LLM texte (llama.cpp) le structure en JSON. | **Retenue.** Chaque brique est mature ; le besoin réel ("extraire des champs numériques d'un texte") est un cas d'usage texte classique, large choix de petits modèles. |
| B — VLM multimodal direct | Un modèle vision (Moondream2/SmolVLM/Llava quantisé + fichier `mmproj`) lit la photo et sort le JSON directement, via un binding JNI llama.cpp maison. | Non implémentée maintenant, mais l'architecture (interface `LocalLabelScanEngine`) prépare son insertion future si l'OCR déçoit en pratique. |
| C — VLM via wrapper llama.cpp tiers | Comme B, mais en s'appuyant sur une lib Android communautaire packageant déjà llama.cpp au lieu d'un binding maison. | Écartée : cumule le risque du multimodal (support jeune/fragile dans llama.cpp) et celui d'une dépendance tierce peu maintenue sur cette même partie expérimentale. |

## Architecture

```
FormScreen (capture photo, inchangé)
        │ imageBytes
        ▼
FormViewModel.scanPhotoLocally(imageBytes)
        │
        ▼
LocalLabelScanEngine  (interface — point d'échange A ↔ B)
        │
        ▼ (implémentation de cette itération)
OcrTextLlmScanEngine
   ├─ 1. TextRecognizer (ML Kit, on-device)      image → texte brut
   ├─ 2. LlamaTextEngine (binding JNI llama.cpp)  texte + prompt → JSON
   └─ 3. parseResponse(json) → NutritionalValues  (même pattern que LabelScanApi)
        │
        ▼
Result<NutritionalValues>  →  FormUiState (mêmes champs que le scan HTTP actuel)
```

`LabelScanApi` reste inchangé et continue de fonctionner comme aujourd'hui,
en parallèle.

## Composants

### `LocalLabelScanEngine` (nouveau, `data/LocalLabelScanEngine.kt`)

Seule abstraction introduite. Une méthode, même signature que
`LabelScanApi.scanLabel` :

```kotlin
interface LocalLabelScanEngine {
    suspend fun scanLabel(imageBytes: ByteArray): Result<NutritionalValues>
}
```

### `OcrTextLlmScanEngine` (nouveau, implémente `LocalLabelScanEngine`)

Orchestration pure Kotlin, sans logique métier native :

1. `TextRecognizer` de ML Kit (`com.google.mlkit:text-recognition`, modèle
   latin embarqué on-device) : `InputImage` → texte brut. Si aucun texte
   n'est détecté, retourne immédiatement `Result.failure` (évite un appel LLM
   inutile).
2. Construit un prompt fixe demandant une réponse JSON stricte avec les clés
   `calories/fats/saturatedFats/carbs/sugars/fiber/proteins/sodium`, y injecte
   le texte OCR, l'envoie à `LlamaTextEngine.complete(prompt)`.
3. Parse la réponse en `NutritionalValues`, avec extraction tolérante (si le
   JSON n'est pas strictement propre, on extrait le premier bloc `{...}`
   avant d'abandonner) — même esprit que `optDoubleOrNull` dans
   `LabelScanApi`.

### `LlamaTextEngine` (nouveau, `data/llm/`)

Wrapper Kotlin autour d'un binding JNI llama.cpp **texte seul** (pas de
vision, pas de fichier `mmproj` pour cette itération) :

- `suspend fun complete(prompt: String): String`, exécuté sur un dispatcher
  dédié à un seul thread (le contexte llama.cpp n'est pas thread-safe).
- Chargement **paresseux** au premier appel (pas au démarrage de l'app), puis
  résident en mémoire pour le reste du processus — même pattern que
  `database`/`labelScanApi` dans `MyNutApplication` (`by lazy`), sauf que le
  coût mémoire (~1 Go) impose de ne charger qu'à la première utilisation
  réelle de la fonctionnalité.
- Binding JNI minimal suivant `llama.cpp/examples/llama.android` :
  `loadModel(path)`, `complete(prompt): String`, `unload()`.

### `LocalModelManager` (nouveau)

Gère le cycle de vie du fichier `.gguf` :

- Vérifie sa présence dans `context.filesDir/models/`.
- S'il est absent : le télécharge depuis une URL fixe (Hugging Face) vers un
  fichier temporaire, vérifie un checksum SHA-256 connu, puis le déplace à sa
  place finale. Expose l'avancement via un flux d'état
  (`Idle` / `Downloading(progress)` / `Ready` / `Failed`).
- Pas de reprise (resume) : une interruption du téléchargement (app fermée,
  écran quitté, réseau perdu) laisse le fichier temporaire incomplet, rejeté
  au prochain essai — on retélécharge depuis le début.
- `LlamaTextEngine` dépend de `LocalModelManager` pour obtenir le chemin du
  fichier avant de charger.

## UX et flux de données

`FormScreen` garde le bouton caméra existant, inchangé, toujours relié à
`LabelScanApi`. Un **second bouton provisoire** "Scanner (IA locale)" est
ajouté pour permettre de comparer les deux moteurs pendant la validation — la
décision finale sur l'UI définitive (remplacement / cohabitation / toggle
réglages) reste hors scope (voir plus bas).

`FormUiState` gagne un champ :

```kotlin
val modelDownloadProgress: Float? = null  // null = pas de téléchargement en cours
```

(`scanInProgress` et `errorMessage` existants sont réutilisés tels quels pour
l'étape OCR+LLM.)

Séquence de `FormViewModel.scanPhotoLocally(imageBytes)` :

1. `LocalModelManager.ensureModelReady()` → si le fichier `.gguf` est
   absent, une **boîte de dialogue de confirmation** s'affiche d'abord ("Le
   modèle IA (~1 Go) doit être téléchargé une fois pour activer le scan
   local. Continuer ?") avant tout accès réseau — pas de téléchargement
   silencieux d'1 Go.
2. Si confirmé : téléchargement avec suivi de progression
   (`modelDownloadProgress` 0→1). En cas d'échec (pas de réseau, serveur
   inaccessible) : `errorMessage` explicite, aucun crash, nouvelle tentative
   possible en retapant le bouton.
3. Modèle prêt → `scanInProgress = true`, exécution de
   `OcrTextLlmScanEngine.scanLabel(imageBytes)` (mêmes indicateurs visuels
   que le scan HTTP existant).
4. Résultat : même logique de fusion que `scanPhoto` actuel — un champ
   retourné non-null écrase le champ existant, sinon la valeur du formulaire
   est conservée.

Une fois le modèle chargé en mémoire, les scans suivants de la même session
app sautent le téléchargement et le chargement — seule la latence
OCR+inférence reste.

## Gestion d'erreurs

Toutes les erreurs remontent via le pattern déjà en place
(`Result<NutritionalValues>` → `errorMessage` dans `FormUiState`), pas de
nouveau mécanisme :

| Cas | Comportement |
|---|---|
| Pas de réseau au 1er téléchargement du modèle | `errorMessage` explicite ("Téléchargement impossible, vérifie ta connexion"), aucun crash, réessai possible |
| Téléchargement corrompu (checksum SHA-256 invalide) | Fichier rejeté, pas placé en `models/`, même message d'erreur + nouvelle tentative au prochain essai |
| Échec de chargement du modèle (OOM, fichier illisible) | `LlamaTextEngine` catch et retourne `Result.failure`, message "Modèle IA indisponible sur cet appareil" |
| OCR ne détecte aucun texte (photo floue, pas d'étiquette) | `Result.failure` avant même d'appeler le LLM ("Aucun texte détecté sur la photo") |
| LLM répond avec un JSON malformé | Parsing tolérant ; si toujours invalide, `Result.failure` "Réponse IA illisible, réessaie" |
| Résultat partiel (LLM ne remplit que certains champs) | Comportement actuel conservé : seuls les champs non-null écrasent le formulaire, pas d'erreur bloquante |

Aucun de ces cas ne doit faire planter l'app.

## Tests

**`app/src/test` (JVM pur)** :
- Parsing de la réponse LLM → `NutritionalValues` (JSON valide, JSON entouré
  de texte parasite, champs manquants/null, JSON invalide).
- Construction du prompt (texte OCR → prompt final), fonction pure.
- `LocalModelManager` : logique de vérification checksum et décision
  "faut-il télécharger", isolée en fonctions pures (le téléchargement réseau
  est mocké).

**`app/src/androidTest` (instrumenté)** :
- Smoke test du binding JNI avec un modèle minuscule dédié aux tests (pas le
  modèle de prod) : charger + compléter sans crash. Lent, à exécuter
  manuellement plutôt qu'en CI systématique.
- `TextRecognizer` ML Kit sur une image d'étiquette réelle en asset de test.
- Test de bout en bout `OcrTextLlmScanEngine.scanLabel` sur une vraie photo
  d'étiquette (asset), best-effort : on vérifie qu'un `Result.success` avec
  au moins quelques champs non-null est retourné, pas des valeurs exactes
  (nature probabiliste du LLM).

Pas de test d'intégration réseau réel pour le téléchargement du modèle (trop
lourd/flaky en CI) — uniquement la logique mockée côté JVM.

## Build & dépendances

`app/build.gradle.kts` :
```kotlin
implementation("com.google.mlkit:text-recognition:<dernière version stable>")   // OCR on-device
```
Pas de dépendance Gradle pour le LLM lui-même : le binding llama.cpp est
natif (JNI), pas une lib Maven.

Build natif :
- `externalNativeBuild { cmake { ... } }` dans `android {}`, plus un
  `ndkVersion` explicite.
- Sources llama.cpp vendorisées en sous-module git, suivant la structure de
  `llama.cpp/examples/llama.android`.
- `abiFilters` restreints à `arm64-v8a` (devices réels) + `x86_64`
  (émulateur, pour développer sans device physique) — pas de
  `armeabi-v7a`.

Manifest : aucun changement (`INTERNET` et `CAMERA` déjà déclarés).

Impact taille app : APK légèrement plus lourd (lib native + ML Kit, quelques
Mo), le gros du poids (~1 Go, le fichier `.gguf`) n'est **pas** dans l'APK —
téléchargé à la demande.

## Hors scope de cette itération

- Le choix final remplacement vs cohabitation vs toggle réglages entre
  `LabelScanApi` et le moteur local — le bouton "Scanner (IA locale)" est
  provisoire, pour comparaison pendant la validation.
- L'implémentation de l'approche B (VLM multimodal direct) — seul le point
  d'insertion (`LocalLabelScanEngine`) est préparé pour l'accueillir : un
  futur `VlmScanEngine` implémenterait la même interface avec un
  `LlamaVisionEngine` (modèle + fichier `mmproj`) à la place de
  `TextRecognizer` + `LlamaTextEngine`.
- Accélération GPU/NNAPI — démarrage CPU-only (llama.cpp/NEON), à réévaluer
  si les temps de scan mesurés en usage réel sont jugés trop longs.
- Choix précis du modèle et de sa quantisation — un candidat de départ sera
  proposé dans le plan d'implémentation (ex : un modèle instruct 1-3B en
  `Q4_K_M`), ajustable empiriquement sans changer l'architecture.

## Note documentée — alternative "import manuel"

Comme le fait LM Studio, il serait possible de laisser l'utilisateur pointer
l'app vers un fichier `.gguf` déjà présent sur l'appareil (téléchargé par un
autre moyen : navigateur, transfert de fichier, etc.) plutôt que de forcer le
téléchargement automatique depuis une URL fixe. Ça donnerait une
indépendance réseau totale (même le téléchargement initial deviendrait
optionnel) et la liberté de choisir n'importe quel modèle compatible. Non
retenu comme mécanisme principal pour cette itération (complexité UX :
sélection de fichier, validation du format/de la compatibilité, risque de
modèle incompatible avec le prompt de structuration attendu), mais documenté
ici comme extension possible si le téléchargement automatique s'avère
insuffisant en pratique.

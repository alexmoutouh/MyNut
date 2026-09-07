# Scan d'étiquette nutritionnelle via VLM local (remplace l'OCR + LLM texte)

Date : 2026-07-22
Statut : validé (brainstorming), en attente du plan d'implémentation
Remplace : `docs/superpowers/specs/2026-07-09-local-llm-label-scan-design.md` (Approche A, abandonnée)

## Contexte

L'itération précédente (Approche A : ML Kit Text Recognition + petit LLM texte
Qwen2.5-1.5B via llama.cpp) a été implémentée, testée et mise en service
derrière le bouton "Scanner (IA locale)" de `FormScreen`. Un test en
conditions réelles sur 4 vraies étiquettes nutritionnelles (`etiket.jpg` à
`etiket4.jpg`, photos fournies par l'utilisateur) a révélé deux défauts
structurels :

1. **Perte de structure du tableau** : une étiquette nutritionnelle est un
   tableau à 2-3 colonnes (pour 100g/100ml, par portion, % des apports de
   référence). Le texte OCR aplati perd cette structure — le LLM texte doit
   deviner quelle valeur appartient à quelle colonne, avec des erreurs
   fréquentes (valeurs de la mauvaise colonne, champs mal mappés).
2. **OCR carrément défaillant sur certaines photos** (fond coloré, faible
   contraste) — indépendamment de tout prompt, le texte source envoyé au LLM
   était déjà faux.

Un diagnostic direct (test instrumenté dédié, dump du texte OCR brut sur les
4 photos) a confirmé les deux problèmes avec des preuves concrètes.

**Décision** : abandonner l'OCR intermédiaire. Un modèle vision-langage
(VLM) local lira la photo directement et produira le JSON structuré,
supprimant l'étape où la structure du tableau se perd.

## Validation empirique avant implémentation

Avant d'investir dans le pipeline natif, l'utilisateur a testé plusieurs VLM
candidats dans LM Studio sur les mêmes 4 photos :

- **Ministral 3 14B Reasoning** (Mistral AI) : résultats globalement bons.
- **Ministral 3 3B** : moins bon, prompt plus détaillé nécessaire, mais déjà
  nettement meilleur que le pipeline OCR+texte actuel.
- **Qwen3-VL-4B-Instruct** : résultats jugés acceptables par l'utilisateur.

Ministral 3 a été écarté malgré ses bons résultats : llama.cpp (le moteur
déjà vendorisé dans le projet, Tâches 4/5) ne supporte pas son architecture
vision (confirmé — absent de la liste des modèles supportés par `libmtmd`,
et le guide officiel de mise en route de Ministral 3 ne documente que
l'inférence texte via llama.cpp, la vision passant uniquement par Hugging
Face Transformers, non déployable sur Android via notre pipeline).

**Qwen3-VL-4B-Instruct est retenu** : résultats validés empiriquement par
l'utilisateur sur les 4 vraies photos, et support confirmé dans llama.cpp
(ticket GitHub `ggml-org/llama.cpp#16207`, "Feature Request: support
qwen3-vl series", fermé "completed" fin octobre 2025).

**Compromis accepté explicitement** : le nouveau modèle pèse ~2,95 Go
(modèle principal + mmproj) contre ~1,06 Go pour l'ancien, et l'inférence
sera probablement au moins aussi lente que l'ancien pipeline (5 à 28 minutes
observées), potentiellement plus (modèle ~2,6x plus gros de paramètres LLM,
plus le coût de l'encodage vision). L'utilisateur a validé ce compromis :
la fiabilité prime sur la taille/vitesse pour cette fonctionnalité, qui
reste provisoire (bouton "Scanner (IA locale)" non définitif, cf. spec
précédente).

## Architecture

```
FormScreen (bouton "Scanner (IA locale)", inchangé)
        │ imageBytes (JPEG)
        ▼
FormViewModel.scanPhotoLocally(imageBytes)
        │
        ▼
LocalLabelScanEngine  (interface existante, inchangée)
        │
        ▼ (nouvelle implémentation, remplace OcrTextLlmScanEngine)
VlmScanEngine
   ├─ 1. Décodage JPEG → Bitmap (BitmapFactory, comme avant)
   ├─ 2. LlamaVisionEngine.complete(bitmap, prompt)  →  binding JNI llama.cpp + libmtmd
   │      (charge Qwen3-VL-4B + mmproj, encode l'image, génère le JSON directement)
   └─ 3. LlmResponseParser.parse(json) → NutritionalValues   (réutilisé tel quel)
        │
        ▼
Result<NutritionalValues>  →  FormUiState (inchangé)
```

`LabelScanApi` (bouton scan HTTP, premier bouton) reste totalement intact et
séparé — seul le moteur derrière "Scanner (IA locale)" change.

## Composants

### Supprimés (code de l'Approche A, retiré plutôt que laissé mort)

- `OcrTextLlmScanEngine` (orchestration OCR + LLM texte)
- ML Kit Text Recognition (dépendance `com.google.mlkit:text-recognition` +
  `kotlinx-coroutines-play-services`) et `TextRecognitionSmokeTest`
- `PromptBuilder` (construction de prompt à partir d'un texte OCR)
- `LlamaTextEngine` et les méthodes JNI texte-seul (`nativeLoadModel`,
  `nativeComplete`, `nativeUnload`) ainsi que `LlamaTextEngineTest`
- Le modèle texte Qwen2.5-1.5B-Instruct et ses références (URL, checksum)
- `OcrTextLlmScanEngineTest`

Ce code reste récupérable via l'historique git si nécessaire.

### Modifiés

**`LocalModelManager`** — le nom de fichier (`MODEL_FILENAME`) devient un
**paramètre du constructeur** au lieu d'une constante fixe liée au modèle
texte de l'Approche A (nécessaire puisqu'on va instancier cette classe deux
fois, une par fichier). Les constantes par défaut de l'ancien modèle sont
supprimées ; `modelUrl`/`expectedSha256`/le nom de fichier deviennent des
paramètres explicites requis. Le mécanisme de téléchargement/checksum/cache
lui-même ne change pas. Les tests existants (`LocalModelManagerTest`)
passent déjà ces paramètres explicitement dans leurs cas de test — seul le
test vérifiant le nom de fichier fixe doit être adapté pour utiliser le
paramètre au lieu de la constante `MODEL_FILENAME`.

### Nouveaux

**`VlmModelManager`** — agrège deux `LocalModelManager` (un pour
`Qwen3VL-4B-Instruct-Q4_K_M.gguf`, un pour
`mmproj-Qwen3VL-4B-Instruct-Q8_0.gguf`). Expose la même "forme" que l'ancien
manager consommé par `FormViewModel` :

```kotlin
fun isModelReady(): Boolean
suspend fun ensureModelReady(): Result<Pair<File, File>>
val state: StateFlow<ModelDownloadState>
```

Grâce à cette forme identique, **`FormViewModel` et `FormScreen` n'ont
besoin d'aucune modification structurelle** au-delà d'un renommage de
propriété (`ocrTextLlmScanEngine` → `vlmScanEngine`) — c'est exactement ce
que l'interface `LocalLabelScanEngine` a été conçue pour permettre. La
progression de téléchargement est présentée en **deux phases séquentielles**
(modèle principal 0→100 %, puis mmproj 0→100 %) plutôt qu'une barre combinée
pondérée — plus simple à implémenter et tester, au prix d'un léger saut
visuel entre les deux phases.

**`LlamaNative`** (méthodes ajoutées) :

```kotlin
external fun nativeLoadVisionModel(modelPath: String, mmprojPath: String): Long
external fun nativeCompleteWithImage(
    handle: Long, prompt: String,
    imageWidth: Int, imageHeight: Int, imagePixels: ByteArray,
    maxTokens: Int
): String
external fun nativeUnloadVision(handle: Long)
```

Implémentées côté JNI via `libmtmd` (`mtmd_init_from_file`,
`mtmd_bitmap_init`, `mtmd_tokenize`, `mtmd_helper_eval_chunks`) plutôt que la
boucle `llama_decode` manuelle de la Tâche 5. `nativePing` (Tâche 4) est
conservée telle quelle — test de fumée utile indépendant du modèle vision.

**`LlamaVisionEngine`** (remplace `LlamaTextEngine`) — même pattern
(dispatcher dédié à un seul thread, chargement paresseux et résident) :

```kotlin
class LlamaVisionEngine(private val modelManager: VlmModelManager) {
    suspend fun complete(bitmap: Bitmap, prompt: String, maxTokens: Int = 512): Result<String>
    fun unload()
}
```

**`VlmScanEngine`** (remplace `OcrTextLlmScanEngine`, implémente
`LocalLabelScanEngine`) :

```kotlin
class VlmScanEngine(
    private val llamaVisionEngine: LlamaVisionEngine
) : LocalLabelScanEngine {
    override suspend fun scanLabel(imageBytes: ByteArray): Result<NutritionalValues>
}
```

Décode le JPEG en `Bitmap` (comme avant), appelle
`LlamaVisionEngine.complete(bitmap, prompt)` avec un **prompt fixe** (simple
constante — plus besoin d'une fonction de construction puisqu'il n'y a plus
de texte OCR à interpoler), puis réutilise `LlmResponseParser.parse(...)`
tel quel.

### Inchangés

`LocalLabelScanEngine` (interface), `LlmResponseParser`, `LabelScanApi`,
`FormScreen` (aucune modification), `FormViewModel` (renommage de propriété
uniquement), le pipeline natif CMake/NDK/sous-module llama.cpp (Tâche 4).

## Pourquoi deux fichiers modèle (rappel)

Un VLM combine deux réseaux distincts : un **encodeur vision** (fichier
`mmproj-*.gguf`, transforme les pixels en tokens exploitables par le modèle
de langage) et le **modèle de langage** lui-même (fichier principal, fait le
raisonnement et génère le texte). Le format GGUF/llama.cpp garde ces deux
composants comme fichiers séparés pour raisons historiques, et les
quantise différemment : le mmproj reste en `Q8_0` (peu compressé, la vision
tolère mal la compression agressive) alors que le modèle principal est en
`Q4_K_M` (plus compressé). Les deux fichiers sont indissociables pour faire
fonctionner la vision.

## UX et flux de données

Le flux visible ne change quasiment pas par rapport à l'Approche A :

1. Bouton "Scanner (IA locale)" → choix caméra/galerie (inchangé).
2. Si le modèle n'est pas encore présent : dialogue de confirmation, texte
   mis à jour ("Le modèle IA (~3 Go) doit être téléchargé une fois...").
3. Téléchargement : barre de progression en deux phases séquentielles.
4. Modèle prêt → décodage de la photo en `Bitmap`, puis directement
   `VlmScanEngine.scanLabel` (plus d'étape OCR intermédiaire) →
   remplissage des champs ou message d'erreur.
5. Scans suivants : modèle déjà résident en mémoire, pas de re-téléchargement.

## Gestion d'erreurs

| Cas | Comportement |
|---|---|
| Pas de réseau au téléchargement (modèle **ou** mmproj) | `errorMessage` explicite, aucun crash, réessai possible |
| Téléchargement corrompu (checksum invalide, modèle **ou** mmproj) | Fichier rejeté, même message, nouvelle tentative — géré indépendamment pour chaque fichier via `VlmModelManager` |
| Échec de chargement (OOM, fichier illisible, `mtmd_init_from_file` échoue) | `Result.failure`, "Modèle IA indisponible sur cet appareil" |
| Photo indécodable (`BitmapFactory` retourne null) | `Result.failure` "Photo illisible" — inchangé |
| Réponse du VLM illisible / JSON invalide | `LlmResponseParser` tolérant + `Result.failure` "Réponse IA illisible" — inchangé |
| Résultat partiel (certains champs seulement) | Comportement conservé : seuls les champs non-null écrasent le formulaire |

Le cas "aucun texte OCR détecté" de l'Approche A disparaît (plus d'OCR) sans
remplacement — un cas générique existant suffit si la photo est illisible.

## Tests

**JVM (`app/src/test`)** :
- `LlmResponseParserTest` — inchangé.
- `LocalModelManagerTest` — ajusté pour le nom de fichier en paramètre.
- **Nouveau** `VlmModelManagerTest` — agrégation de deux `LocalModelManager`
  (fakes) : prêt seulement si les deux fichiers le sont, le téléchargement
  enchaîne les deux, un échec sur l'un fait échouer l'ensemble.
- `PromptBuilderTest` — supprimé (prompt désormais une constante fixe).

**Instrumenté (`app/src/androidTest`)** :
- `LlamaNativeSmokeTest` — conservé tel quel.
- `TextRecognitionSmokeTest`, `LlamaTextEngineTest`, `OcrTextLlmScanEngineTest`
  — supprimés.
- **Nouveau** `LlamaVisionEngineTest` — équivalent instrumenté de l'ancien
  `LlamaTextEngineTest` ; l'existence d'un petit modèle vision "jouet"
  équivalent à `stories260K.gguf` reste à vérifier au moment du plan — à
  défaut, ce test utilisera directement le modèle réel.
- **Nouveau** `VlmScanEngineTest` — test e2e réel utilisant une vraie photo
  d'étiquette (`etiket.jpg` ou `etiket2.jpg`, déjà à la racine du projet)
  plutôt que l'image synthétique à une colonne utilisée précédemment.

## Build & dépendances

**Supprimé** : `com.google.mlkit:text-recognition`,
`org.jetbrains.kotlinx:kotlinx-coroutines-play-services`.

**CMake** (`app/src/main/cpp/CMakeLists.txt`) : ajout de
`set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)` (option confirmée existante
et indépendante de `LLAMA_BUILD_TOOLS`, qui reste à `OFF`), et ajout de la
target `mtmd` dans `target_link_libraries` en plus de `llama`.

**Modèle** : `Qwen/Qwen3-VL-4B-Instruct-GGUF` (licence Apache 2.0) :
- `Qwen3VL-4B-Instruct-Q4_K_M.gguf` — 2 497 281 664 octets — SHA-256
  `66358cb18bb6b3b1b6675aa412c7a88ef01d228f481184d13668e5201c730a0a`
- `mmproj-Qwen3VL-4B-Instruct-Q8_0.gguf` — 453 974 304 octets — SHA-256
  `30ba2c7dd3127a4561b6cba9d13d0f711c91bdb38742e2f56d73c8cb596bd06d`

(Ces deux checksums ont été récupérés via l'API Hugging Face et vérifiés
comme ayant la bonne longueur — 64 caractères hexadécimaux — mais, comme
toute donnée récupérée via un outil de fetch, à revérifier une dernière fois
avant de les coder en dur, par prudence.)

**Impact taille** : le binaire natif grossit légèrement (bibliothèque
`mtmd` en plus de `llama`), partiellement compensé par la suppression de ML
Kit. Le changement principal est le téléchargement du modèle (~2,95 Go au
lieu de ~1,06 Go), toujours hors APK, téléchargé à la demande.

## Hors scope de cette itération

- Accélération GPU/NNAPI — CPU-only maintenu, comme pour l'Approche A.
- Prétraitement d'image (contraste, recadrage, redressement) — pourrait
  aider sur les cas difficiles comme `etiket4.jpg` (fond sombre, faible
  contraste), mais non traité ici.
- Choix précis de la quantisation (`Q4_K_M`) — point de départ ajustable
  empiriquement, comme pour l'Approche A.
- Décision finale sur l'UI définitive (remplacement vs cohabitation entre
  scan HTTP et scan local) — toujours reportée, le bouton "Scanner (IA
  locale)" reste provisoire.
- Toute recommandation d'aspect ratio ou de prétraitement spécifique à
  Qwen3-VL — non vérifiée pour ce modèle précis (une note existe pour
  Ministral 3, mais n'a pas été confirmée applicable à Qwen3-VL) ; à
  vérifier au moment de l'implémentation si des problèmes de qualité
  apparaissent liés au format de l'image.

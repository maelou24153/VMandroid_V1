# VMDroid – machines virtuelles sur Android

Application Android (Kotlin + Jetpack Compose) qui crée et fait tourner des machines virtuelles avec **QEMU**, avec un écran intégré (client VNC écrit en Kotlin), clavier, souris tactile et journaux.

Peut faire tourner :
- **Android-x86 / Bliss OS** (Android dans la VM) ;
- **Linux**, **Windows 10 / 7** et anciens PC 32 bits (x86) ;
- **Linux ARM64** (avec accélération KVM si l'appareil l'autorise, ce qui est rare).

## Fonctionnalités
- Création / modification / suppression de machines (préréglages, RAM, cœurs, taille du disque).
- Import d'images ISO depuis le stockage (copiées dans `Android/data/com.vmdroid/files/isos`).
- Disque virtuel créé automatiquement (qcow2 si `libqemu-img.so` est présent, sinon fichier brut « sparse »).
- Réseau en mode utilisateur (accès Internet) et redirections de ports (ex. `2222:22`).
- Écran de la VM : toucher = clic, appui long = clic droit, glisser = déplacer/maintenir, molette, clavier Android, clavier matériel, Ctrl/Alt/Win, flèches, Ctrl+Alt+Suppr.
- Service au premier plan : la VM continue quand l'écran est éteint.
- Plusieurs VM possibles en parallèle (un port VNC local par VM).

## Compiler avec GitHub (sans Android Studio)
1. Crée un dépôt GitHub et envoie-y le contenu de ce dossier (`git init`, `git add .`, `git commit -m "VMDroid"`, `git remote add origin <url>`, `git push -u origin main`).
2. Onglet **Actions** : le workflow « Compiler l'APK » se lance à chaque push (ou à la main via *Run workflow*).
3. À la fin, ouvre l'exécution et télécharge l'artefact **VMDroid-debug** : il contient l'APK à installer sur ton téléphone.
4. Pour une release : `git tag v1.0 && git push --tags` ajoute l'APK à la page *Releases*.

Pour que l'APK contienne QEMU, au choix :
- commite les fichiers `libqemu-*.so` dans `app/src/main/jniLibs/arm64-v8a/` ;
- ou héberge un zip des `.so` (à la racine du zip) et renseigne son adresse dans *Settings → Secrets and variables → Actions → Variables* sous le nom `QEMU_URL`.

## Compilation (Android Studio)
1. Ouvrir le dossier dans **Android Studio** (Koala ou plus récent) ; il génère le wrapper Gradle et synchronise.
2. **Ajouter le moteur QEMU** (étape obligatoire, voir ci-dessous).
3. *Run* sur un appareil arm64 (Android 8+, 6 Go de RAM ou plus recommandé).

## Ajouter QEMU
Les binaires QEMU ne sont pas inclus : ils doivent être compilés pour Android (arm64, liés statiquement ou avec leurs bibliothèques) puis placés ici, **renommés en `.so`** :

```
app/src/main/jniLibs/arm64-v8a/
    libqemu-system-x86_64.so    PC 64 bits (Android-x86, Windows, Linux)   ← le plus utile
    libqemu-system-i386.so      PC 32 bits (optionnel)
    libqemu-system-aarch64.so   Linux ARM64 (optionnel)
    libqemu-img.so              création de disques qcow2 (optionnel)
```

Les binaires sont exécutés depuis le dossier des bibliothèques natives de l'application (autorisé par Android, contrairement au dossier de données).

Fichiers de firmware (BIOS, VGA BIOS, ROM réseau, UEFI ARM…) : ce sont ceux du dossier `share/qemu` de QEMU.
- soit les mettre dans un zip nommé `qemu-share.zip` dans `app/src/main/assets/` (extrait au premier lancement) ;
- soit importer ce zip dans l'appli avec le bouton dossier en haut de l'écran principal.

Pour ARM64 il faut en plus `edk2-aarch64-code.fd` dans ce même zip.

## À savoir
- **Performances** : sur un téléphone, un invité x86 est *émulé* (TCG), donc lent. Android-x86 et Linux léger sont utilisables sur un appareil haut de gamme ; Windows est très lent. La virtualisation matérielle (KVM) n'est quasiment jamais accessible aux applications Android.
- Pour Android-x86, utilisez un ISO x86_64 (ex. Bliss OS, Android-x86 9.0) ; essayez 3 Go de RAM, 2 cœurs.
- Licence : QEMU est sous GPLv2. Si vous distribuez l'appli avec QEMU, vous devez respecter cette licence (code source disponible).
- Windows : respectez les conditions de licence de Microsoft.

## Structure
```
app/src/main/java/com/vmdroid/
  data/    VmConfig, VmStore         – modèle + stockage JSON
  engine/  QemuEngine, VmService     – ligne de commande QEMU, processus, notification
  vnc/     RfbClient, KeyMap         – client VNC (Raw, CopyRect, DesktopSize) et touches
  ui/      ListScreen, EditScreen, ConsoleScreen, Theme
```

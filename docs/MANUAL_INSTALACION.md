# Manual de instalación

## Preparar el entorno

1. Instala Android Studio y abre la raíz del repositorio mediante **File → Open**.
2. En **SDK Manager**, instala la plataforma API 37, Build Tools 36.0.0 y Platform Tools. Configura el Gradle JDK incluido en Android Studio. La sesión de QA utilizó JBR 25 y el proyecto compila código Java 17. Usa un AVD con API 37 para reproducir esa sesión. La app declara API mínima 26. [Configuración de Gradle Android](../app/build.gradle)
3. Sincroniza el proyecto. Para la parte local de Firebase, instala Node.js 22, npm y Firebase CLI con `npm install -g firebase-tools@15.37.0`. La estabilización usó Node 22.23.5 y Firebase CLI 15.37.0. Verifica `node --version`, `firebase --version` y `adb devices`. Agrega Platform Tools al `PATH` o ejecuta `adb.exe` desde el SDK.

## Compilar y ejecutar con Firebase configurado

1. Un operador autorizado registra en Firebase una aplicación Android con paquete `com.example.comunidadconectada`, habilita Authentication por correo y contraseña y dispone de Realtime Database. Para una instalación que ya cuenta con estos servicios, utiliza su configuración aprobada.
2. Coloca el archivo de configuración descargado de ese proyecto en `app/google-services.json` de forma local. Comprueba que corresponda al paquete y contenga la URL de Realtime Database. Mantén el archivo fuera del paquete de entrega.
3. La instancia necesita reglas acordes al modelo. El archivo [database.rules.json](../database.rules.json) es la referencia versionable para la revisión local. Cualquier actualización de las reglas publicadas es un cambio independiente que requiere autorización. [Guía Spark](FIREBASE_SPARK.md)
4. Compila desde la raíz o ejecuta el módulo **app** desde Android Studio.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. La pantalla inicial informa si falta el archivo de configuración o la URL de Realtime Database.

## Ejecutar pruebas con Firebase Emulator Suite

Trabaja en una copia dedicada del repositorio sin `app/google-services.json`. El proyecto demo evita el uso de recursos en producción. El archivo [firebase.json](../firebase.json) configura Auth en 9099, Realtime Database en 9000 y carga las reglas locales. Ejecuta desde la raíz. [Configuración oficial del emulador](https://firebase.google.com/docs/emulator-suite/install_and_configure) · [Proyectos demo y Realtime Database](https://firebase.google.com/docs/emulator-suite/connect_rtdb)

```powershell
npm --prefix functions ci
npm --prefix functions test
firebase emulators:exec --project demo-comunidad --only auth,database "node --test --test-force-exit --test-concurrency=1 functions/integration/flows.test.js functions/integration/provision-admin.test.js functions/integration/rules.test.js"
```

Para instrumentación y demostración, inicia una sesión de emuladores independiente con el siguiente comando.

```powershell
firebase emulators:start --project demo-comunidad --only auth,database
```

En una segunda consola, prepara las cuentas y reportes de prueba una vez por sesión de Firebase. Selecciona el identificador de tu AVD de `adb devices` en lugar de `emulator-5556`. El siguiente bloque se ejecuta en Windows PowerShell.

```powershell
$env:GCLOUD_PROJECT = 'demo-comunidad'
$env:FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099'
$env:FIREBASE_DATABASE_EMULATOR_HOST = '127.0.0.1:9000'
node functions/integration/seed-android.js
$qaDevice = 'emulator-5556'
$env:ANDROID_SERIAL = $qaDevice
adb -s $qaDevice reverse tcp:9099 tcp:9099
adb -s $qaDevice reverse tcp:9000 tcp:9000
.\gradlew.bat -PcommunityEmulators=true connectedDebugAndroidTest
```

Usa un solo AVD para esa corrida. Las pruebas de flujos escriben en los datos locales. Para la demostración elige una sesión nueva de los emuladores y vuelve a sembrar. Si quieres instalar la variante demo sin ejecutar la instrumentación, usa estos comandos.

```powershell
.\gradlew.bat -PcommunityEmulators=true assembleDebug
adb -s $qaDevice install -r app/build/outputs/apk/debug/app-debug.apk
```

Las pruebas `StartupTest` de configuración ausente y preferencias requieren la copia sin configuración real y un build **sin** la propiedad `communityEmulators`.

```powershell
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.comunidadconectada.StartupTest'
```

La ejecución local utiliza los puertos, el AVD y las cuentas ficticias de la [guía de demostración](GUIA_DEMOSTRACION.md). Los resultados y los casos que quedaron fuera de la corrida están en la [matriz QA](MATRIZ_PRUEBAS_QA.md).

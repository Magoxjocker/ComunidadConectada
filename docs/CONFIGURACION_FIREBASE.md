# Configuración Firebase

> **Referencia histórica.** Las instrucciones de Blaze y despliegue pertenecen a la integración anterior. Usa el [manual de instalación](MANUAL_INSTALACION.md) y la [guía Spark](FIREBASE_SPARK.md) para la entrega.

## Vinculación Android

El módulo aplica Google Services 4.6.0 al encontrar `app/google-services.json` y resuelve Auth, Realtime Database, Cloud Messaging y Functions mediante Firebase BoM 35.2.0. Functions permanece como dependencia del cliente porque el repositorio invoca `communityAction` y `mqttSubscription`. La rama de emuladores se activa con `-PcommunityEmulators=true` y requiere una copia de trabajo sin el JSON real.

La configuración revisada contiene un cliente Android para `com.example.comunidadconectada`. Aún falta `project_info.firebase_url`. El aviso de la app distingue este caso de un JSON ausente. La ausencia de la URL en el archivo puede indicar que la instancia aún debe crearse o que el archivo debe descargarse nuevamente.

## Firebase Console

1. Abre el proyecto **ComunidadConectada** y comprueba que la app Android registrada usa `com.example.comunidadconectada`.
2. Activa **Authentication → Proveedores → Correo electrónico y contraseña**. Configura una política de ocho caracteres como mínimo, letra y número, y revisa las plantillas de verificación y recuperación.
3. Si Realtime Database aún no existe, créala en modo bloqueado. Para el backend incluido, usa `us-central1`. Si la instancia ya existe, copia su URL exacta desde Firebase Console y verifica que la región sea compatible con los disparadores antes de desplegar. Descarga otra vez `google-services.json` desde **Configuración del proyecto → Tus apps**, guárdalo en `app/google-services.json` y verifica el campo `project_info.firebase_url` sin publicar su contenido. Sincroniza Gradle y reinstala el APK.
4. Usa Node 22 y Firebase CLI. Ejecuta `firebase login` y confirma el proyecto con `firebase projects:list`.
5. En `functions/.env.PROJECT_ID`, define `CC_DATABASE_INSTANCE` con el ID de RTDB y `CC_MQTT_HOST` con el dominio y puerto TLS del broker. Sigue la [guía MQTT](CONFIGURACION_MQTT.md) para guardar el secreto.
6. Habilita el plan Blaze para desplegar las funciones y crea alertas de presupuesto. La cuenta de servicio del backend necesita leer Firebase Auth, leer/escribir RTDB, enviar FCM y acceder al secreto MQTT.
7. Despliega al proyecto explícito, crea la [cuenta inicial](CREACION_ADMINISTRADOR.md), sincroniza Gradle y reinstala. Usa un dispositivo con Google Play Services. Comprueba que Firebase Cloud Messaging esté habilitado para el proyecto. En Android 13 o superior, concede permiso de notificaciones desde **Mi perfil** y publica un anuncio administrativo para verificar que el backend lo envíe y que Android abra el anuncio correcto.

```powershell
npm ci --prefix functions
firebase deploy --only database --project PROJECT_ID
firebase deploy --only functions --project PROJECT_ID
firebase functions:list --project PROJECT_ID
```

[Configurar Firebase en Android](https://firebase.google.com/docs/android/setup) · [Regiones](https://firebase.google.com/docs/functions/locations) · [Precios y planes](https://firebase.google.com/pricing)

## Emuladores locales del servidor

Crea `functions/.env.local` con `CC_DATABASE_INSTANCE=skip-external-delivery` y `CC_MQTT_HOST=localhost:8883`. En `functions/.secret.local`, define `CC_MQTT_JWT_SECRET` con cualquier valor de prueba de al menos 32 bytes. Los dos archivos están excluidos de Git. Los emuladores prueban Auth, RTDB y funciones. FCM y EMQX reales requieren configuración externa.

```powershell
npm ci --prefix functions
npm test --prefix functions
firebase emulators:exec --project demo-comunidad --only auth,database,functions 'npm run test:rules --prefix functions && npm run test:flows --prefix functions'
```

Para probar el chat desde Android, ejecuta el siguiente comando con un AVD iniciado y el SDK visible. El flag usa conexiones locales redirigidas por ADB y queda limitado al build debug. Retira cualquier JSON de configuración externa del módulo durante esta prueba.

```powershell
adb reverse tcp:9099 tcp:9099
adb reverse tcp:9000 tcp:9000
adb reverse tcp:5001 tcp:5001
firebase emulators:exec --project demo-comunidad --only auth,database,functions 'node functions/integration/seed-android.js && gradlew.bat -PcommunityEmulators=true connectedDebugAndroidTest'
```

Compila de nuevo sin el flag para generar un APK de evaluación contra Firebase configurado.

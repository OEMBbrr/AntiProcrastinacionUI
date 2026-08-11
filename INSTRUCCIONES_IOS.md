# Compilar e Instalar AntiProcrastinación en iOS

Este repositorio Flutter contiene toda la interfaz y lógica principal. Como la aplicación está hecha en **Flutter**, es 100% compatible con iOS (iPhone/iPad). Sin embargo, compilar una aplicación para iOS (generar el archivo `.ipa` o instalarla en un iPhone) requiere herramientas exclusivas de Apple.

Dado que estamos operando desde un entorno Windows, te presento las 3 opciones para poder instalar esto en tu iPhone:

### Opción 1: Compilar en la Nube (Gratis y Recomendado)
Puedes usar **Codemagic** o **GitHub Actions**. Dado que acabamos de subir este código a tu repositorio `AntiProcrastinacionUI` en GitHub:
1. Abre [Codemagic](https://codemagic.io/) e inicia sesión con GitHub.
2. Selecciona tu repositorio `AntiProcrastinacionUI`.
3. Selecciona que es un proyecto **Flutter** y marca **iOS**.
4. Haz clic en *Start new build*. Codemagic usará una Mac en la nube para compilar tu app y te dará un archivo `.ipa` descargable o un código QR para instalarlo directamente.

### Opción 2: Si consigues prestada una Mac (MacBook / iMac)
1. Instala Xcode desde la App Store.
2. Instala Flutter en esa Mac.
3. Clona tu repositorio: `git clone https://github.com/OEMBbrr/AntiProcrastinacionUI.git`
4. Abre la terminal en esa carpeta y corre:
   ```bash
   flutter pub get
   cd ios
   pod install
   cd ..
   flutter build ios
   ```
5. Abre el archivo `ios/Runner.xcworkspace` en Xcode, conecta tu iPhone por cable, selecciona tu iPhone arriba a la izquierda y presiona el botón de **Play (Run)**. Se instalará en segundos.

### Opción 3: TestFlight / App Store (Oficial)
Si pagas la cuenta de Apple Developer (99 USD al año):
1. Usando el mismo Codemagic o GitHub Actions de la Opción 1, puedes configurar la exportación hacia *App Store Connect*.
2. Desde allí la lanzas a **TestFlight** y simplemente te llegará una notificación a la app de TestFlight en tu iPhone para descargarla.

---

> **Nota de adaptaciones ya hechas**: El código que acabo de subir ya cuenta con el soporte para los íconos de Cupertino (`cupertino_icons`) y toda la lógica responsiva. No necesitas programar nada más de interfaz para que se vea nativo en iOS.

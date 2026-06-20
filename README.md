# AitorShop 🛒

**AitorShop** es una aplicación móvil nativa inteligente para la lista de la compra colaborativa, desarrollada con las mejores prácticas de **Jetpack Compose (Kotlin)**, **Material Design 3** y sincronización nativa en tiempo real con **Supabase REST, Realtime y Auth**.

Esta aplicación está diseñada tanto para uso diario individual como para que múltiples usuarios editen y actualicen la misma lista de compras de forma coordinada y en tiempo real.

---

## ✨ Características Principales

- 🏷️ **Nombre de la Aplicación:** Personalizada al 100% como **AitorShop** tanto en el sistema operativo como en los metadatos de visualización.
- 🔐 **Autenticación con Supabase:** Inicio de sesión y registro de cuentas seguro con correo electrónico/contraseña, y compatibilidad integrada con Google Auth (simulador nativo de Bottom Sheet selector de cuentas de Google Play Services).
- 🤝 **Sincronización Colaborativa en Tiempo Real:** Varias personas pueden visualizar y editar la misma lista de compras simultáneamente. Basta con ingresar el mismo "Código de Lista" (por ejemplo: `CASA_FAMILIA` o `COMPRA_PISO`) en Ajustes para sincronizar los cambios de todos de forma reactiva.
- 💶 **Precio Estimado de Artículos:** Permite añadir a cada artículo una cifra de precio estimado individual, manteniendo un control exhaustivo del presupuesto del carrito de compra.
- 🛍️ **Modo Compra Optimizado:**
  - Vista limpia de gran escala optimizada con tipografía legible y tamaños de toque adaptados para uso dinámico en el supermercado.
  - Tachado visual grande e intuitivo (`LineThrough`) al marcar cada artículo como comprado.
  - Barra superior de progreso dinámico que detalla el progreso actual ($X$ de $Y$ artículos comprados) y total financiero estimado de la lista actualizado en tiempo real.
- 📋 **Registro de Historial:** Guarda de forma permanente las compras completadas catalogándolas como "compras realizadas" con coste acumulado, conteo de ítems y fecha.
- 🎙️ **Entrada por Voz Inteligente:** Dicta tus productos por voz (ej. *"Añadir 3 de leche entera por 1.20"* o *"Dos panes"*) y nuestra lógica procesará dinámicamente el nombre, unidades de cantidad, precio y unidad métrica correspondientemente.

---

## 🛠️ Configuración de la Base de Datos en Supabase

Sigue estos rápidos pasos para dejar lista tu base de datos en Supabase para sincronizar con **AitorShop**:

1. Ve a tu consola/panel de control de [Supabase](https://supabase.com).
2. Entra en la sección **SQL Editor** de tu proyecto y crea una consulta en blanco.
3. Pega el siguiente script de creación y presiona **Run** para crear la tabla y dotarla de capacidad en tiempo real (Supabase Realtime):

```sql
-- 1. Crear tabla para almacenar los artículos de compra
create table shopping_items (
  id text primary key,
  name text not null,
  quantity float8 not null default 1.0,
  unit text not null default 'uds.',
  price float8 not null default 0.0,
  category text not null default 'Otros',
  "isPurchased" boolean not null default false,
  "listId" text not null default 'default',
  "createdAt" int8 not null default (extract(epoch from now()) * 1000),
  "isDeleted" boolean not null default false
);

-- 2. Habilitar la publicación en tiempo real para sincronizar múltiples usuarios
alter publication supabase_realtime add table shopping_items;
```

---

## 🔑 Variables de Entorno `.env`

AitorShop utiliza el plugin seguro de Gradle `Secrets Gradle Plugin` para inyectar credenciales durante la compilación.

Para configurar la aplicación, crea un archivo llamado `.env` en la **raíz del proyecto** (o configúralas en la sección **Guía de Secretos de AI Studio** en pantalla) con las siguientes claves:

```env
# URL base de tu proyecto de Supabase (ejemplo)
SUPABASE_URL=https://tu-proyecto.supabase.co

# Clave pública anónima de tu proyecto de Supabase (ejemplo)
SUPABASE_KEY=tu-apikey-anonima-publica-de-supabase
```

*Nota: Si las claves no están declaradas o no coinciden, la aplicación entrará automáticamente en un robusto **Modo Invitado / Offline**, el cual mantiene la funcionalidad, dictado por voz y base de datos local (Room/SQLite) de forma independiente para evitar bloqueos.*

---

## 🚀 Cómo Compilar y Correr el Proyecto

Se requieren **JDK 11 o superior** y **Android Studio (Ladybug o superior)** o **Gradle CLI**.

### Compilación desde Línea de Comandos (CLI)

Para ensamblar y compilar el APK de depuración directamente con Gradle:

```bash
# Compilar proyecto y asegurar empaquetado seguro
gradle assembleDebug
```

Para ejecutar las pruebas unitarias y verificar el motor de lógica local:

```bash
gradle :app:testDebugUnitTest
```

---

## 📁 Estructura del Proyecto

El código fuente sigue las directrices oficiales **MVVM** (Model-View-ViewModel) y Clean Architecture:

```text
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/
│   │   │   │   ├── MainActivity.kt        # Entrada principal, enrutador de pestañas y pasarela de Auth.
│   │   │   │   ├── data/
│   │   │   │   │   ├── local/             # Entidades de Room SQLite, DAOs y creador de BBDD local.
│   │   │   │   │   ├── remote/            # Definiciones de Retrofit API y DTOs para Supabase Sync & Auth.
│   │   │   │   │   └── repository/        # Mediador de repositorios locales y remotos (sincronizador).
│   │   │   │   └── ui/
│   │   │   │       ├── MainViewModel.kt   # Estado global unificado, lógica de dictado por voz y llamadas Auth.
│   │   │   │       ├── screens/           # Pantallas adaptativas de la UI (M3 Compose).
│   │   │   │       │   ├── AuthScreen.kt  # Pantalla de registro/ingreso y Google Auth.
│   │   │   │       │   ├── ShoppingListScreen.kt # Gestor principal de productos con voz y catálogo.
│   │   │   │       │   ├── ShoppingModeScreen.kt # Vista optimizada para tienda con tachado y barra superior de progreso real.
│   │   │   │       │   ├── HistoryScreen.kt # Historial de compras completadas.
│   │   │   │       │   └── SettingsScreen.kt # Ajustes de lista, modo oscuro y visualizador SQL.
│   │   │   │       └── theme/             # Sistema central de temas Material 3.
│   │   │   └── res/                       # Recursos XML, Cadenas localizadas (AitorShop) y adaptadores gráficos.
│   └── build.gradle.kts                   # Configuración del módulo de la app (dependencias de Room, OkHttp, Retrofit).
├── gradle/
│   └── libs.versions.toml                 # Catálogo centralizado de versiones de complementos del proyecto (Android Gradle, Kotlin).
├── build.gradle.kts                       # Script raíz de construcción.
└── settings.gradle.kts                    # Ajustes de repositorios de Gradle.
```

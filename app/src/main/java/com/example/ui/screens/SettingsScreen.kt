package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    innerPadding: PaddingValues
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isConfigured = viewModel.isSupabaseConfigured
    val activeListId by viewModel.activeListId.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsState()
    val isGuestMode by viewModel.isGuestMode.collectAsState()

    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val sqlScript = """
-- Crea esta tabla en la consola SQL de tu proyecto de Supabase:
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

-- Habilita actualizaciones en tiempo real para esta tabla
alter publication supabase_realtime add table shopping_items;
""".trimIndent()

    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SQL Supabase script", text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Script SQL copiado al portapapeles", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = 16.dp)
            .verticalScroll(scrollState)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .padding(top = 12.dp, bottom = 4.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Image(
                painter = painterResource(id = com.example.R.drawable.img_list_header_1781954525058),
                contentDescription = "AitorShop Ajustes Banner",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Text(
            "Configuración",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // --- SECTION 1: APARIENCIA ---
        Text(
            "Apariencia",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Modo Oscuro", fontWeight = FontWeight.Bold)
                        Text(
                            "Alterna entre modo claro y nocturno",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Switch(
                    checked = isDarkMode,
                    onCheckedChange = { viewModel.toggleDarkMode() }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SECTION: SESIÓN DE USUARIO ---
        Text(
            "Sesión de Usuario",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUserLoggedIn) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isUserLoggedIn) "Conectado como" else "Modo Invitado / Sin Cuenta",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isUserLoggedIn) "$userEmail" else "Las listas no se sincronizarán en la nube de Supabase.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Button(
                    onClick = { viewModel.logout() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isUserLoggedIn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isUserLoggedIn) Icons.Default.Close else Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isUserLoggedIn) "Cerrar Sesión" else "Iniciar Sesión / Registrarse",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SECTION 2: CONECTIVIDAD (Supabase status) ---
        Text(
            "Sincronización Supabase",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isConfigured) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                }
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isConfigured) Icons.Default.Share else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = if (isConfigured) "Supabase Conectado" else "Supabase sin Configurar",
                        fontWeight = FontWeight.Bold,
                        color = if (isConfigured) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = if (isConfigured) {
                            "Los datos de la lista '$activeListId' están sincronizándose en tiempo real."
                        } else {
                            "Funcionando en modo local (offline). Introduce tus claves en el panel de secretos de AI Studio para activar la sincronización."
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // --- SECTION 3: TUTORIAL / SUPABASE SETUP INSTRUCTIONS ---
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Cómo configurar tu base de datos Supabase (¡Gratis!):",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "1. Crea una cuenta gratuita en supabase.com y lanza un nuevo proyecto.\n" +
                    "2. Ve a la consola SQL y copia el script que se muestra abajo.\n" +
                    "3. Ve a Project Settings > API en Supabase y obtén tu URL del proyecto y tu clave anon pública.\n" +
                    "4. En el panel lateral de Google AI Studio, añade 'SUPABASE_URL' y 'SUPABASE_KEY' en la pestaña Secrets y se inyectarán listos para compilar.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Script de Creación SQL", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    TextButton(onClick = { copyToClipboard(sqlScript) }) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copiar SQL", fontSize = 12.sp)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    SelectionContainer {
                        Text(
                            text = sqlScript,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- SECTION 4: GITHUB & APK EXPORT GUIDE ---
        Text(
            "Exportar y Despliegue APK",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Guía para compilar y subir a Github", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Para guardar tu repositorio y desplegar el APK 100% gratis:\n\n" +
                    "• **Paso 1: Guardar en GitHub:** En el menú superior o lateral de AI Studio, haz clic en 'Sync to GitHub' o 'Export to GitHub'. Sigue el flujo para conectarte con tu cuenta de GitHub y creas el repositorio totalmente gratis.\n\n" +
                    "• **Paso 2: Generar e Instalar el APK:** AI Studio realiza compilaciones automáticas de tu código para el simulador streaming. Puedes descargar el APK final para instalarlo en teléfonos reales directamente abriendo el menú de Ajustes del Proyecto en AI Studio > presiona 'Descargar APK'.\n\n" +
                    "• **Paso 3: Crear un 'Release' en GitHub:** En tu página de GitHub, clica en 'Releases' > 'Create a new release'. Elige una versión (como v1.0.0), arrastra el archivo APK descargado al panel de adjuntos y publica. Tus familiares y amigos podrán descargar el APK directamente desde allí gratis.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

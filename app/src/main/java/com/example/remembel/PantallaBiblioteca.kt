package com.example.remembel

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun PantallaBiblioteca(
    modifier: Modifier = Modifier,
    onVolver: () -> Unit
) {
    val context = LocalContext.current
    val carpetaBase = remember { obtenerCarpetaBiblioteca(context) }

    var carpetaActual by remember { mutableStateOf(carpetaBase) }
    var refrescar by remember { mutableIntStateOf(0) } // truco: cambiar esto fuerza releer el disco

    var mostrarDialogoNuevaCarpeta by remember { mutableStateOf(false) }
    var elementoParaRenombrar by remember { mutableStateOf<File?>(null) }
    var elementoParaBorrar by remember { mutableStateOf<File?>(null) }
    var elementoParaMover by remember { mutableStateOf<File?>(null) }
    var mensaje by remember { mutableStateOf("") }

    // Se recalcula solo cuando cambia la carpeta o pedimos refresco manual (tras crear/borrar/renombrar)
    val contenido = remember(carpetaActual, refrescar) {
        val hijos = carpetaActual.listFiles()?.toList() ?: emptyList()
        hijos.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = {
                if (carpetaActual == carpetaBase) onVolver()
                else carpetaActual = carpetaActual.parentFile ?: carpetaBase
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
            }
            Text(
                if (carpetaActual == carpetaBase) "Biblioteca" else carpetaActual.name,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { mostrarDialogoNuevaCarpeta = true }) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "Nueva carpeta")
            }
        }
        if (mensaje.isNotEmpty()) {
            Text(
                mensaje,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(Modifier.height(8.dp))

        if (contenido.isEmpty()) {
            Text(
                "Carpeta vacía",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(contenido) { elemento ->
                ElementoBiblioteca(
                    archivo = elemento,
                    onAbrir = { if (elemento.isDirectory) carpetaActual = elemento },
                    onRenombrar = { elementoParaRenombrar = elemento },
                    onBorrar = { elementoParaBorrar = elemento },
                    onMover = if (!elemento.isDirectory) { { elementoParaMover = elemento } } else null
                )
            }
        }
    }

    if (mostrarDialogoNuevaCarpeta) {
        var nombreNuevo by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { mostrarDialogoNuevaCarpeta = false },
            title = { Text("Nueva carpeta") },
            text = {
                OutlinedTextField(
                    value = nombreNuevo,
                    onValueChange = { nombreNuevo = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (nombreNuevo.isNotBlank()) {
                        File(carpetaActual, nombreNuevo.trim()).mkdirs()
                        refrescar++
                    }
                    mostrarDialogoNuevaCarpeta = false
                }) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoNuevaCarpeta = false }) { Text("Cancelar") }
            }
        )
    }

    elementoParaRenombrar?.let { archivo ->
        var nuevoNombre by remember(archivo) {
            mutableStateOf(if (archivo.isDirectory) archivo.name else archivo.name.removeSuffix(".m4a"))
        }
        AlertDialog(
            onDismissRequest = { elementoParaRenombrar = null },
            title = { Text("Renombrar") },
            text = {
                OutlinedTextField(
                    value = nuevoNombre,
                    onValueChange = { nuevoNombre = it },
                    label = { Text("Nuevo nombre") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val extension = if (archivo.isDirectory) "" else ".m4a"
                    val destino = File(archivo.parentFile, nuevoNombre.trim() + extension)

                    when {
                        destino == archivo -> {
                            // El nombre no cambió realmente; no hacemos nada.
                        }
                        destino.exists() -> {
                            mensaje = "Ya existe algo llamado \"${destino.name}\" aquí."
                        }
                        else -> {
                            val exito = archivo.renameTo(destino)
                            mensaje = if (exito) "" else "No se pudo renombrar. Inténtalo de nuevo."
                        }
                    }
                    elementoParaRenombrar = null
                    refrescar++
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { elementoParaRenombrar = null }) { Text("Cancelar") }
            }
        )
    }

    elementoParaBorrar?.let { archivo ->
        AlertDialog(
            onDismissRequest = { elementoParaBorrar = null },
            title = { Text("¿Borrar definitivamente?") },
            text = { Text(archivo.name) },
            confirmButton = {
                TextButton(onClick = {
                    archivo.deleteRecursively()
                    elementoParaBorrar = null
                    refrescar++
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { elementoParaBorrar = null }) { Text("Cancelar") }
            }
        )
    }
    elementoParaMover?.let { archivo ->
        val carpetas = remember(refrescar) { listarCarpetas(carpetaBase) }
        AlertDialog(
            onDismissRequest = { elementoParaMover = null },
            title = { Text("Mover \"${archivo.name}\" a...") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(carpetas) { (etiqueta, destino) ->
                        Text(
                            etiqueta,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val destinoFinal = File(destino, archivo.name)
                                    when {
                                        destino == archivo.parentFile -> {
                                            mensaje = "Ya está en esa carpeta."
                                        }
                                        destinoFinal.exists() -> {
                                            mensaje = "Ya existe \"${archivo.name}\" en esa carpeta."
                                        }
                                        else -> {
                                            val exito = archivo.renameTo(destinoFinal)
                                            mensaje = if (exito) "" else "No se pudo mover. Inténtalo de nuevo."
                                        }
                                    }
                                    elementoParaMover = null
                                    refrescar++
                                }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { elementoParaMover = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ElementoBiblioteca(
    archivo: File,
    onAbrir: () -> Unit,
    onRenombrar: (() -> Unit)?,
    onMover: (() -> Unit)? = null,
    onBorrar: (() -> Unit)?
) {
    var menuAbierto by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Icon(if (archivo.isDirectory) Icons.Filled.Folder else Icons.Filled.AudioFile, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Text(archivo.name, modifier = Modifier.weight(1f).clickable(onClick = onAbrir))
        if (onRenombrar != null || onBorrar != null) {
            Box {
                IconButton(onClick = { menuAbierto = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Opciones")
                }
                DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                    if (onRenombrar != null) {
                        DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menuAbierto = false; onRenombrar() })
                    }
                    if (onMover != null) {
                        DropdownMenuItem(text = { Text("Mover a...") }, onClick = { menuAbierto = false; onMover() })
                    }
                    if (onBorrar != null) {
                        DropdownMenuItem(text = { Text("Borrar") }, onClick = { menuAbierto = false; onBorrar() })
                    }
                }
            }
        }
    }
}

fun obtenerCarpetaBiblioteca(context: Context): File {
    val carpeta = File(context.getExternalFilesDir(null), "biblioteca")
    if (!carpeta.exists()) carpeta.mkdirs()
    return carpeta
}

fun obtenerCarpetaGrabaciones(context: Context): File =
    File(context.getExternalFilesDir(null), "grabaciones")
/**
 * Recorre recursivamente todas las subcarpetas a partir de "base" y devuelve
 * una lista de pares (ruta legible, carpeta real) para mostrar en un selector.
 */
private fun listarCarpetas(base: File): List<Pair<String, File>> {
    val resultado = mutableListOf<Pair<String, File>>("Biblioteca (raíz)" to base)

    fun recorrer(actual: File, rutaRelativa: String) {
        val subcarpetas = actual.listFiles { f -> f.isDirectory } ?: return
        for (sub in subcarpetas.sortedBy { it.name }) {
            val ruta = if (rutaRelativa.isEmpty()) sub.name else "$rutaRelativa/${sub.name}"
            resultado.add(ruta to sub)
            recorrer(sub, ruta)
        }
    }
    recorrer(base, "")

    return resultado
}


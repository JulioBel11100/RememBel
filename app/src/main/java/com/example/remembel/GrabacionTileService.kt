package com.example.remembel

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Azulejo de Ajustes Rápidos: un atajo para empezar/parar la grabación
 * sin abrir la app, desde el panel que se despliega deslizando dos veces.
 */
class GrabacionTileService : TileService() {

    private var trabajoEscucha: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        // Mientras el panel esté abierto, escuchamos el StateFlow del servicio
        // para que el azulejo se mantenga sincronizado con la realidad.
        trabajoEscucha = CoroutineScope(Dispatchers.Main).launch {
            RecordingService.estaGrabando.collectLatest { grabando ->
                actualizarTile(grabando)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        trabajoEscucha?.cancel()
    }

    override fun onClick() {
        super.onClick()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+: hace falta "rebotar" por una Activity visible
            // para poder arrancar un servicio de tipo micrófono.
            val intent = Intent(this, GrabacionTrampolinActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            // Versiones anteriores no tienen esta restricción: arranque directo.
            if (RecordingService.estaGrabando.value) {
                stopService(Intent(this, RecordingService::class.java))
            } else {
                startForegroundService(Intent(this, RecordingService::class.java))
            }
        }
    }

    private fun actualizarTile(grabando: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (grabando) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (grabando) "Grabando" else "RememBel"
        tile.updateTile()
    }
}
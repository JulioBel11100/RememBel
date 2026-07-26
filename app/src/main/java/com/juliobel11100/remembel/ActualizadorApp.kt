package com.juliobel11100.remembel

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Comprueba en Google Play si hay una versión más reciente publicada y, si el
 * usuario acepta, la descarga en segundo plano y la instala (flujo "flexible"
 * de Play Core: no interrumpe el uso de la app mientras se descarga).
 *
 * Es la única llamada de red de toda la app — ver privacidad.md. El resto del
 * flujo (grabación, biblioteca, recuperación) sigue siendo 100% local.
 */
object ActualizadorApp {

    private val _hayActualizacionDisponible = MutableStateFlow(false)
    val hayActualizacionDisponible: StateFlow<Boolean> = _hayActualizacionDisponible

    private val _actualizacionLista = MutableStateFlow(false)
    val actualizacionLista: StateFlow<Boolean> = _actualizacionLista

    private var gestor: AppUpdateManager? = null
    private var infoActual: AppUpdateInfo? = null

    private val listener = InstallStateUpdatedListener { estado ->
        _actualizacionLista.value = estado.installStatus() == InstallStatus.DOWNLOADED
    }

    /** Llamar en onCreate y onResume: recoge tanto una actualización nueva como una ya descargada. */
    fun comprobar(activity: Activity) {
        val manager = gestor ?: AppUpdateManagerFactory.create(activity).also {
            it.registerListener(listener)
            gestor = it
        }
        manager.appUpdateInfo.addOnSuccessListener { info ->
            infoActual = info
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                _hayActualizacionDisponible.value = true
            }
            if (info.installStatus() == InstallStatus.DOWNLOADED) {
                _actualizacionLista.value = true
            }
        }
    }

    fun iniciarDescarga(lanzador: ActivityResultLauncher<IntentSenderRequest>) {
        val manager = gestor ?: return
        val info = infoActual ?: return
        manager.startUpdateFlowForResult(
            info,
            lanzador,
            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
        )
        _hayActualizacionDisponible.value = false
    }

    /** Reinicia la app aplicando la actualización ya descargada. */
    fun completarActualizacion() {
        gestor?.completeUpdate()
    }
}

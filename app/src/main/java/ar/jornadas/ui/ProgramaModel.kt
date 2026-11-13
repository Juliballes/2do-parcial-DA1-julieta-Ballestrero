package ar.jornadas.ui

import android.app.Application
import androidx.lifecycle.*
import ar.jornadas.Servicios
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ProgramaModel(app: Application, private val guardado: SavedStateHandle) : AndroidViewModel(app) {
    private val servicios = Servicios.obtener(app)
    val mesa = servicios.mesa
    private val repo = servicios.agenda
    val charlas = repo.programa.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val descargado = repo.descargado.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val dia = guardado.getStateFlow("dia", 0)
    val tema = guardado.getStateFlow("tema", "Todos")
    val cargando = repo.cargando
    val aviso = combine(repo.fallo, repo.descargado) { fallo, existeEdicion ->
        when {
            !fallo -> ""
            existeEdicion -> "No se pudo actualizar: se están usando datos guardados."
            else -> "No se pudo consultar el programa. Reintentá cuando haya señal."
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun iniciarSesion() {
        viewModelScope.launch { repo.cargarInicial() }
    }

    fun elegirDia(valor: Int) { guardado["dia"] = valor }
    fun elegirTema(valor: String) { guardado["tema"] = valor }

    fun recargar() {
        viewModelScope.launch { repo.sincronizar() }
    }

    fun marcar(id: String) { viewModelScope.launch { repo.cambiarFavorita(id) } }
}

package ar.jornadas.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

class AgendaRepository(private val api: ProgramaApi, private val dao: CarteleraDao) {
    val programa = dao.observar()
    val descargado = dao.observarDescarga()
    private val inicialSolicitada = AtomicBoolean(false)
    private val _cargando = MutableStateFlow(false)
    val cargando = _cargando.asStateFlow()
    private val _fallo = MutableStateFlow(false)
    val fallo = _fallo.asStateFlow()

    suspend fun cargarInicial() {
        if (inicialSolicitada.compareAndSet(false, true)) sincronizar()
    }

    suspend fun sincronizar() {
        // No se encola una segunda descarga mientras ya hay una en curso.
        if (!_cargando.compareAndSet(false, true)) return
        _fallo.value = false
        try {
            val fichas = api.consultar().map {
                Charla(it.id, it.title, it.track, it.day, it.time, it.room, it.speaker, it.summary)
            }
            dao.reemplazarPrograma(fichas)
        } catch (cancelada: CancellationException) {
            throw cancelada
        } catch (error: Exception) {
            // La edición guardada permanece intacta si la actualización falla.
            _fallo.value = true
        } finally {
            _cargando.value = false
        }
    }

    suspend fun cambiarFavorita(id: String) = dao.alternar(id)
}

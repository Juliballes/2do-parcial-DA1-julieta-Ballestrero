package ar.jornadas.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AgendaRepositoryTest {
    private lateinit var app: Context
    private lateinit var db: Archivo
    private lateinit var dao: CarteleraDao
    private lateinit var api: ApiControlada
    private lateinit var repo: AgendaRepository

    @Before fun preparar() {
        app = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(app, Archivo::class.java)
            .allowMainThreadQueries().build()
        dao = db.cartelera()
        api = ApiControlada(app)
        repo = AgendaRepository(api, dao)
    }

    @After fun cerrar() { db.close() }

    @Test fun primeraDescargaGuardaLasDoceCharlas() = runBlocking {
        repo.sincronizar()
        assertEquals(12, dao.actuales().size)
        assertTrue(repo.descargado.first())
        assertFalse(repo.fallo.value)
        assertFalse(repo.cargando.value)
    }

    @Test fun sinDescargaSePuedeReintentarDespuesDeUnaFalla() = runBlocking {
        api.disponible = false
        repo.sincronizar()
        assertTrue(repo.fallo.value)
        assertFalse(repo.descargado.first())
        assertTrue(dao.actuales().isEmpty())
        api.disponible = true
        repo.sincronizar()
        assertEquals(2, api.intentos)
        assertFalse(repo.fallo.value)
        assertEquals(12, dao.actuales().size)
    }

    @Test fun fallaConservaProgramaDetalleYFavoritas() = runBlocking {
        repo.sincronizar()
        repo.cambiarFavorita("11")
        val anterior = dao.actuales()
        api.disponible = false
        api.revision = 3
        repo.sincronizar()
        assertEquals(anterior, dao.actuales())
        assertTrue(dao.actuales().single { it.id == "11" }.favorita)
        assertTrue(repo.descargado.first())
        assertTrue(repo.fallo.value)
    }

    @Test fun v2ActualizaElContenidoSinPerderLaMarca() = runBlocking {
        repo.sincronizar()
        repo.cambiarFavorita("11")
        api.revision = 2
        repo.sincronizar()
        val charla = dao.actuales().single { it.id == "11" }
        assertEquals("Compose en producción avanzada", charla.titulo)
        assertEquals("Sala Puerto", charla.sala)
        assertTrue(charla.favorita)
    }

    @Test fun v3RetiraLa11AgregaLa90YConservaMarcasVigentes() = runBlocking {
        repo.sincronizar()
        repo.cambiarFavorita("11")
        repo.cambiarFavorita("12")
        api.revision = 3
        repo.sincronizar()
        val actuales = dao.actuales()
        assertFalse(actuales.any { it.id == "11" })
        assertFalse(actuales.single { it.id == "90" }.favorita)
        assertTrue(actuales.single { it.id == "12" }.favorita)
        assertFalse(actuales.filter { it.favorita }.any { it.id == "11" })
    }

    @Test fun charlaRetiradaRegresaSinSuMarcaAnterior() = runBlocking {
        repo.sincronizar()
        repo.cambiarFavorita("11")
        api.revision = 3
        repo.sincronizar()
        api.revision = 1
        repo.sincronizar()
        assertFalse(dao.actuales().single { it.id == "11" }.favorita)
    }

    @Test fun respetaAltaYBajaDeFavoritasDuranteLaPeticion() = runBlocking {
        repo.sincronizar()
        repo.cambiarFavorita("11")
        api.revision = 2
        val espera = CompletableDeferred<Unit>()
        api.espera = espera
        val descarga = async(start = CoroutineStart.UNDISPATCHED) { repo.sincronizar() }
        assertTrue(repo.cargando.value)
        repo.cambiarFavorita("11")
        repo.cambiarFavorita("12")
        espera.complete(Unit)
        descarga.await()
        val actuales = dao.actuales()
        assertFalse(actuales.single { it.id == "11" }.favorita)
        assertTrue(actuales.single { it.id == "12" }.favorita)
    }

    @Test fun dosActualizacionesDuranteLaEsperaProducenUnSoloIntento() = runBlocking {
        val espera = CompletableDeferred<Unit>()
        api.espera = espera
        val primera = async(start = CoroutineStart.UNDISPATCHED) { repo.sincronizar() }
        repo.sincronizar()
        assertEquals(1, api.intentos)
        espera.complete(Unit)
        primera.await()
        assertEquals(1, api.intentos)
        repo.sincronizar()
        assertEquals(2, api.intentos)
    }

    @Test fun cargaAutomaticaUnaVezYActualizacionExplicitaPermaneceDisponible() = runBlocking {
        repo.cargarInicial()
        repo.cargarInicial()
        repo.cargarInicial()
        assertEquals(1, api.intentos)
        repo.sincronizar()
        repo.cargarInicial()
        assertEquals(2, api.intentos)
    }

    @Test fun cargaAutomaticaFallidaNoSeRepitePorVolverALaPantalla() = runBlocking {
        api.disponible = false
        repo.cargarInicial()
        repo.cargarInicial()
        assertEquals(1, api.intentos)
        api.disponible = true
        repo.sincronizar()
        assertEquals(2, api.intentos)
        assertFalse(repo.fallo.value)
    }

    @Test fun v4EsUnaDescargaValidaAunqueEsteVacia() = runBlocking {
        repo.sincronizar()
        api.revision = 4
        repo.sincronizar()
        assertTrue(dao.actuales().isEmpty())
        assertTrue(repo.descargado.first())
        assertFalse(repo.fallo.value)
        api.disponible = false
        repo.sincronizar()
        assertTrue(dao.actuales().isEmpty())
        assertTrue(repo.descargado.first())
        assertTrue(repo.fallo.value)
    }

    @Test fun v4SeConservaAlReabrirLaBaseSinSenal() = runBlocking {
        val nombre = "vacia-persistente.db"
        app.deleteDatabase(nombre)
        var persistente = Room.databaseBuilder(app, Archivo::class.java, nombre).build()
        try {
            api.revision = 4
            AgendaRepository(api, persistente.cartelera()).sincronizar()
            persistente.close()
            persistente = Room.databaseBuilder(app, Archivo::class.java, nombre).build()
            api.disponible = false
            val nuevaSesion = AgendaRepository(api, persistente.cartelera())
            nuevaSesion.cargarInicial()
            assertTrue(nuevaSesion.descargado.first())
            assertTrue(persistente.cartelera().actuales().isEmpty())
            assertTrue(nuevaSesion.fallo.value)
        } finally {
            persistente.close()
            app.deleteDatabase(nombre)
        }
    }

    @Test fun unaFallaEnLaEscrituraRevierteLaInstantaneaCompleta() = runBlocking {
        repo.sincronizar()
        repo.cambiarFavorita("11")
        val anterior = dao.actuales()
        db.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER simular_fallo BEFORE INSERT ON charlas
            WHEN NEW.id = '90' BEGIN SELECT RAISE(ABORT, 'falla controlada'); END
        """.trimIndent())
        api.revision = 3
        repo.sincronizar()
        assertTrue(repo.fallo.value)
        assertEquals(anterior, dao.actuales())
        assertTrue(repo.descargado.first())
    }

    @Test fun migracionConservaCharlasYMarcasDeLaVersionInstalada() = runBlocking {
        val nombre = "migracion.db"
        app.deleteDatabase(nombre)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(app).name(nombre)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("""
                            CREATE TABLE charlas (
                                id TEXT NOT NULL PRIMARY KEY,
                                titulo TEXT NOT NULL, tema TEXT NOT NULL, dia INTEGER NOT NULL,
                                hora TEXT NOT NULL, sala TEXT NOT NULL, ponente TEXT NOT NULL,
                                resumen TEXT NOT NULL, favorita INTEGER NOT NULL
                            )
                        """.trimIndent())
                        db.execSQL("INSERT INTO charlas VALUES ('11','Anterior','Android',1,'09:00','Sala','Ponente','Resumen',1)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        helper.writableDatabase
        helper.close()
        val migrada = Room.databaseBuilder(app, Archivo::class.java, nombre)
            .addMigrations(Archivo.MIGRACION_1_2).build()
        try {
            assertTrue(migrada.cartelera().actuales().single().favorita)
            assertEquals("Anterior", migrada.cartelera().actuales().single().titulo)
            assertTrue(migrada.cartelera().observarDescarga().first())
        } finally {
            migrada.close()
            app.deleteDatabase(nombre)
        }
    }

    private class ApiControlada(private val app: Context) : ProgramaApi {
        var revision = 1
        var disponible = true
        var intentos = 0
        var espera: CompletableDeferred<Unit>? = null

        override suspend fun consultar(): List<FichaRemota> {
            intentos++
            val edicion = revision
            val online = disponible
            espera?.await()
            if (!online) throw IOException("Sin señal")
            val tipo = object : TypeToken<List<FichaRemota>>() {}.type
            return app.assets.open("programa-$edicion.json").bufferedReader().use {
                Gson().fromJson(it, tipo)
            }
        }
    }
}

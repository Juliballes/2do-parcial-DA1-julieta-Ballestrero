package ar.jornadas.data

import androidx.room.*
import retrofit2.http.GET
import kotlinx.coroutines.flow.Flow
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

data class FichaRemota(val id: String, val title: String, val track: String, val day: Int,
    val time: String, val room: String, val speaker: String, val summary: String)

interface ProgramaApi {
    @GET("programa") suspend fun consultar(): List<FichaRemota>
}

@Entity(tableName = "charlas")
data class Charla(@PrimaryKey val id: String, val titulo: String, val tema: String, val dia: Int,
    val hora: String, val sala: String, val ponente: String, val resumen: String,
    val favorita: Boolean = false)

// Su presencia distingue una descarga vacía válida de no haber descargado nunca.
@Entity(tableName = "estado_programa")
data class EstadoPrograma(@PrimaryKey val id: Int = 1)

@Dao
interface CarteleraDao {
    @Query("SELECT * FROM charlas ORDER BY dia, hora, sala")
    fun observar(): Flow<List<Charla>>

    @Query("SELECT EXISTS(SELECT 1 FROM estado_programa WHERE id = 1)")
    fun observarDescarga(): Flow<Boolean>

    @Query("SELECT * FROM charlas")
    suspend fun actuales(): List<Charla>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun guardar(fichas: List<Charla>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun registrarDescarga(estado: EstadoPrograma)

    @Query("DELETE FROM charlas")
    suspend fun borrarPrograma()

    @Transaction
    suspend fun reemplazarPrograma(fichas: List<Charla>) {
        // Se leen las marcas al aplicar la respuesta, no al iniciar la petición.
        val favoritas = actuales().filter { it.favorita }.map { it.id }.toSet()
        borrarPrograma()
        guardar(fichas.map { it.copy(favorita = it.id in favoritas) })
        registrarDescarga(EstadoPrograma())
    }

    @Query("UPDATE charlas SET favorita = NOT favorita WHERE id = :id")
    suspend fun alternar(id: String)
}

@Database(entities = [Charla::class, EstadoPrograma::class], version = 2, exportSchema = false)
abstract class Archivo : RoomDatabase() {
    abstract fun cartelera(): CarteleraDao

    companion object {
        val MIGRACION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS estado_programa (id INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("INSERT INTO estado_programa (id) SELECT 1 WHERE EXISTS(SELECT 1 FROM charlas)")
            }
        }
    }
}

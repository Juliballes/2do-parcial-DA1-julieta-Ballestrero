package ar.jornadas.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateViewModelFactory
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProgramaModelTest {
    @Test fun filtrosSeRestauranEnUnaNuevaActivityConEstadoGuardado() {
        val primera = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val model = ViewModelProvider(primera.get(),
            SavedStateViewModelFactory(RuntimeEnvironment.getApplication(), primera.get()))
            .get(ProgramaModel::class.java)
        model.elegirDia(2)
        model.elegirTema("Datos")
        val estado = Bundle()
        primera.pause().saveInstanceState(estado).stop().destroy()

        val restaurada = Robolectric.buildActivity(ComponentActivity::class.java).setup(estado)
        try {
            val nuevoModel = ViewModelProvider(restaurada.get(),
                SavedStateViewModelFactory(RuntimeEnvironment.getApplication(), restaurada.get()))
                .get(ProgramaModel::class.java)
            assertEquals(2, nuevoModel.dia.value)
            assertEquals("Datos", nuevoModel.tema.value)
        } finally {
            restaurada.pause().stop().destroy()
        }
    }

    @Test fun unaTareaNuevaArrancaSinFiltros() {
        val anterior = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val model = ViewModelProvider(anterior.get(),
            SavedStateViewModelFactory(RuntimeEnvironment.getApplication(), anterior.get()))
            .get(ProgramaModel::class.java)
        model.elegirDia(2)
        model.elegirTema("Datos")
        anterior.pause().stop().destroy()

        val nueva = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        try {
            val nuevoModel = ViewModelProvider(nueva.get(),
                SavedStateViewModelFactory(RuntimeEnvironment.getApplication(), nueva.get()))
                .get(ProgramaModel::class.java)
            assertEquals(0, nuevoModel.dia.value)
            assertEquals("Todos", nuevoModel.tema.value)
        } finally {
            nueva.pause().stop().destroy()
        }
    }
}

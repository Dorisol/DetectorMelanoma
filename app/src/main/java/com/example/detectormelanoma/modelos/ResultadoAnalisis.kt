package com.example.detectormelanoma.modelos

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

//Clase de datos para almacenar el resultado del análisis
@Parcelize

data class ResultadoAnalisis(
    val resultado: String,           // "Benigno", "Maligno", "Sospechoso"
    val radioPromedio: Double,       // Media de distancias
    val irregularidad: Double,       // Desviación estándar
    val recomendacion: String        // Texto de recomendación
): Parcelable

//num para tipos de resultado
enum class TipoResultado(val displayName: String, val color: Int) {
    BENIGNO("BENIGNO", 0xFF4CAF50.toInt()),        // Verde
    MALIGNO("MALIGNO", 0xFFF44336.toInt()),        // Rojo
}

//Objeto helper para generar recomendaciones
object RecomendacionHelper {

    fun obtenerRecomendacion(resultado: String): String {
        return when (resultado.uppercase()) {
            "BENIGNO" -> "Monitorear periódicamente. Consultar si hay cambios en tamaño, forma o color."
            "MALIGNO" -> "🚨 IMPORTANTE: Agendar cita con un dermatólogo lo antes posible para evaluación profesional."
            else -> "Consultar con un especialista para mayor seguridad."
        }
    }

    fun obtenerColor(resultado: String): Int {
        return when (resultado.uppercase()) {
            "BENIGNO" -> TipoResultado.BENIGNO.color
            "MALIGNO" -> TipoResultado.MALIGNO.color
            else -> 0xFF757575.toInt() // Gris por defecto
        }
    }
}
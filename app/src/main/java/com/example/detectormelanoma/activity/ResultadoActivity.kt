package com.example.detectormelanoma.activity

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.detectormelanoma.databinding.ActivityResultadoBinding
import com.example.detectormelanoma.modelos.RecomendacionHelper
import com.example.detectormelanoma.modelos.ResultadoAnalisis

class ResultadoActivity : AppCompatActivity(){

    private lateinit var binding: ActivityResultadoBinding
    private lateinit var resultado: ResultadoAnalisis

    companion object {
        // Variable temporal para pasar el bitmap
        var imagenTemporal: Bitmap? = null
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityResultadoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //Obtener resultado del intent
        resultado = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("resultado", ResultadoAnalisis::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("resultado")
        } ?: run {
            finish()
            return
        }

        configurarUI()
        configurarListeners()
    }

    private fun configurarUI() {
        // Mostrar imagen analizada
        imagenTemporal?.let {
            binding.ivImagenAnalizada.setImageBitmap(it)
        }

        // Configurar resultado principal
        binding.tvResultado.text = resultado.resultado
        binding.tvResultado.setTextColor(RecomendacionHelper.obtenerColor(resultado.resultado))

        // Configurar color del card según resultado
        when (resultado.resultado.uppercase()) {
            "BENIGNO" -> {
                binding.cardResultadoPrincipal.setCardBackgroundColor(0xFFE8F5E9.toInt())
            }
            "MALIGNO" -> {
                binding.cardResultadoPrincipal.setCardBackgroundColor(0xFFFFEBEE.toInt())
            }
        }

        // Mostrar métricas
        binding.tvRadioPromedio.text = String.format("%.2f", resultado.radioPromedio)
        binding.tvIrregularidad.text = String.format("%.2f", resultado.irregularidad)

        // Mostrar recomendación
        binding.tvRecomendacion.text = resultado.recomendacion
    }

    private fun configurarListeners() {
        binding.btnNuevoAnalisis.setOnClickListener {
            // Limpiar imagen temporal
            imagenTemporal?.recycle()
            imagenTemporal = null

            // Crear nuevo intent para limpiar el estado anterior
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)

            // Volver a MainActivity
            finish()
        }

        binding.btnCompartir.setOnClickListener {
            compartirResultado()
        }

        // Soporte para botón "Atrás" en ActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun compartirResultado() {
        val textoCompartir = """
            Resultado del Análisis de Melanoma
            
            Diagnóstico: ${resultado.resultado}
           
            
            Métricas:
            - Radio Promedio: ${String.format("%.2f", resultado.radioPromedio)}
            - Irregularidad: ${String.format("%.2f", resultado.irregularidad)}
            
            Recomendación: ${resultado.recomendacion}
            
            ⚠️ Esta aplicación NO reemplaza el diagnóstico médico profesional.
        """.trimIndent()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, textoCompartir)
            putExtra(Intent.EXTRA_SUBJECT, "Resultado de Análisis de Melanoma")
        }

        startActivity(Intent.createChooser(intent, "Compartir resultado"))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.ivImagenAnalizada.setImageDrawable(null)
        imagenTemporal?.recycle()
        imagenTemporal = null
    }

}


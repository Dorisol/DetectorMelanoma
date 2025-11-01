package com.example.detectormelanoma.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.detectormelanoma.databinding.ActivityMainBinding
import com.example.detectormelanoma.modelos.ProcesadorImagen
import com.example.detectormelanoma.modelos.RecomendacionHelper
import com.example.detectormelanoma.modelos.ResultadoAnalisis
import com.example.detectormelanoma.ui.theme.DetectorMelanomaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.IOException

class MainActivity: AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imagenActual: Bitmap? = null
    private var fotoUri: Uri? = null
    private lateinit var procesadorImagen: ProcesadorImagen


    // Launcher para permisos de cámara
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            abrirCamara()
        } else {
            Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher para permisos de galería
    private val requestGaleriaPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            abrirGaleria()
        } else {
            Toast.makeText(this, "Permiso de galería denegado", Toast.LENGTH_SHORT).show()
        }
    }

    // Launcher para captura de foto
    private val tomarFotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            fotoUri?.let { uri ->
                cargarImagenDesdeUri(uri)
            }
        }
    }

    // Launcher para selección de galería
    private val seleccionarGaleriaLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            cargarImagenDesdeUri(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar OpenCV
        if (!OpenCVLoader.initDebug()) {
            Toast.makeText(this, "Error al inicializar OpenCV", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Inicializar procesador de imágenes
        procesadorImagen = ProcesadorImagen()
        configurarListeners()
    }

    private fun configurarListeners() {
        binding.btnTomarFoto.setOnClickListener {
            verificarPermisoYTomarFoto()
        }

        binding.btnAbrirGaleria.setOnClickListener {
            verificarPermisoYAbrirGaleria()
        }

        binding.btnAnalizar.setOnClickListener {
            imagenActual?.let { bitmap ->
                analizarImagen(bitmap)
            }
        }
    }

    private fun verificarPermisoYTomarFoto() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                abrirCamara()
            }
            else -> {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun verificarPermisoYAbrirGaleria() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                abrirGaleria()
            }
            else -> {
                requestGaleriaPermission.launch(permission)
            }
        }
    }

    private fun abrirCamara() {
        val fotoFile = try {
            crearArchivoTemporal()
        } catch (e: IOException) {
            Toast.makeText(this, "Error al crear archivo de foto", Toast.LENGTH_SHORT).show()
            return
        }
        fotoUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", fotoFile)

        // Solución: Verificar que no sea null antes de lanzar
        fotoUri?.let { uri ->
            tomarFotoLauncher.launch(uri)
        }
    }

    private fun abrirGaleria() {
        seleccionarGaleriaLauncher.launch("image/*")
    }

    private fun crearArchivoTemporal(): File {
        val directorio = File(getExternalFilesDir(null), "Pictures")
        if (!directorio.exists()) {
            directorio.mkdirs()
        }
        return File.createTempFile(
            "JPEG_${System.currentTimeMillis()}_",
            ".jpg",
            directorio
        )
    }

    private fun cargarImagenDesdeUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                imagenActual = bitmap
                binding.ivImagen.setImageBitmap(bitmap)
                binding.layoutPlaceholder.visibility = View.GONE
                binding.btnAnalizar.isEnabled = true
            } else {
                Toast.makeText(this, "Error al cargar la imagen", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    private fun analizarImagen(bitmap: Bitmap) {
        // Mostrar loading
        binding.progressBar.visibility = View.VISIBLE
        binding.tvCargando.visibility = View.VISIBLE
        binding.btnAnalizar.isEnabled = false

        lifecycleScope.launch {
            try {
                val resultado = withContext(Dispatchers.Default) {
                    // Convertir Bitmap a Mat de OpenCV en escala de grises
                    val mat = Mat()
                    Utils.bitmapToMat(bitmap, mat)

                    val matGris = Mat()
                    Imgproc.cvtColor(mat, matGris, Imgproc.COLOR_RGB2GRAY)

                    // Procesar y analizar.
                    val (media, std) = procesadorImagen.procesarYAnalizarLunar(matGris)

                    // Liberar memoria
                    mat.release()
                    matGris.release()

                    // Clasificar
                    if (media != null && std != null) {
                        clasificarLunar(media, std)
                    } else {
                        null
                    }
                }

                // Ocultar loading
                binding.progressBar.visibility = View.GONE
                binding.tvCargando.visibility = View.GONE
                binding.btnAnalizar.isEnabled = true

                // Navegar a resultados
                if (resultado != null) {
                    val intent = Intent(this@MainActivity, ResultadoActivity::class.java)
                    intent.putExtra("resultado", resultado)
                    // Guardar bitmap temporalmente (en app real, usa file o URI)
                    ResultadoActivity.imagenTemporal = bitmap
                    startActivity(intent)
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "No se pudo detectar el lunar en la imagen",
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.tvCargando.visibility = View.GONE
                binding.btnAnalizar.isEnabled = true

                Toast.makeText(
                    this@MainActivity,
                    "Error al procesar: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }


    private fun clasificarLunar(media: Double, std: Double): ResultadoAnalisis {
        // Umbrales basados en el notebok
        val UMBRAL_MEDIA = 20.0
        val UMBRAL_STD = 3.0

        var puntosMalignidad = 0

        if (media > UMBRAL_MEDIA) puntosMalignidad++
        if (std > UMBRAL_STD) puntosMalignidad++

        val resultado = when (puntosMalignidad) {
            2 -> "MALIGNO"
            else -> "BENIGNO"
        }

        val recomendacion = RecomendacionHelper.obtenerRecomendacion(resultado)

        return ResultadoAnalisis(
            resultado = resultado,
            radioPromedio = media,
            irregularidad = std,
            recomendacion = recomendacion
        )
    }


    override fun onDestroy() {
        super.onDestroy()
        imagenActual = null
    }

}


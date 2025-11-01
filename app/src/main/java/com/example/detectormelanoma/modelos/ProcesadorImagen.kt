package com.example.detectormelanoma.modelos

import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import kotlin.math.pow
import kotlin.math.sqrt

class ProcesadorImagen {
    fun procesarYAnalizarLunar(imagenOriginal: Mat): Pair<Double?, Double?> {
        try {

            // 1. NORMALIZAR TAMAÑO (107 * 83 como en el notebook)
            val imagenRedimensionada = Mat()
            Imgproc.resize(imagenOriginal, imagenRedimensionada, Size(107.0, 83.0))

            // 2. FILTROO GAUSSIANO
            val gaussBlur = Mat()
            Imgproc.GaussianBlur(imagenRedimensionada, gaussBlur, Size(7.0, 7.0), 0.0)

            // 3. BINARIZACIÓN ADAPTATIVA
            val binarizada = Mat()
            Imgproc.adaptiveThreshold(
                gaussBlur,
                binarizada,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                45,
                5.0
            )

            // 4. COMPONENTES CONECTADOS
            val etiquetas = Mat()  //id del objeto al que pertenece
            val stats = Mat()      //area, posicion, x,y, ancho, alto
            val centroides = Mat()
            val cantObjetos = Imgproc.connectedComponentsWithStats(
                binarizada,
                etiquetas,
                stats,
                centroides,
                8,
                CvType.CV_32S
            )

            // Validar que haya objetos además del fondo
            //Si solo hay fondo, no hay objetos detectados -> no hay lunar
            if (cantObjetos < 2) {
                liberarMats(imagenRedimensionada, gaussBlur, binarizada, etiquetas, stats, centroides)
                return Pair(null, null)
            }

            // Encontrar el objeto más grande (el lunar), excluyendo el fondo (índice 0)
            var idxLunar = 1
            var areaMaxima = stats.get(1, Imgproc.CC_STAT_AREA)[0]

            for (i in 2 until cantObjetos) {
                val area = stats.get(i, Imgproc.CC_STAT_AREA)[0]
                if (area > areaMaxima) {
                    areaMaxima = area
                    idxLunar = i
                }
            }

            // 5. CREAR MÁSCARA DEL LUNAR
            //imagen negra, que pone en blanco solo pixelees del lunar. El resto es negro
            val mascaraLunar = Mat.zeros(imagenOriginal.size(), CvType.CV_8U)
            for (y in 0 until etiquetas.rows()) {
                for (x in 0 until etiquetas.cols()) {
                    if (etiquetas.get(y, x)[0].toInt() == idxLunar) {
                        mascaraLunar.put(y, x, 255.0)
                    }
                }
            }

            // 6. RELLENAR AGUJEROS (Alternativa a scipy.binary_fill_holes)
            val mascaraRellena = rellenarAgujeros(mascaraLunar)

            // 7. ENCONTRAR CONTORNOS (borde del lunar)
            val contornos = ArrayList<MatOfPoint>()
            val jerarquia = Mat()
            Imgproc.findContours(
                mascaraRellena,
                contornos,
                jerarquia,
                Imgproc.RETR_EXTERNAL,            //contorno exterior
                Imgproc.CHAIN_APPROX_SIMPLE      //reduce puntos redundantes
            )

            if (contornos.isEmpty()) {
                liberarMats(imagenRedimensionada, gaussBlur, binarizada, etiquetas, stats, centroides,
                    mascaraLunar, mascaraRellena, jerarquia)
                return Pair(null, null)
            }

            // Seleccionar el contorno más grande
            val contornoPrincipal = contornos.maxByOrNull { Imgproc.contourArea(it) }
                ?: run {
                    liberarMats(imagenRedimensionada, gaussBlur, binarizada, etiquetas, stats, centroides,
                        mascaraLunar, mascaraRellena, jerarquia)
                    return Pair(null, null)
                }

            // 8. CALCULAR DISTANCIAS AL CENTROIDE
            val centroideX = centroides.get(idxLunar, 0)[0]
            val centroideY = centroides.get(idxLunar, 1)[0]

            val puntos = contornoPrincipal.toArray()
            val distancias = puntos.map { punto ->
                //distancia euclidiana
                sqrt((punto.x - centroideX).pow(2.0) + (punto.y - centroideY).pow(2.0))
            }

            // 9. CALCULAR MEDIA Y DESVIACIÓN ESTÁNDAR DE LAS DISTANCIAS
            val media = distancias.average()
            val varianza = distancias.map { (it - media).pow(2.0) }.average()
            val desviacionEstandar = sqrt(varianza)

            // Liberar memoria (Mat usados)
            liberarMats( imagenRedimensionada, gaussBlur, binarizada, etiquetas, stats, centroides,
                mascaraLunar, mascaraRellena, jerarquia)
            contornos.forEach { it.release() }

            return Pair(media, desviacionEstandar)

        } catch (e: Exception) {
            e.printStackTrace()
            return Pair(null, null)
        }
    }

    //Rellena agujeros en una máscara binaria.
    //Esta es una alternativa a scipy.ndimage.binary_fill_holes

    private fun rellenarAgujeros(mascara: Mat): Mat {
        val resultado = Mat()

        // Método 1: Operación morfológica de Closing  (dilatación + erosion)
        // "pincel" para rellenar

        //dilatacion: expandir areas blancas (cierra agujeros pequeño)
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE,
            Size(5.0, 5.0)
        )

        //erosion. Reduce de vuelta al tamaño original
        Imgproc.morphologyEx(
            mascara,
            resultado,
            Imgproc.MORPH_CLOSE,
            kernel
        )

        kernel.release()
        return resultado
    }

    // Libera memoria de múltiples Mats
    private fun liberarMats(vararg mats: Mat) {
        mats.forEach { it.release() }
    }




}
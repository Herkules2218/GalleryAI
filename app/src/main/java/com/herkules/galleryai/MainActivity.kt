package com.herkules.galleryai

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var scan: Button
    private lateinit var copy: Button
    private val matches = mutableListOf<Pair<android.net.Uri, String>>()
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    private val categories = linkedMapOf(
        "Автомобілі" to listOf("car", "vehicle", "motorcycle", "automotive", "truck", "wheel"),
        "Спорт" to listOf("sport", "wrestling", "athlete", "fitness", "gym", "exercise", "ball"),
        "Тварини" to listOf("animal", "dog", "cat", "bird", "pet", "wildlife"),
        "Природа" to listOf("nature", "landscape", "mountain", "forest", "flower", "tree", "sky", "beach"),
        "Їжа" to listOf("food", "dish", "meal", "cuisine", "fruit", "vegetable"),
        "Люди" to listOf("person", "face", "selfie", "portrait", "people"),
        "Документи" to listOf("document", "text", "paper", "receipt"),
        "Будівлі" to listOf("building", "architecture", "house", "city")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 44, 32, 24)
        }
        val heading = TextView(this).apply { text = "HERCULES AI GALLERY"; textSize = 24f }
        status = TextView(this).apply {
            text = "Натисни «Сканувати». Фото не видаляються. Категорії приблизні."
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }
        scan = Button(this).apply { text = "Сканувати галерею"; setOnClickListener { requestOrScan() } }
        copy = Button(this).apply {
            text = "Копіювати в тематичні папки"
            isEnabled = false
            setOnClickListener { copyPhotos() }
        }
        root.addView(heading)
        root.addView(status)
        root.addView(scan)
        root.addView(copy)
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    private fun requestOrScan() {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), 17)
        } else startScan()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 17 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) startScan()
        else status.text = "Потрібен дозвіл на всі фотографії для повного сканування."
    }

    private fun startScan() {
        scan.isEnabled = false
        copy.isEnabled = false
        matches.clear()
        status.text = "Шукаю фотографії..."
        executor.execute {
            val photos = mutableListOf<android.net.Uri>()
            val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            contentResolver.query(collection, arrayOf(MediaStore.Images.Media._ID),
                null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    photos.add(android.content.ContentUris.withAppendedId(collection, cursor.getLong(idCol)))
                }
            }
            handler.post { analyzeNext(photos, 0) }
        }
    }

    private fun analyzeNext(photos: List<android.net.Uri>, index: Int) {
        if (index >= photos.size) {
            scan.isEnabled = true
            copy.isEnabled = matches.isNotEmpty()
            val counts = matches.groupingBy { it.second }.eachCount()
            status.text = "Перевірено: ${photos.size}; розпізнано: ${matches.size}\n" +
                counts.entries.joinToString("\n") { "${it.key}: ${it.value}" } +
                "\n\nФото без впевненої категорії потраплять у «Потребує перевірки». Натисни кнопку нижче для копіювання."
            return
        }
        if (index % 10 == 0) status.text = "Аналізую ${index + 1} / ${photos.size}..."
        val uri = photos[index]
        try {
            val image = InputImage.fromFilePath(this, uri)
            labeler.process(image).addOnSuccessListener { labels ->
                val best = labels.filter { it.confidence >= 0.65f }
                    .sortedByDescending { it.confidence }
                    .firstNotNullOfOrNull { label ->
                        categories.entries.firstOrNull { entry ->
                            entry.value.any { word -> label.text.contains(word, ignoreCase = true) }
                        }?.key
                    }
                matches.add(uri to (best ?: "Потребує перевірки"))
                analyzeNext(photos, index + 1)
            }.addOnFailureListener { analyzeNext(photos, index + 1) }
        } catch (_: Exception) { analyzeNext(photos, index + 1) }
    }

    private fun copyPhotos() {
        copy.isEnabled = false
        scan.isEnabled = false
        status.text = "Копіюю фотографії. Не закривай застосунок..."
        val items = matches.toList()
        executor.execute {
            var success = 0
            var failed = 0
            for ((uri, category) in items) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "galleryai_${System.currentTimeMillis()}_${success + failed}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Hercules AI Gallery/$category")
                        put(MediaStore.Images.Media.IS_PENDING, 1)
                    }
                    val outputUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                        ?: throw IllegalStateException("Не вдалося створити файл")
                    try {
                        contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input)
                            contentResolver.openOutputStream(outputUri).use { output ->
                                requireNotNull(output)
                                input.copyTo(output)
                            }
                        }
                        contentResolver.update(outputUri, ContentValues().apply {
                            put(MediaStore.Images.Media.IS_PENDING, 0)
                        }, null, null)
                        success++
                    } catch (e: Exception) {
                        contentResolver.delete(outputUri, null, null)
                        throw e
                    }
                } catch (_: Exception) { failed++ }
            }
            handler.post {
                status.text = "Готово! Скопійовано: $success; помилок: $failed.\nОригінали не змінені. Папки: Pictures/Hercules AI Gallery."
                scan.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        labeler.close()
        executor.shutdown()
        super.onDestroy()
    }
}

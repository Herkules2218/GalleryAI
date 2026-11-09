package com.herkules.galleryai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.HorizontalScrollView
import android.widget.EditText
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import android.view.Window
import android.view.Gravity
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.graphics.Rect
import android.widget.FrameLayout
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
    private lateinit var root: LinearLayout
    private lateinit var body: LinearLayout
    private lateinit var progress: ProgressBar
    private var scanning = false
    private var moving = false
    private var moveIndex = 0
    private var movedCount = 0
    private var moveFailed = 0
    private val moveItems = mutableListOf<Pair<Uri, String>>()
    private val moveRequestCode = 811
    private var page = "Головна"
    private val custom = linkedSetOf<String>()
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
        "Будівлі" to listOf("building", "architecture", "house", "city", "skyscraper"),
        "Транспорт" to listOf("train", "airplane", "aircraft", "bus", "ship", "boat", "bicycle"),
        "Техніка" to listOf("computer", "laptop", "phone", "electronics", "keyboard", "screen"),
        "Одяг" to listOf("clothing", "fashion", "shoe", "dress", "jacket", "apparel"),
        "Мистецтво" to listOf("art", "painting", "drawing", "illustration", "sculpture"),
        "Інтер’єр" to listOf("furniture", "room", "interior", "bedroom", "kitchen"),
        "Скріншоти" to listOf("screenshot", "interface", "website")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.decorView.systemUiVisibility = 0
        window.statusBarColor = Color.rgb(12, 17, 29)
        window.navigationBarColor = Color.rgb(12, 17, 29)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 18, 16, 20)
            setBackgroundColor(Color.rgb(12, 17, 29))
        }
        val heading = TextView(this).apply {
            text = "✦ HERCULES AI"
            textSize = 21f
            setTextColor(Color.rgb(102, 232, 207))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(4, 8, 0, 12)
            maxLines = 1
        }
        root.addView(heading)
        val tabs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; isFillViewport = false }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Головна", "Альбоми", "ШІ-Помічник", "Очищення", "Налаштування").forEach { name ->
            nav.addView(button(name) { page = name; renderPage() }.apply { textSize = 11f; isAllCaps = false; minWidth = 0; minimumWidth = 0; setPadding(14, 0, 14, 0) })
        }
        tabs.addView(nav)
        root.addView(tabs)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(102, 232, 207))
        }
        root.addView(progress)
        status = TextView(this).apply {
            text = "Готовий аналізувати галерею. Оригінали не змінюються."
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(8, 18, 8, 18)
        }
        root.addView(status)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(body)
        val scroll = ScrollView(this).apply { addView(root); setFillViewport(true); setBackgroundColor(Color.rgb(12, 17, 29)); isVerticalScrollBarEnabled = false }
        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            scroll.requestApplyInsets()
        }
        scan = button("✦ СКАНУВАТИ ГАЛЕРЕЮ") { requestOrScan() }
        copy = button("ПЕРЕМІСТИТИ ОРИГІНАЛИ В ПАПКИ") { confirmMove() }
        copy.isEnabled = false
        renderPage()
    }

    private fun button(title: String, action: () -> Unit): Button =
        Button(this).apply {
            text = title
            textSize = 12f
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(32, 75, 79))
            setOnClickListener { action() }
        }

    private fun card(title: String, detail: String, action: (() -> Unit)? = null) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 15, 18, 15)
            background = GradientDrawable().apply {
                setColor(Color.rgb(29, 40, 57))
                cornerRadius = 18f
            }
        }
        box.addView(TextView(this).apply {
            text = title
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(102, 232, 207))
        })
        box.addView(TextView(this).apply {
            text = detail
            textSize = 13f
            setTextColor(Color.WHITE)
            setPadding(0, 8, 0, 0)
        })
        if (action != null) box.setOnClickListener { action() }
        body.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12 })
    }

    private fun renderPage() {
        body.removeAllViews()
        when (page) {
            "Головна" -> {
                card("РОЗУМНА ГАЛЕРЕЯ", "Локальний аналіз фотографій. Усі доступні фото отримують категорію, навіть якщо вона потребує перевірки.")
                body.addView(scan)
                body.addView(copy)
                card("БЕЗ ДУБЛІКАТІВ", "Переміщення змінює папку існуючого фото. Перед початком Android попросить підтвердження доступу.")
                val counts = matches.groupingBy { it.second }.eachCount()
                card("СТАТИСТИКА", "Категоризовано: ${matches.size}\n" +
                    counts.entries.joinToString("\n") { "${it.key}: ${it.value}" })
            }
            "Альбоми" -> {
                body.addView(button("＋ СТВОРИТИ КАТЕГОРІЮ") { addCategory() })
                val counts = matches.groupingBy { it.second }.eachCount()
                (categories.keys + custom + listOf("Потребує перевірки")).distinct().forEach { name ->
                    card("📁 $name", "${counts[name] ?: 0} фото · Натисни, щоб переглянути", { showAlbum(name) })
                }
            }
            "ШІ-Помічник" -> {
                card("✦ РЕКОМЕНДАЦІЇ ШІ", "Пропозиції формуються з результатів сканування. Жодних автоматичних видалень.")
                val counts = matches.groupingBy { it.second }.eachCount()
                val unknown = counts["Потребує перевірки"] ?: 0
                card("ПЕРЕВІРИТИ НЕВИЗНАЧЕНІ", "$unknown фотографій можна вручну розподілити по альбомах.", { page = "Альбоми"; showAlbum("Потребує перевірки") })
                if ((counts["Автомобілі"] ?: 0) > 20) {
                    card("ПРОПОЗИЦІЯ: ПІДКАТЕГОРІЯ", "Можна створити окрему папку для конкретної марки авто. Назву задаєш ти.", { addCategory() })
                }
                body.addView(button("＋ НОВА КАТЕГОРІЯ") { addCategory() })
                card("ЧАТ ІЗ ШІ", "Повноцінний діалоговий помічник ще розробляється. Зараз доступні локальні підказки та ручне виправлення категорій.")
            }
            "Очищення" -> {
                card("БЕЗПЕЧНЕ ОЧИЩЕННЯ", "Фотографії не видаляються автоматично. Оригінали залишаються на місці.")
                card("ДУБЛІКАТИ", "Захист від повторного копіювання вже додано для файлів, скопійованих цією версією. Пошук схожих фото ще в розробці.")
            }
            else -> {
                card("НАЛАШТУВАННЯ", "Обробка фотографій відбувається на пристрої за допомогою ML Kit.")
                card("ЗАХИСТ ДАНИХ", "Сортування переміщує оригінали з дозволу Android. Перед початком перевір результати категоризації. Видалення вимкнене.")
            }
        }
    }

    private fun addCategory() {
        val input = EditText(this).apply { hint = "Назва категорії" }
        AlertDialog.Builder(this).setTitle("Нова категорія").setView(input)
            .setPositiveButton("Створити") { _, _ ->
                val name = input.text.toString().trim().replace("/", " ").replace("\\", " ")
                if (name.isNotEmpty() && name.length <= 50) { custom.add(name); renderPage() }
            }.setNegativeButton("Скасувати", null).show()
    }

    private fun showAlbum(name: String) {
        body.removeAllViews()
        body.addView(button("← ДО АЛЬБОМІВ") { renderPage() })
        val items = matches.indices.filter { matches[it].second == name }
        card("📁 $name", "${items.size} фотографій. Натисни на фото, щоб змінити категорію.")
        items.take(150).forEach { index ->
            val item = matches[index]
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 6, 0, 6) }
            val thumb = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                try { setImageURI(item.first) } catch (_: Exception) {}
            }
            row.addView(thumb, LinearLayout.LayoutParams(110, 110))
            row.addView(button("Змінити категорію") { chooseCategory(index, name) })
            body.addView(row)
        }
        if (items.size > 150) card("ПОКАЗАНО 150 ФОТО", "Для швидкої роботи відображено перші 150 фотографій.")
    }

    private fun chooseCategory(index: Int, oldName: String) {
        val options = (categories.keys + custom + listOf("Потребує перевірки")).distinct().toTypedArray()
        AlertDialog.Builder(this).setTitle("Перенести до категорії")
            .setItems(options) { _, selected ->
                matches[index] = matches[index].first to options[selected]
                showAlbum(oldName)
            }.setNegativeButton("Скасувати", null).show()
    }

    private fun queryName(uri: Uri): String {
        return try {
            contentResolver.query(uri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) ?: "" else ""
            } ?: ""
        } catch (_: Exception) { "" }
    }

    private fun confirmMove() {
        if (scanning || moving || matches.isEmpty()) return
        val review = matches.count { it.second == "Потребує перевірки" }
        AlertDialog.Builder(this)
            .setTitle("Перемістити оригінали?")
            .setMessage("Буде переміщено ${matches.size} фотографій у Pictures/Hercules AI Gallery. Оригінальні файли залишаться тими самими, але змінять папки. ${review} фото потребують перевірки. Рекомендуємо спочатку переглянути альбоми. Android може попросити дозвіл.")
            .setPositiveButton("ПЕРЕМІСТИТИ") { _, _ ->
                moveItems.clear()
                moveItems.addAll(matches)
                moveIndex = 0
                movedCount = 0
                moveFailed = 0
                moving = true
                scan.isEnabled = false
                copy.isEnabled = false
                moveNext()
            }
            .setNegativeButton("Скасувати", null).show()
    }

    private fun moveNext() {
        if (moveIndex >= moveItems.size) {
            moving = false
            scan.isEnabled = true
            copy.isEnabled = true
            status.text = "Переміщено: $movedCount; помилок: $moveFailed. Файли без копій. Перевір альбоми в Honor Gallery."
            return
        }
        val (uri, category) = moveItems[moveIndex]
        val target = "Pictures/Hercules AI Gallery/${category.replace("/", " ").replace("\\", " " )}/"
        val values = ContentValues().apply { put(MediaStore.Images.Media.RELATIVE_PATH, target) }
        try {
            val changed = contentResolver.update(uri, values, null, null)
            if (changed > 0) movedCount++ else moveFailed++
            moveIndex++
            if (moveIndex % 20 == 0) status.text = "Переміщую ${moveIndex}/${moveItems.size}..."
            handler.post { moveNext() }
        } catch (e: SecurityException) {
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    val request = MediaStore.createWriteRequest(contentResolver, listOf(uri))
                    startIntentSenderForResult(request.intentSender, moveRequestCode, null, 0, 0, 0)
                } else if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                    startIntentSenderForResult(e.userAction.actionIntent.intentSender, moveRequestCode, null, 0, 0, 0)
                } else {
                    moveFailed++
                    moveIndex++
                    handler.post { moveNext() }
                }
            } catch (_: Exception) {
                moveFailed++
                moveIndex++
                handler.post { moveNext() }
            }
        } catch (_: Exception) {
            moveFailed++
            moveIndex++
            handler.post { moveNext() }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == moveRequestCode && moving) {
            if (resultCode != RESULT_OK) {
                moving = false
                scan.isEnabled = true
                copy.isEnabled = true
                status.text = "Переміщення зупинено. Переміщено: $movedCount; помилок: $moveFailed."
            } else handler.post { moveNext() }
        }
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
        scanning = true
        progress.visibility = View.VISIBLE
        progress.progress = 0
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
            scanning = false
            progress.progress = 100
            scan.isEnabled = true
            copy.isEnabled = matches.isNotEmpty()
            val counts = matches.groupingBy { it.second }.eachCount()
            status.text = "Перевірено: ${photos.size}; розпізнано: ${matches.size}\n" +
                counts.entries.joinToString("\n") { "${it.key}: ${it.value}" } +
                "\n\nФото без впевненої категорії потраплять у «Потребує перевірки». Натисни кнопку нижче для копіювання."
            renderPage()
            return
        }
        if (index % 10 == 0) progress.progress = if (photos.isEmpty()) 0 else index * 100 / photos.size
        if (index % 10 == 0) status.text = "Аналізую ${index + 1} / ${photos.size}..."
        val uri = photos[index]
        try {
            val image = InputImage.fromFilePath(this, uri)
            labeler.process(image).addOnSuccessListener { labels ->
                val best = labels.filter { it.confidence >= 0.45f }
                    .sortedByDescending { it.confidence }
                    .firstNotNullOfOrNull { label ->
                        categories.entries.firstOrNull { entry ->
                            entry.value.any { word -> label.text.contains(word, ignoreCase = true) }
                        }?.key
                    }
                val fileName = queryName(uri)
                val finalCategory = if (fileName.startsWith("Screenshot", true) || fileName.startsWith("Знімок екрана", true)) "Скріншоти" else best ?: "Потребує перевірки"
                matches.add(uri to finalCategory)
                analyzeNext(photos, index + 1)
            }.addOnFailureListener { matches.add(uri to "Потребує перевірки"); analyzeNext(photos, index + 1) }
        } catch (_: Exception) { matches.add(uri to "Потребує перевірки"); analyzeNext(photos, index + 1) }
    }

    override fun onDestroy() {
        labeler.close()
        executor.shutdown()
        super.onDestroy()
    }
}

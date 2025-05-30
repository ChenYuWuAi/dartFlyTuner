package com.iiise.dartflytuner

import android.annotation.SuppressLint
import android.content.ClipData
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.json.JSONObject
import android.graphics.Color
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.set
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import org.json.JSONArray
import java.util.Collection
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.EditText
import org.json.JSONException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var editPrimaryYaw: TextInputEditText
    private lateinit var editPrimaryForce: TextInputEditText
    private lateinit var editPrimaryForceOffset: TextInputEditText
    private lateinit var editAuxiliaryYawOffsets: Array<TextInputEditText>
    private lateinit var editAuxiliaryForceOffsets: Array<TextInputEditText>
    private lateinit var editDartLaunchProcessOffsetBegin: TextInputEditText
    private lateinit var editDartLaunchProcessOffsetEnd: TextInputEditText
    private lateinit var editTargetAutoAimXAxis: TextInputEditText
    private lateinit var editAdaptiveVelocityExpected: Array<TextInputEditText>
    private lateinit var switchAdaptiveVelocityEnabled: SwitchMaterial
    private lateinit var switchAutoAimEnabled: SwitchMaterial
    private lateinit var checkboxes: Map<String, MaterialCheckBox>
    private lateinit var ivQR: ImageView
    private lateinit var toggleCommandType: MaterialButtonToggleGroup
    private lateinit var btnSelectAll: MaterialButton

    private val qrHandler = Handler(Looper.getMainLooper())
    private var qrRunnable: Runnable? = null
    private val DEBOUNCE_MS = 300L

    // Request code for QR scanner
    private val REQUEST_SCAN_QR = 1001


    fun showFilePicker(title: String, files: Array<String>, onSelect: (String) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(files) { _, which ->
                onSelect(files[which])
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 解析 JSON 配置文件并填充到各个控件
     * @param json: JSON 对象，包含CommandType和Data
     */
    fun parseJsonToWidget(json: JSONObject) {
        // 解析 JSON 对象
        val data = json.getJSONObject("data")
        // 填充到各个控件
        editPrimaryYaw.setText(data.optString("primary_yaw", ""))
        editPrimaryForce.setText(data.optString("primary_force", ""))
        editPrimaryForceOffset.setText(data.optString("primary_force_offset", ""))
        editDartLaunchProcessOffsetBegin.setText(
            data.optString("dart_launch_process_offset_begin", "")
        )
        editDartLaunchProcessOffsetEnd.setText(
            data.optString("dart_launch_process_offset_end", "")
        )
        editTargetAutoAimXAxis.setText(data.optString("target_auto_aim_x_axis", ""))
        switchAutoAimEnabled.isChecked = data.optBoolean("auto_aim_enabled", false)
        // 填充辅助偏移量
        val auxiliaryYawOffsets = data.optJSONArray("auxiliary_yaw_offsets")
        if (auxiliaryYawOffsets != null) {
            for (i in 0 until auxiliaryYawOffsets.length()) {
                editAuxiliaryYawOffsets[i].setText(auxiliaryYawOffsets.getString(i))
            }
        }
        val auxiliaryForceOffsets = data.optJSONArray("auxiliary_force_offsets")
        if (auxiliaryForceOffsets != null) {
            for (i in 0 until auxiliaryForceOffsets.length()) {
                editAuxiliaryForceOffsets[i].setText(auxiliaryForceOffsets.getString(i))
            }
        }

        // 如果存在自适应速度控制配置，则填充
        val adaptiveVelocityConfig = json.optJSONObject("adaptive_velocity_control")
        if (adaptiveVelocityConfig != null) {
            val expectedVelocities = adaptiveVelocityConfig.optJSONArray("adaptive_velocity_expected")
            if (expectedVelocities != null) {
                for (i in 0 until expectedVelocities.length()) {
                    editAdaptiveVelocityExpected[i].setText(expectedVelocities.getString(i))
                }
            }
            switchAdaptiveVelocityEnabled.isChecked =
                adaptiveVelocityConfig.optBoolean("adaptive_velocity_enabled", false)
        }
        // 否则不做任何操作
    }


    // 读取并应用配置
    private fun loadConfig() {
        val files =
            filesDir.listFiles { f -> f.extension == "json" }?.map { it.name }?.toTypedArray()
                ?: arrayOf()
        if (files.isEmpty()) {
            Toast.makeText(this, "没有可加载的配置", Toast.LENGTH_SHORT).show()
            return
        }
        showFilePicker("选择配置文件", files) { name ->
            val json = openFileInput(name).bufferedReader()
                .use { it.readText() }
            val root = JSONObject(json)
            // Log Data
            Log.d("DartFlyTuner", root.toString(4))
            try {
                val data = root.getJSONObject("data")
            } catch (e: Exception) {
                Toast.makeText(this, "配置文件格式错误", Toast.LENGTH_SHORT).show()
                return@showFilePicker
            }
            parseJsonToWidget(root)
            Toast.makeText(this, "已加载 $name", Toast.LENGTH_SHORT).show()
            checkboxes.values.forEach { it.isChecked = true }
        }
    }

    // 保存当前配置
    @SuppressLint("SetTextI18n")
    private fun saveConfig() {
        // 生成 JSON 字符串，复用 generateQRCode 中组装 root 的逻辑
        val root = assembleConfigJson()
        // 弹出重命名对话框，以月日年时分秒默认命名
        // 获取年月日
        val calendar = java.util.Calendar.getInstance()

        val input = EditText(this).apply {
            setText(
                "config_${calendar.get(java.util.Calendar.YEAR)}_${
                    calendar.get(java.util.Calendar.MONTH) + 1
                }_${calendar.get(java.util.Calendar.DAY_OF_MONTH)}_${calendar.get(java.util.Calendar.HOUR_OF_DAY)}_${
                    calendar.get(
                        java.util.Calendar.MINUTE
                    )
                }.json"
            )
        }
        AlertDialog.Builder(this)
            .setTitle("保存为")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val name =
                    input.text.toString().let { if (it.endsWith(".json")) it else "$it.json" }
                openFileOutput(name, Context.MODE_PRIVATE).bufferedWriter()
                    .use { it.write(root.toString()) }
                Toast.makeText(this, "已保存 $name", Toast.LENGTH_SHORT)
                    .show()  // :contentReference[oaicite:6]{index=6}
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun assembleConfigJson(): JSONObject {
        val config = JSONObject()

        if (checkboxes["primary_yaw"]?.isChecked == true) {
            config.put("primary_yaw", editPrimaryYaw.text.toString().toIntOrNull() ?: 0)
        }
        if (checkboxes["primary_force"]?.isChecked == true) {
            config.put("primary_force", editPrimaryForce.text.toString().toIntOrNull() ?: 0)
        }
        if (checkboxes["primary_force_offset"]?.isChecked == true) {
            config.put(
                "primary_force_offset",
                editPrimaryForceOffset.text.toString().toIntOrNull() ?: 0
            )
        }
        if (checkboxes["auxiliary_yaw_offsets"]?.isChecked == true) {
            val offsets = editAuxiliaryYawOffsets.map { it.text.toString().toIntOrNull() ?: 0 }
            putCollectionIntoJSONObject(config, "auxiliary_yaw_offsets", offsets as Collection<*>)
        }
        if (checkboxes["auxiliary_force_offsets"]?.isChecked == true) {
            val offsets = editAuxiliaryForceOffsets.map { it.text.toString().toIntOrNull() ?: 0 }
            putCollectionIntoJSONObject(config, "auxiliary_force_offsets", offsets as Collection<*>)
        }
        if (checkboxes["dart_launch_process_offset_begin"]?.isChecked == true) {
            config.put(
                "dart_launch_process_offset_begin",
                editDartLaunchProcessOffsetBegin.text.toString().toIntOrNull() ?: 0
            )
        }
        if (checkboxes["dart_launch_process_offset_end"]?.isChecked == true) {
            config.put(
                "dart_launch_process_offset_end",
                editDartLaunchProcessOffsetEnd.text.toString().toIntOrNull() ?: 0
            )
        }
        if (checkboxes["target_auto_aim_x_axis"]?.isChecked == true) {
            config.put(
                "target_auto_aim_x_axis",
                editTargetAutoAimXAxis.text.toString().toDoubleOrNull() ?: 0.0
            )
        }

        val adaptiveVelocityConfig = JSONObject()

        if (checkboxes["adaptive_velocity_control"]?.isChecked == true) {
            val expectedVelocities = editAdaptiveVelocityExpected.map {
                it.text.toString()
            }
            putCollectionIntoJSONObject(
                adaptiveVelocityConfig, "adaptive_velocity_expected", expectedVelocities as Collection<*>
            )
        }

        adaptiveVelocityConfig.put("adaptive_velocity_enabled", switchAdaptiveVelocityEnabled.isChecked)

        config.put("auto_aim_enabled", switchAutoAimEnabled.isChecked)

        val commandType = when (toggleCommandType.checkedButtonId) {
            R.id.btn_params -> "DartParams"
            R.id.btn_protocols -> "DartProtocols"
            else -> "DartParams"
        }
        val root = JSONObject().apply {
            put("command_type", commandType)
            put("data", config)
            put("adaptive_velocity_control", adaptiveVelocityConfig)
        }
        val content = root.toString()
        return root
    }

    // 复制当前配置到剪贴板
    private fun copyConfig() {
        val root = assembleConfigJson().toString()
        val clipboard =
            getSystemService(CLIPBOARD_SERVICE) as ClipboardManager  // :contentReference[oaicite:7]{index=7}
        val clip = ClipData.newPlainText("config", root)
        clipboard.setPrimaryClip(clip)  // :contentReference[oaicite:8]{index=8}
        Toast.makeText(this, "配置已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    private fun deleteConfig() {
        val files =
            filesDir.listFiles { f -> f.extension == "json" }?.map { it.name }?.toTypedArray()
                ?: arrayOf()
        if (files.isEmpty()) {
            Toast.makeText(this, "没有可删除的配置", Toast.LENGTH_SHORT).show()
            return
        }
        val selected = BooleanArray(files.size)
        AlertDialog.Builder(this)
            .setTitle("删除配置")
            .setMultiChoiceItems(files, selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            .setPositiveButton("删除") { _, _ ->
                files.filterIndexed { index, _ -> selected[index] }.forEach { name ->
                    deleteFile(name)
                }
                Toast.makeText(this, "已删除选中配置", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // 全选或取消全选所有复选框
    private fun toggleAllCheckboxes() {
        val allChecked = checkboxes.values.all { it.isChecked }
        checkboxes.values.forEach { it.isChecked = !allChecked }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupListeners()
        updateQRCode() // 初始生成一次

        // 如果有保存的配置文件，自动加载
        val files =
            filesDir.listFiles { f -> f.extension == "json" }?.map { it.name }?.toTypedArray()
                ?: arrayOf()
        if (files.isNotEmpty()) {
            val json = openFileInput(files[0]).bufferedReader()
                .use { it.readText() }
            val root = JSONObject(json)
            // Log Data
            Log.d("DartFlyTuner", root.toString(4))
            try {
                val data = root.getJSONObject("data")
            } catch (e: Exception) {
                Toast.makeText(this, "配置文件格式错误", Toast.LENGTH_SHORT).show()
                return
            }
            parseJsonToWidget(root)
            checkboxes.values.forEach { it.isChecked = true }
            Toast.makeText(this, "已加载 ${files[0]}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindViews() {
        val topAppBar = findViewById<MaterialToolbar>(R.id.top_app_bar)
        setSupportActionBar(topAppBar)

        editPrimaryYaw = findViewById(R.id.edit_primary_yaw)
        editPrimaryForce = findViewById(R.id.edit_primary_force)
        editPrimaryForceOffset = findViewById(R.id.edit_primary_force_offset)

        editAuxiliaryYawOffsets = arrayOf(
            findViewById(R.id.edit_auxiliary_yaw_offset_0),
            findViewById(R.id.edit_auxiliary_yaw_offset_1),
            findViewById(R.id.edit_auxiliary_yaw_offset_2),
            findViewById(R.id.edit_auxiliary_yaw_offset_3)
        )
        editAuxiliaryForceOffsets = arrayOf(
            findViewById(R.id.edit_auxiliary_force_offset_0),
            findViewById(R.id.edit_auxiliary_force_offset_1),
            findViewById(R.id.edit_auxiliary_force_offset_2),
            findViewById(R.id.edit_auxiliary_force_offset_3)
        )

        editAdaptiveVelocityExpected = arrayOf(
            findViewById(R.id.edit_adaptive_velocity_expected_0),
            findViewById(R.id.edit_adaptive_velocity_expected_1),
            findViewById(R.id.edit_adaptive_velocity_expected_2),
            findViewById(R.id.edit_adaptive_velocity_expected_3)
        )

        editDartLaunchProcessOffsetBegin = findViewById(R.id.edit_dart_launch_process_offset_begin)
        editDartLaunchProcessOffsetEnd = findViewById(R.id.edit_dart_launch_process_offset_end)
        editTargetAutoAimXAxis = findViewById(R.id.edit_target_auto_aim_x_axis)
        switchAutoAimEnabled = findViewById(R.id.switch_auto_aim_enabled)
        switchAdaptiveVelocityEnabled = findViewById(R.id.switch_adaptive_velocity_control)
        ivQR = findViewById(R.id.iv_qr)
        toggleCommandType = findViewById(R.id.toggle_command_type)
        // 默认选中第一个按钮
        toggleCommandType.check(R.id.btn_params)

        checkboxes = mapOf(
            "primary_yaw" to findViewById(R.id.checkbox_primary_yaw),
            "primary_force" to findViewById(R.id.checkbox_primary_force),
            "primary_force_offset" to findViewById(R.id.checkbox_primary_force_offset),
            "auxiliary_yaw_offsets" to findViewById(R.id.checkbox_auxiliary_yaw_offsets),
            "auxiliary_force_offsets" to findViewById(R.id.checkbox_auxiliary_force_offsets),
            "dart_launch_process_offset_begin" to findViewById(R.id.checkbox_dart_launch_process_offset_begin),
            "dart_launch_process_offset_end" to findViewById(R.id.checkbox_dart_launch_process_offset_end),
            "target_auto_aim_x_axis" to findViewById(R.id.checkbox_target_auto_aim_x_axis),
            "adaptive_velocity_control" to findViewById(R.id.checkbox_adaptive_velocity_control)
        )

        btnSelectAll = findViewById(R.id.btn_select_all)
    }

    private fun setupListeners() {
        // 复选框 & 开关
        checkboxes.values.forEach { cb ->
            cb.setOnCheckedChangeListener { _, _ -> scheduleQRCodeUpdate() }
        }
        switchAutoAimEnabled.setOnCheckedChangeListener { _, _ -> scheduleQRCodeUpdate() }

        switchAdaptiveVelocityEnabled.setOnCheckedChangeListener { _, _ -> scheduleQRCodeUpdate() }

        // 文本输入防抖
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                scheduleQRCodeUpdate()
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        }
        listOf(
            editPrimaryYaw, editPrimaryForce, editPrimaryForceOffset,
            editDartLaunchProcessOffsetBegin, editDartLaunchProcessOffsetEnd,
            editTargetAutoAimXAxis
        ).forEach { it.addTextChangedListener(watcher) }
        editAuxiliaryYawOffsets.forEach { it.addTextChangedListener(watcher) }
        editAuxiliaryForceOffsets.forEach { it.addTextChangedListener(watcher) }
        editAdaptiveVelocityExpected.forEach { it.addTextChangedListener(watcher) }

        // 切换组
        toggleCommandType.addOnButtonCheckedListener { _, _, isChecked ->
            if (isChecked) scheduleQRCodeUpdate()
        }

        // 全选按钮
        btnSelectAll.setOnClickListener { toggleAllCheckboxes() }
    }

    private fun scheduleQRCodeUpdate() {
        qrRunnable?.let { qrHandler.removeCallbacks(it) }
        qrRunnable = Runnable { updateQRCode() }
        qrHandler.postDelayed(qrRunnable!!, DEBOUNCE_MS)
    }

    private fun updateQRCode() {
        val root = assembleConfigJson()
        val content = root.toString()
        try {
            val size = 400
            val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L)
            val bitMatrix = MultiFormatWriter().encode(
                content, BarcodeFormat.QR_CODE, size, size, hints
            )
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
            ivQR.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            Toast.makeText(this, "二维码生成失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Unit {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_SCAN_QR && resultCode == RESULT_OK) {
            val jsonString = data?.getStringExtra("SCANNED_JSON") ?: return
            try {
                val root = JSONObject(jsonString)
                parseJsonToWidget(root)
                Toast.makeText(this, "已导入扫码配置", Toast.LENGTH_SHORT).show()
            } catch (e: JSONException) {
                Toast.makeText(this, "扫码内容不是有效 JSON", Toast.LENGTH_LONG).show()
            }
        }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_load_config -> {
                loadConfig()
                true
            }

            R.id.action_save_config -> {
                saveConfig()
                true
            }

            R.id.action_import_config -> {
                // request camera permission if needed…
                startActivityForResult(
                    Intent(this, ScannerActivity::class.java),
                    REQUEST_SCAN_QR
                )
                true
            }

            R.id.action_copy_config -> {
                copyConfig()
                true
            }

            R.id.action_delete_config -> {
                deleteConfig()
                true
            }

            R.id.action_import_config_from_clipboard -> {
                AlertDialog.Builder(this)
                    .setTitle("从剪贴板导入")
                    .setMessage("是否从剪贴板导入配置？")
                    .setPositiveButton("确定") { _, _ ->
                        val clipboard =
                            getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val jsonString = clip.getItemAt(0).text.toString()
                            try {
                                val root = JSONObject(jsonString)
                                parseJsonToWidget(root)
                                Toast.makeText(this, "已导入剪贴板配置", Toast.LENGTH_SHORT).show()
                            } catch (e: JSONException) {
                                Toast.makeText(this, "剪贴板内容不是有效 JSON", Toast.LENGTH_LONG)
                                    .show()
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }

            R.id.action_about -> {
                AlertDialog.Builder(this)
                    .setTitle("关于DartFlyTuner")
                    .setMessage("作者：东南大学 3SE战队 程浩\nGitHub: https://github.com/ChenYuWuAi")
                    .setPositiveButton("好") { _, _ -> }
                    .show()
                true
            }

            R.id.action_export_config -> {
                // 将当前二维码保存为图片
                val bitmap = (ivQR.drawable as BitmapDrawable).bitmap
                val path =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)

                val file = File(path, "DartFlyTuner_QRCode${System.currentTimeMillis()}.png")
                try {
                    FileOutputStream(file).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    Toast.makeText(this, "二维码已保存到 $path", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    Toast.makeText(this, "保存二维码失败：${e.message}", Toast.LENGTH_LONG).show()
                }
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.top_app_bar_menu, menu)
        return true
    }

    private fun putCollectionIntoJSONObject(
        jsonObject: JSONObject,
        key: String,
        collection: Collection<*>
    ) {
        val jsonArray = JSONArray()
        for (item in collection) {
            jsonArray.put(item)
        }
        jsonObject.put(key, jsonArray)
    }

    private fun generateQRCode() {
        // 组装 JSON 字符串
        val root = assembleConfigJson()
        val content = root.toString()

        try {
            val size = 400  // 增大至 400×400
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L  // 降低纠错，减小数据量
            )
            val bitMatrix = MultiFormatWriter().encode(
                content,
                BarcodeFormat.QR_CODE, size, size, hints
            )
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) for (y in 0 until size)
                bitmap[x, y] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
            ivQR.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            // 捕获容量不足等异常，避免崩溃
            Toast.makeText(this, "二维码生成失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
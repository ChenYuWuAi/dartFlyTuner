package com.iiise.dartflytuner

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.roundToInt
import androidx.core.graphics.set
import androidx.core.graphics.createBitmap
import androidx.appcompat.widget.Toolbar

class MainActivity : AppCompatActivity() {
    private lateinit var editYaw: EditText
    private lateinit var editPitch: EditText
    private lateinit var editTrigger: EditText
    private lateinit var ivQR: ImageView
    private lateinit var btnSave: Button
    private lateinit var btnLoad: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化Toolbar
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // 添加菜单
        supportActionBar?.apply {
            setHomeAsUpIndicator(R.drawable.ic_home_black_24dp)
        }

        // 初始化控件
        editYaw = findViewById(R.id.edit_yaw)
        editPitch = findViewById(R.id.edit_pitch)
        editTrigger = findViewById(R.id.edit_trigger)
        ivQR = findViewById(R.id.iv_qr)
        btnSave = findViewById(R.id.btn_save)
        //remove btnLoad
        //btnLoad = findViewById(R.id.btn_load)

        // 绑定按钮点击事件
        btnSave.setOnClickListener { saveConfig() }
        //remove setOnClickListener of btnLoad

        // 实时监听参数变化，更新二维码
        listOf(editYaw, editPitch, editTrigger).forEach { editText ->
            editText.addTextChangedListener(object : TextWatcherAdapter() {
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    updateQRCode()
                }

                override fun afterTextChanged(s: Editable?) {
                }
            })
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.bottom_nav_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_load -> {
                loadConfig() // Call your loadConfig() function here
                return true
            }

            android.R.id.home -> {
                finish() // Call `finish()` to close the activity and return to the previous one.
                return true
            }

            else -> return super.onOptionsItemSelected(item)
        }
    }

    // 保存配置到文件
    private fun saveConfig() {
        val yaw = editYaw.text.toString()
        val pitch = editPitch.text.toString()
        val trigger = editTrigger.text.toString()

        // 创建JSON对象
        val config = JSONObject().apply {
            put("yaw", yaw)
            put("pitch", pitch)
            put("trigger", trigger)
        }

        // 保存到文件（内部存储）
        val filename = "config_${System.currentTimeMillis()}.json"
        val file = File(filesDir, filename)
        try {
            FileOutputStream(file).use { fos ->
                fos.write(config.toString().toByteArray())
                Toast.makeText(this, "保存成功：$filename", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    // 加载配置文件
    private fun loadConfig() {
        val dir = filesDir
        val files = dir.listFiles() ?: return
        if (files.isEmpty()) {
            Toast.makeText(this, "无配置文件", Toast.LENGTH_SHORT).show()
            return
        }

        // 选择最后一个文件示例（实际需要列表选择）
        val lastFile = files[files.size - 1]
        try {
            FileInputStream(lastFile).use { fis ->
                val data = ByteArray(lastFile.length().toInt())
                fis.read(data)
                val json = String(data)
                val config = JSONObject(json)

                // 更新EditText
                editYaw.setText(config.getString("yaw"))
                editPitch.setText(config.getString("pitch"))
                editTrigger.setText(config.getString("trigger"))
                Toast.makeText(this, "加载成功", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "加载失败", Toast.LENGTH_SHORT).show()
        }
    }

    // 实时生成二维码（基于当前参数）
    private fun updateQRCode() {
        val yaw = editYaw.text.toString()
        val pitch = editPitch.text.toString()
        val trigger = editTrigger.text.toString()

        // 创建JSON字符串
        val config = JSONObject().apply {
            put("yaw", yaw)
            put("pitch", pitch)
            put("trigger", trigger)
        }

        try {
            // 生成二维码
            val writer = MultiFormatWriter()
            val bitMatrix = writer.encode(
                config.toString(), // JSON内容
                BarcodeFormat.QR_CODE,
                200, 200, // 尺寸
                null
            )

            val bitmap = createBitmap(200, 200, Bitmap.Config.RGB_565)
            for (x in 0 until 200) {
                for (y in 0 until 200) {
                    bitmap[x, y] = if (bitMatrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                }
            }
            ivQR.setImageBitmap(bitmap)
        } catch (e: WriterException) {
            e.printStackTrace()
        }
    }
}

// TextWatcher适配器（简化代码）
abstract class TextWatcherAdapter : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    override fun afterTextChanged(s: Editable?) {}
}
package org.musicnfc.writer

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : Activity(), NfcAdapter.ReaderCallback {
    private val worker = Executors.newSingleThreadExecutor()
    private val pendingAction = AtomicInteger(ACTION_NONE)
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var platformPicker: Spinner
    private lateinit var linkInput: EditText
    private lateinit var parameterValue: TextView
    private lateinit var uriValue: TextView
    private lateinit var statusValue: TextView
    private lateinit var writeButton: Button
    private var generatedUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(244, 246, 250)
        window.navigationBarColor = Color.rgb(244, 246, 250)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        buildScreen()
        refreshNfcStatus()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(BG)
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(28))
        }
        scroll.addView(page)

        page.addView(text("musicNFCwriter", 22, NAVY, true), lp(-1, -2, 0, 0, 0, 14))
        val inputCard = card()
        page.addView(inputCard, lp(-1, -2, 0, 0, 0, 14))
        platformPicker = Spinner(this)
        val platformLabels = MusicLinks.Platform.entries.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, platformLabels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        platformPicker.adapter = adapter
        inputCard.addView(platformPicker, lp(-1, dp(50), 0, 2, 0, 12))
        linkInput = EditText(this).apply {
            textSize = 14f
            setTextColor(NAVY)
            setHintTextColor(Color.rgb(148, 157, 173))
            hint = "粘贴分享链接，例如 https://..."
            gravity = Gravity.TOP or Gravity.START
            minLines = 2
            maxLines = 4
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = rounded(Color.rgb(247, 248, 251), Color.rgb(226, 230, 238), dp(12))
        }
        inputCard.addView(linkInput, lp(-1, -2, 0, 2, 0, 14))
        inputCard.addView(button("解析链接并生成 URI", BLUE, Color.WHITE).apply {
            setOnClickListener { generateUri() }
        }, lp(-1, dp(50), 0, 0, 0, 0))

        val resultCard = card()
        page.addView(resultCard, lp(-1, -2, 0, 0, 0, 14))
        addSectionTitle(resultCard, "歌曲参数")
        parameterValue = text("等待解析", 14, MUTED, false)
        resultCard.addView(parameterValue, lp(-1, -2, 0, 8, 0, 14))
        addSectionTitle(resultCard, "生成的 NFC URI")
        uriValue = text("生成后会显示在这里", 13, NAVY, false).apply {
            setTextIsSelectable(true)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.rgb(247, 248, 251), Color.rgb(226, 230, 238), dp(12))
        }
        resultCard.addView(uriValue, lp(-1, -2, 0, 8, 0, 0))

        val nfcCard = card()
        page.addView(nfcCard, lp(-1, -2, 0, 0, 0, 14))
        addSectionTitle(nfcCard, "NFC 标签操作")
        statusValue = text("正在检查 NFC…", 14, MUTED, false).apply {
            setLineSpacing(dp(3).toFloat(), 1f)
        }
        nfcCard.addView(statusValue, lp(-1, -2, 0, 8, 0, 12))
        writeButton = button("写入歌曲 URI", NAVY, Color.WHITE).apply {
            isEnabled = false
            setOnClickListener { armAction(ACTION_WRITE) }
        }
        nfcCard.addView(writeButton, lp(-1, dp(50), 0, 0, 0, 10))
        nfcCard.addView(button("删除标签中的 NDEF 内容", Color.rgb(255, 239, 237), Color.rgb(177, 55, 48)).apply {
            setOnClickListener { armAction(ACTION_ERASE) }
        }, lp(-1, dp(48), 0, 0, 0, 8))
        nfcCard.addView(text(
            "删除只清除标签中的 NDEF 内容，不会更改卡片 UID 或扇区密钥。Mifare Classic / CUID 卡若认证或兼容性受限，手机可能无法写入或擦除。",
            12, MUTED, false
        ).apply { setLineSpacing(dp(3).toFloat(), 1f) }, lp(-1, -2, 0, 0, 0, 0))

        page.addView(text(
            "写入采用 NDEF URI 记录。自定义 Scheme 能否启动歌曲由对应音乐 App 和 Android 系统支持情况决定。",
            12, MUTED, false
        ).apply { setLineSpacing(dp(3).toFloat(), 1f) }, lp(-1, -2, 0, 2, 0, 0))

        setContentView(scroll)
    }

    private fun generateUri() {
        val source = MusicLinks.normalizeInput(linkInput.text.toString())
        if (source.isEmpty()) {
            linkInput.error = "请先粘贴歌曲分享链接"
            return
        }
        val platform = MusicLinks.Platform.entries[platformPicker.selectedItemPosition]
        generatedUri = null
        writeButton.isEnabled = false
        uriValue.text = "正在解析链接…"
        parameterValue.text = "正在提取平台歌曲参数…"

        worker.execute {
            try {
                var parameter = MusicLinks.extractParameter(platform, source)
                if (parameter == null) {
                    parameter = MusicLinks.extractParameter(platform, MusicLinks.resolveRedirect(source))
                }
                requireNotNull(parameter) {
                    "没有从链接或跳转地址中找到该平台要求的歌曲参数。请确认平台选择正确，并粘贴歌曲分享链接。"
                }
                val result = MusicLinks.buildUri(platform, parameter)
                runOnUiThread {
                    generatedUri = result
                    parameterValue.text = "${parameterName(platform)}：$parameter"
                    uriValue.text = result
                    writeButton.isEnabled = nfcAdapter?.isEnabled == true
                    setStatus("解析完成。检查 URI 后，点击“写入歌曲 URI”并贴近标签。", BLUE)
                }
            } catch (error: Exception) {
                val message = error.message ?: "链接解析失败，请检查网络或分享链接。"
                runOnUiThread {
                    uriValue.text = "未生成"
                    parameterValue.text = "没有提取到歌曲参数"
                    setStatus(message, Color.rgb(177, 55, 48))
                }
            }
        }
    }

    private fun parameterName(platform: MusicLinks.Platform): String = when (platform) {
        MusicLinks.Platform.QQ -> "songmid"
        MusicLinks.Platform.NETEASE -> "歌曲 ID"
        MusicLinks.Platform.KUGOU -> "chain"
        MusicLinks.Platform.QISHUI -> "track_id"
    }

    private fun refreshNfcStatus() {
        val adapter = nfcAdapter
        when {
            adapter == null -> setStatus("此设备没有 NFC 硬件，无法写入或擦除标签。", Color.rgb(177, 55, 48))
            !adapter.isEnabled -> setStatus("NFC 当前已关闭。请在系统设置中开启 NFC。", Color.rgb(177, 103, 24))
            else -> setStatus("NFC 已就绪。写入或删除时，按提示将标签贴近手机背面。", MUTED)
        }
    }

    private fun armAction(action: Int) {
        val adapter = nfcAdapter
        if (adapter == null || !adapter.isEnabled) {
            refreshNfcStatus()
            return
        }
        if (action == ACTION_WRITE && generatedUri.isNullOrEmpty()) {
            toast("请先解析歌曲链接并生成 URI")
            return
        }
        pendingAction.set(action)
        val instruction = if (action == ACTION_WRITE) {
            "请将 NFC 标签贴近手机背面，正在写入：\n$generatedUri"
        } else {
            "请将 NFC 标签贴近手机背面，准备清除标签的 NDEF 内容。"
        }
        setStatus(instruction, BLUE)
        try {
            adapter.enableReaderMode(
                this,
                this,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
                null
            )
        } catch (error: Exception) {
            pendingAction.set(ACTION_NONE)
            setStatus("无法启动 NFC 读取模式：${error.message}", Color.rgb(177, 55, 48))
        }
    }

    override fun onTagDiscovered(tag: Tag) {
        val action = pendingAction.getAndSet(ACTION_NONE)
        if (action == ACTION_NONE) return
        val uri = generatedUri
        runOnUiThread { setStatus("已识别标签，正在操作…", BLUE) }

        try {
            val message = if (action == ACTION_WRITE) uriMessage(requireNotNull(uri)) else emptyMessage()
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                try {
                    if (!ndef.isWritable) throw IOException("此标签为只读，不能写入或擦除。")
                    if (message.toByteArray().size > ndef.maxSize) {
                        throw IOException("生成的 URI 超出标签容量，请使用容量更大的标签。")
                    }
                    ndef.writeNdefMessage(message)
                } finally {
                    runCatching { ndef.close() }
                }
            } else {
                val formatable = NdefFormatable.get(tag)
                    ?: throw IOException("此标签不支持 NDEF 写入或格式化。")
                formatable.connect()
                try {
                    formatable.format(message)
                } finally {
                    runCatching { formatable.close() }
                }
            }
            val complete = if (action == ACTION_WRITE) {
                "写入成功。可以移开标签并重新扫描验证。"
            } else {
                "已清除标签中的 NDEF 内容。"
            }
            runOnUiThread { setStatus(complete, Color.rgb(35, 135, 89)) }
        } catch (error: Exception) {
            val details = error.message?.let { "标签操作失败：$it" } ?: "标签操作失败。"
            runOnUiThread { setStatus("$details 请保持标签贴稳后重试。", Color.rgb(177, 55, 48)) }
        } finally {
            runOnUiThread { runCatching { nfcAdapter?.disableReaderMode(this) } }
        }
    }

    private fun uriMessage(uri: String) = NdefMessage(arrayOf(NdefRecord.createUri(uri)))

    private fun emptyMessage(): NdefMessage {
        val empty = NdefRecord(NdefRecord.TNF_EMPTY, byteArrayOf(), byteArrayOf(), byteArrayOf())
        return NdefMessage(arrayOf(empty))
    }

    override fun onPause() {
        super.onPause()
        runCatching { nfcAdapter?.disableReaderMode(this) }
    }

    override fun onDestroy() {
        super.onDestroy()
        worker.shutdownNow()
    }

    private fun setStatus(message: String, color: Int) {
        if (::statusValue.isInitialized) {
            statusValue.text = message
            statusValue.setTextColor(color)
        }
    }

    private fun addSectionTitle(parent: LinearLayout, value: String) {
        parent.addView(text(value, 13, NAVY, true), lp(-1, -2, 0, 0, 0, 0))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(Color.WHITE, Color.rgb(235, 238, 244), dp(18))
    }

    private fun text(value: String, sp: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = sp.toFloat()
        setTextColor(color)
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun button(title: String, backgroundColor: Int, foreground: Int) = Button(this).apply {
        text = title
        textSize = 14f
        setTextColor(foreground)
        isAllCaps = false
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        background = rounded(backgroundColor, backgroundColor, dp(13))
        if (android.os.Build.VERSION.SDK_INT >= 21) stateListAnimator = null
    }

    private fun rounded(color: Int, stroke: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
        if (stroke != color) setStroke(dp(1), stroke)
    }

    private fun lp(width: Int, height: Int, left: Int, top: Int, right: Int, bottom: Int) =
        LinearLayout.LayoutParams(width, height).apply {
            setMargins(dp(left), dp(top), dp(right), dp(bottom))
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val ACTION_NONE = 0
        private const val ACTION_WRITE = 1
        private const val ACTION_ERASE = 2
        private val NAVY = Color.rgb(25, 37, 63)
        private val BLUE = Color.rgb(55, 103, 222)
        private val MUTED = Color.rgb(104, 115, 134)
        private val BG = Color.rgb(244, 246, 250)
    }
}

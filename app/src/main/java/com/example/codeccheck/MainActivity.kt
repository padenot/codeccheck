package com.example.codeccheck

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SearchView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Button
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Color
import android.media.MediaCodecInfo.CodecCapabilities
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.Spannable
import android.view.Menu
import android.view.MenuItem


class MainActivity : AppCompatActivity() {

    private lateinit var outputView: TextView
    private lateinit var searchView: SearchView
    var hwFilter: String = "ALL"    // "ALL", "HW", "SW"
    var typeFilter: String = "ALL"  // "ALL", "VIDEO", "AUDIO"
    var currentQuery: String = ""

    data class CodecBlock(
        val text: String,
        val codecName: String,
        val isHW: Boolean,
        val isAudio: Boolean
    )

    private lateinit var codecBlocks: List<CodecBlock>

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)

        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                currentQuery = newText.orEmpty()
                updateOutput()
                return true
            }
        })

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_hw_filter -> {
                hwFilter = when (hwFilter) {
                    "ALL" -> "HW"
                    "HW" -> "SW"
                    else -> "ALL"
                }
                item.title = "HW/SW: $hwFilter"
            }
            R.id.action_type_filter -> {
                typeFilter = when (typeFilter) {
                    "ALL" -> "VIDEO"
                    "VIDEO" -> "AUDIO"
                    else -> "ALL"
                }
                item.title = "Type: $typeFilter"
            }
            R.id.action_copy -> {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val header = "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                        "Date: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n\n"
                val textToCopy = header + outputView.text.toString()
                val clip = ClipData.newPlainText("Codec Info", textToCopy)
                clipboard.setPrimaryClip(clip)
            }
        }
        updateOutput()
        return true
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        searchView = SearchView(this)
        outputView = TextView(this).apply {
            setPadding(16, 16, 16, 16)
            isFocusable = true
            isFocusableInTouchMode = true
        }


        val scrollView = ScrollView(this).apply {
            addView(outputView)
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scrollView)
        }

        setContentView(layout)

        codecBlocks = buildCodecBlocks()
        updateOutput()
    }

    private fun getConstantMap(clazz: Class<*>, vararg prefixes: String): Map<Int, String> {
        return clazz.fields
            .filter { f -> prefixes.any { f.name.startsWith(it) } && f.type == Int::class.java }
            .associateBy(
                keySelector = { it.getInt(null) },
                valueTransform = { it.name }
            )
    }

    private fun getProfilePrefix(mime: String): String = when {
        mime.contains("avc", ignoreCase = true) -> "AVCProfile"
        mime.contains("hevc", ignoreCase = true) -> "HEVCProfile"
        mime.contains("av01", ignoreCase = true) -> "AV1Profile"
        mime.contains("vp9", ignoreCase = true) -> "VP9Profile"
        mime.contains("vp8", ignoreCase = true) -> "VP8Profile"
        else -> ""
    }

    private fun getLevelPrefix(mime: String): String = when {
        mime.contains("avc", ignoreCase = true) -> "AVCLevel"
        mime.contains("hevc", ignoreCase = true) -> "HEVC"
        mime.contains("av01", ignoreCase = true) -> "AV1Level"
        mime.contains("vp9", ignoreCase = true) -> "VP9Level"
        mime.contains("vp8", ignoreCase = true) -> "VP8Level"
        else -> ""
    }

    private fun getNamedConstantMap(clazz: Class<*>, prefix: String): Map<Int, String> {
        return clazz.fields
            .filter { it.name.startsWith(prefix) && it.type == Int::class.java }
            .associateBy(
                keySelector = { it.getInt(null) },
                valueTransform = { it.name }
            )
    }

    private fun isHardwareAccelerated(codecName: String): Boolean {
        val lower = codecName.lowercase()
        return listOf(
            "qcom",
            "mtk",
            "exynos",
            "intel",
            "nvidia",
            "arm",
            "c2.",
            "omx."
        ).any { it in lower } &&
                !lower.contains("google") && !lower.contains("android")
    }

    private fun buildCodecBlocks(): List<CodecBlock> {
        val blocks = mutableListOf<CodecBlock>()
        val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
        val codecs = codecList.codecInfos

        val colorFormatMap = getConstantMap(MediaCodecInfo.CodecCapabilities::class.java, "COLOR_")

        for (codec in codecs) {
            val builder = StringBuilder()
            val isHW = isHardwareAccelerated(codec.name)
            val isEncoder = codec.isEncoder
            var isAudio = false

            builder.appendLine(codec.name)
            if (codec.supportedTypes[0].startsWith("audio/", ignoreCase = true)) isAudio = true
            builder.appendLine("  ${if (isHW) "Hardware" else "Software"} ${if (isAudio) "audio" else "video"} ${if (isEncoder) "encoder" else "decoder"}")

            for (type in codec.supportedTypes) {
                builder.appendLine("  MIME type: $type")

                if (!isAudio) {
                    val caps = codec.getCapabilitiesForType(type)
                    builder.appendLine("    Color formats:")
                    for (cf in caps.colorFormats) {
                        val name = colorFormatMap[cf] ?: "Unknown"
                        builder.appendLine("      0x${Integer.toHexString(cf)} ($name)")
                    }

                    val profilePrefix = getProfilePrefix(type)
                    val levelPrefix = getLevelPrefix(type)

                    if (profilePrefix.isEmpty()) {
                        continue
                    }

                    val profileMap =
                        getNamedConstantMap(
                            MediaCodecInfo.CodecProfileLevel::class.java,
                            profilePrefix
                        )
                    val levelMap =
                        getNamedConstantMap(
                            MediaCodecInfo.CodecProfileLevel::class.java,
                            levelPrefix
                        )

                    builder.appendLine("    Profile levels:")
                    for (pl in caps.profileLevels) {
                        val profileName = profileMap[pl.profile] ?: "Unknown"
                        val levelName = levelMap[pl.level] ?: "Unknown"
                        builder.appendLine("      Profile: $profileName  Level: $levelName")
                    }
                }
                blocks.add(CodecBlock(builder.toString(), codec.name, isHW, isAudio))
            }
        }
        return blocks
    }

    fun updateOutput() {
         val codecs = codecBlocks.filter { block : CodecBlock ->
            val matchQuery = block.text.contains(currentQuery, ignoreCase = true)
            val matchHW =
                hwFilter == "ALL" || (hwFilter == "HW" && block.isHW) || (hwFilter == "SW" && !block.isHW)
            val matchType = typeFilter == "ALL" ||
                    (typeFilter == "AUDIO" && block.isAudio) ||
                    (typeFilter == "VIDEO" && !block.isAudio)
            matchQuery && matchHW && matchType
        }


        val builder = SpannableStringBuilder()
        for (block in codecs) {
            val start = builder.length
            builder.append(block.text)
            builder.appendLine()
        }

        outputView.text = builder
    }
}

package com.georgewitte.yep

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.georgewitte.yep.data.CheckStore
import com.georgewitte.yep.logic.Check
import com.georgewitte.yep.logic.Checks
import com.georgewitte.yep.ui.HoldToConfirmCard
import com.georgewitte.yep.ui.dp
import com.georgewitte.yep.ui.roundedRect
import com.georgewitte.yep.ui.setUpEdgeToEdge
import java.util.Date

class MainActivity : Activity() {

    private lateinit var store: CheckStore
    private lateinit var listView: LinearLayout
    private lateinit var summaryView: TextView
    private lateinit var banner: View
    private lateinit var bannerText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var checks: List<Check> = emptyList()

    /** The most recent confirmation, kept so the banner's Undo can remove it. */
    private var undoable: Pair<String, Long>? = null

    private val ticker = object : Runnable {
        override fun run() {
            refreshStatuses()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    private val hideBanner = Runnable {
        banner.visibility = View.GONE
        undoable = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setUpEdgeToEdge(findViewById(R.id.root))

        store = CheckStore(this)
        listView = findViewById(R.id.check_list)
        summaryView = findViewById(R.id.summary)
        banner = findViewById(R.id.banner)
        bannerText = findViewById(R.id.banner_text)

        findViewById<View>(R.id.banner_undo).setOnClickListener { undoLastConfirmation() }
        findViewById<View>(R.id.add_check).setOnClickListener { showAddDialog() }
    }

    override fun onResume() {
        super.onResume()
        checks = store.load()
        rebuildList()
        handler.postDelayed(ticker, REFRESH_MS)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun rebuildList() {
        listView.removeAllViews()
        val now = System.currentTimeMillis()
        for (check in checks) {
            val card = HoldToConfirmCard(this)
            card.bind(check, now)
            card.onConfirmed = { confirm(check.id) }
            card.setOnClickListener { openHistory(check.id) }
            listView.addView(
                card,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { bottomMargin = dp(12) },
            )
        }
        updateSummary(now)
    }

    /** Re-renders "x min ago" text without rebuilding views (so an in-progress hold isn't interrupted). */
    private fun refreshStatuses() {
        val now = System.currentTimeMillis()
        checks.forEachIndexed { index, check ->
            (listView.getChildAt(index) as? HoldToConfirmCard)?.bind(check, now)
        }
        updateSummary(now)
    }

    private fun updateSummary(now: Long) {
        summaryView.text = when {
            checks.isEmpty() -> getString(R.string.summary_empty)
            else -> {
                val fresh = Checks.freshCount(checks, now)
                if (fresh == 0) getString(R.string.summary_none) else getString(R.string.summary_some, fresh, checks.size)
            }
        }
    }

    private fun confirm(id: String) {
        val now = System.currentTimeMillis()
        checks = Checks.confirm(checks, id, now)
        store.save(checks)
        refreshStatuses()

        val check = checks.firstOrNull { it.id == id } ?: return
        undoable = id to now
        val time = DateFormat.getTimeFormat(this).format(Date(now))
        bannerText.text = getString(R.string.banner_confirmed, check.emoji, check.name, time)
        banner.visibility = View.VISIBLE // banner_text is a polite live region, so TalkBack reads it out.
        handler.removeCallbacks(hideBanner)
        handler.postDelayed(hideBanner, BANNER_MS)
    }

    private fun undoLastConfirmation() {
        val (id, at) = undoable ?: return
        checks = Checks.removeConfirmation(checks, id, at)
        store.save(checks)
        refreshStatuses()
        handler.removeCallbacks(hideBanner)
        hideBanner.run()
    }

    private fun openHistory(id: String) {
        startActivity(Intent(this, HistoryActivity::class.java).putExtra(HistoryActivity.EXTRA_CHECK_ID, id))
    }

    private fun showAddDialog() {
        val padding = dp(22)
        val input = EditText(this).apply {
            hint = getString(R.string.add_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(Checks.MAX_NAME_LENGTH))
            isSingleLine = true
        }

        var chosenEmoji = Checks.EMOJI_CHOICES.first()
        val emojiRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val emojiViews = Checks.EMOJI_CHOICES.map { emoji ->
            TextView(this).apply {
                text = emoji
                textSize = 26f
                gravity = Gravity.CENTER
                contentDescription = emoji
                val size = dp(52)
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(6) }
            }
        }
        fun highlight() = emojiViews.forEach { view ->
            view.background = if (view.text == chosenEmoji) {
                roundedRect(getColor(R.color.hold_fill), dp(14).toFloat(), getColor(R.color.brand), dp(2))
            } else {
                null
            }
        }
        emojiViews.forEach { view ->
            view.setOnClickListener {
                chosenEmoji = view.text.toString()
                highlight()
            }
            emojiRow.addView(view)
        }
        highlight()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, dp(8), padding, 0)
            addView(input)
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.add_pick_icon)
                setPadding(0, dp(16), 0, dp(8))
            })
            addView(HorizontalScrollView(this@MainActivity).apply {
                isHorizontalScrollBarEnabled = false
                addView(emojiRow)
            })
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.add_title)
            .setView(content)
            .setPositiveButton(R.string.add) { _, _ ->
                checks = Checks.add(checks, input.text.toString(), chosenEmoji)
                store.save(checks)
                rebuildList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dialog.show()

        val addButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        addButton.isEnabled = false
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                addButton.isEnabled = Checks.cleanName(s?.toString().orEmpty()).isNotEmpty()
            }
        })
        input.requestFocus()
    }

    private companion object {
        const val REFRESH_MS = 30_000L
        const val BANNER_MS = 6_000L
    }
}

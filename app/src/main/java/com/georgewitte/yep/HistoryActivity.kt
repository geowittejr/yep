package com.georgewitte.yep

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.format.DateFormat
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.georgewitte.yep.data.CheckStore
import com.georgewitte.yep.logic.Check
import com.georgewitte.yep.logic.Checks
import com.georgewitte.yep.logic.TimeText
import com.georgewitte.yep.ui.dp
import com.georgewitte.yep.ui.setUpEdgeToEdge
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** Shows every recorded confirmation for one check, plus rename / clear / delete. */
class HistoryActivity : Activity() {

    private lateinit var store: CheckStore
    private lateinit var checkId: String
    private var checks: List<Check> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        setUpEdgeToEdge(findViewById(R.id.root))
        store = CheckStore(this)
        checkId = intent.getStringExtra(EXTRA_CHECK_ID) ?: run { finish(); return }

        findViewById<View>(R.id.back).setOnClickListener { finish() }
        findViewById<View>(R.id.rename).setOnClickListener { showRenameDialog() }
        findViewById<View>(R.id.clear_history).setOnClickListener { confirmClearHistory() }
        findViewById<View>(R.id.delete_check).setOnClickListener { confirmDelete() }
    }

    override fun onResume() {
        super.onResume()
        checks = store.load()
        render()
    }

    private fun currentCheck(): Check? = checks.firstOrNull { it.id == checkId }

    private fun render() {
        val check = currentCheck() ?: run { finish(); return }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val locale = Locale.getDefault()
        val timeFormat = DateFormat.getTimeFormat(this)
        val formatTime = { millis: Long -> timeFormat.format(Date(millis)) }

        findViewById<TextView>(R.id.emoji).text = check.emoji
        findViewById<TextView>(R.id.name).text = check.name
        findViewById<TextView>(R.id.subtitle).text = when (val last = check.lastConfirmed) {
            null -> getString(R.string.history_never)
            else -> resources.getQuantityString(
                R.plurals.history_count, check.history.size, check.history.size,
                TimeText.moment(now, last, zone, locale, formatTime),
            )
        }

        val list = findViewById<LinearLayout>(R.id.history_list)
        list.removeAllViews()
        findViewById<View>(R.id.history_empty).visibility = if (check.history.isEmpty()) View.VISIBLE else View.GONE
        for (at in check.history) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(14), 0, dp(14))
            }
            row.addView(
                TextView(this).apply {
                    text = TimeText.moment(now, at, zone, locale, formatTime)
                    textSize = 16f
                    setTextColor(getColor(R.color.text))
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            row.addView(TextView(this).apply {
                text = TimeText.ago(now, at)
                textSize = 14f
                setTextColor(getColor(R.color.text_muted))
            })
            list.addView(row)
            list.addView(View(this).apply { setBackgroundColor(getColor(R.color.outline)) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
        }
    }

    private fun save(updated: List<Check>) {
        checks = updated
        store.save(updated)
        render()
    }

    private fun showRenameDialog() {
        val check = currentCheck() ?: return
        val input = EditText(this).apply {
            setText(check.name)
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(Checks.MAX_NAME_LENGTH))
            isSingleLine = true
        }
        val container = LinearLayout(this).apply {
            setPadding(dp(22), dp(8), dp(22), 0)
            addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.rename)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ -> save(Checks.rename(checks, checkId, input.text.toString())) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmClearHistory() {
        AlertDialog.Builder(this)
            .setTitle(R.string.clear_history_title)
            .setMessage(R.string.clear_history_message)
            .setPositiveButton(R.string.clear) { _, _ -> save(Checks.clearHistory(checks, checkId)) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete() {
        val check = currentCheck() ?: return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_title, check.name))
            .setMessage(R.string.delete_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                store.save(Checks.delete(checks, checkId))
                finish()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val EXTRA_CHECK_ID = "check_id"
    }
}

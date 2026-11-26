package com.supernova.launcher.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.supernova.launcher.R
import com.supernova.launcher.data.LauncherItem
import com.supernova.launcher.data.WidgetStyle

object WidgetStyleSheet {
    fun show(context: Context, viewModel: LauncherViewModel, item: LauncherItem) {
        val prefs = viewModel.state.value.prefs
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(PAD_H, PAD_V, PAD_H, PAD_V)
        }
        val corner = slider(
            context, content, R.string.widget_corner_radius, MAX_CORNER_RADIUS,
            item.widgetCornerRadius ?: prefs.widgetCornerRadius
        )
        val borderWidth = slider(
            context, content, R.string.widget_border_width, MAX_BORDER_WIDTH,
            item.widgetBorderWidth ?: prefs.widgetBorderWidth
        )
        val borderColor = edit(
            context, content, R.string.widget_border_color,
            item.widgetBorderColor.orEmpty().ifBlank { prefs.widgetBorderColor }
        )
        val shadow = slider(
            context, content, R.string.widget_shadow, MAX_SHADOW,
            item.widgetShadow ?: prefs.widgetShadow
        )
        val shadowColor = edit(
            context, content, R.string.widget_shadow_color,
            item.widgetShadowColor.orEmpty().ifBlank { prefs.widgetShadowColor }
        )
        val scroll = ScrollView(context).apply { addView(content) }
        val title = context.getString(
            R.string.widget_appearance,
            item.label.ifBlank { context.getString(R.string.widget) }
        )
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(R.string.save) { _, _ ->
                viewModel.setWidgetStyle(
                    item.id,
                    WidgetStyle(
                        corner.progress,
                        borderWidth.progress,
                        borderColor.text.toString().trim().takeIf { it.isNotBlank() },
                        shadow.progress,
                        shadowColor.text.toString().trim().takeIf { it.isNotBlank() }
                    )
                )
            }
            .setNeutralButton(R.string.widget_reset_appearance) { _, _ ->
                viewModel.setWidgetStyle(item.id, WidgetStyle())
            }
            .setNegativeButton(R.string.cancel, null)
            .showTracked()
    }

    private fun slider(context: Context, parent: ViewGroup, title: Int, max: Int, value: Int): SeekBar {
        val titleView = TextView(context).apply {
            text = context.getString(title)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.START
        }
        val valueView = TextView(context).apply {
            text = value.coerceIn(0, max).toString()
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(valueView)
        }
        val seek = SeekBar(context).apply {
            this.max = max
            progress = value.coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    valueView.text = progress.toString()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }
        parent.addView(header)
        parent.addView(seek)
        return seek
    }

    private fun edit(context: Context, parent: ViewGroup, title: Int, value: String): EditText {
        val titleView = TextView(context).apply {
            text = context.getString(title)
            typeface = Typeface.DEFAULT_BOLD
        }
        val edit = EditText(context).apply {
            setText(value)
            isSingleLine = true
        }
        parent.addView(titleView)
        parent.addView(edit)
        return edit
    }

    private const val PAD_H = 24
    private const val PAD_V = 16
    private const val MAX_CORNER_RADIUS = 32
    private const val MAX_BORDER_WIDTH = 16
    private const val MAX_SHADOW = 32
}

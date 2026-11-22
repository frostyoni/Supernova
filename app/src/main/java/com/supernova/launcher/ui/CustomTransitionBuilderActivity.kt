package com.supernova.launcher.ui

import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.supernova.launcher.R
import com.supernova.launcher.data.ChipEdit
import com.supernova.launcher.data.CustomTransition
import com.supernova.launcher.data.LauncherPreferences
import com.supernova.launcher.data.Pref
import com.supernova.launcher.ui.settings.preview.MotionPreviewParts
import com.supernova.launcher.ui.settings.preview.PageEffectPreviewView
import kotlinx.coroutines.launch

// The Labs transition builder: two lanes of chips over the swipe's 0..1 and
// a live mini-home preview that scrubs the same spec. The JSON is still the
// storage and the share format; this only edits it.
class CustomTransitionBuilderActivity : AppCompatActivity() {

    private lateinit var preview: PageEffectPreviewView
    private lateinit var timeline: TransitionTimelineView
    private lateinit var channel: AppCompatSpinner
    private lateinit var ease: AppCompatSpinner
    private lateinit var fromField: AppCompatEditText
    private lateinit var valueField: AppCompatEditText
    private lateinit var addBox: CheckBox
    private lateinit var atTo: TextView
    private lateinit var inspector: LinearLayout

    private var spec: CustomTransition = CustomTransition()
    private var populating = false

    private val channels = CustomTransition.Channel.entries
    private val eases = CustomTransition.Ease.entries

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        spec = CustomTransitionStore.load(this) ?: CustomTransition.example
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val surface = ContextCompat.getColor(this, R.color.surface)
        window.setBackgroundDrawable(ColorDrawable(surface))
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(surface)
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val base = arrayOf(root.paddingLeft, root.paddingTop, root.paddingRight, root.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(base[0] + bars.left, base[1] + bars.top, base[2] + bars.right, base[3] + bars.bottom)
            insets
        }
        setContentView(root)

        root.addView(buildTopBar(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        root.addView(buildPreview(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210)))
        timeline = TransitionTimelineView(this).apply {
            setPadding(0, dp(8), 0, dp(8))
            onChanged = { syncFromTimeline() }
            onSelected = { populateInspector() }
        }
        root.addView(timeline, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        inspector = buildInspector()
        root.addView(inspector, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        refresh(play = true)
    }

    private fun buildTopBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        bar.addView(TextView(this).apply {
            text = getString(R.string.custom_transition_builder)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(MaterialButton(this).apply {
            text = getString(R.string.save)
            setOnClickListener { save() }
        })
        bar.addView(MaterialButton(this).apply {
            text = "⋮"
            setOnClickListener { showOverflow(this) }
        })
        return bar
    }

    private fun showOverflow(anchor: View) {
        val menu = PopupMenu(this, anchor)
        val shareItem = menu.menu.add(R.string.labs_custom_transition_share)
        val importItem = menu.menu.add(R.string.custom_transition_builder_import)
        val exampleItem = menu.menu.add(R.string.custom_transition_builder_example)
        val resetItem = menu.menu.add(R.string.custom_transition_builder_reset)
        menu.setOnMenuItemClickListener { item ->
            when (item) {
                shareItem -> share()
                importItem -> importDialog()
                exampleItem -> loadExample()
                resetItem -> resetSpec()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
        menu.show()
    }

    private fun buildPreview(): View {
        val frame = FrameLayout(this).apply {
            background = MotionPreviewParts.wallpaperBackground(this@CustomTransitionBuilderActivity, dp(14).toFloat())
            clipToOutline = true
        }
        val icons = List(12) { index ->
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(iconColour(index))
            }
        }
        preview = PageEffectPreviewView(this, icons, 4)
        frame.addView(preview, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        return frame
    }

    private fun buildInspector(): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        atTo = TextView(this).apply {
            setPadding(0, dp(4), 0, dp(8))
            text = getString(R.string.custom_transition_builder_no_chip)
        }
        column.addView(atTo)

        channel = spinner(channels.map { channelName(it) })
        ease = spinner(eases.map { easeName(it) })
        column.addView(halfRow(listOf(channel, ease)))

        fromField = numberField(getString(R.string.custom_transition_builder_from))
        valueField = numberField(getString(R.string.custom_transition_builder_value))
        addBox = CheckBox(this).apply { text = getString(R.string.custom_transition_builder_add) }
        column.addView(halfRow(listOf(fromField, valueField, addBox)))

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        buttons.addView(button(getString(R.string.custom_transition_builder_add_chip)) { addChip() })
        buttons.addView(button(getString(R.string.custom_transition_builder_duplicate)) { duplicate() })
        buttons.addView(button(getString(R.string.custom_transition_builder_delete)) { delete() })
        column.addView(buttons, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        channel.onItemSelectedListener = listener { if (!populating) updateSelected { it.copy(channel = channels[channel.selectedItemPosition]) } }
        ease.onItemSelectedListener = listener { if (!populating) updateSelected { it.copy(ease = eases[ease.selectedItemPosition]) } }
        fromField.doAfterTextChanged { if (!populating) fromField.text?.toString()?.toFloatOrNull()?.let { value -> updateSelected { it.copy(from = value) } } }
        valueField.doAfterTextChanged { if (!populating) valueField.text?.toString()?.toFloatOrNull()?.let { value -> updateSelected { it.copy(value = value) } } }
        addBox.setOnCheckedChangeListener { _, checked -> if (!populating) updateSelected { it.copy(add = checked) } }
        return column
    }

    private fun spinner(labels: List<String>): AppCompatSpinner = AppCompatSpinner(this).apply {
        adapter = ArrayAdapter(
            this@CustomTransitionBuilderActivity,
            android.R.layout.simple_spinner_item,
            labels
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun listener(onSelected: () -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = onSelected()
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }

    private fun numberField(hint: String): AppCompatEditText = AppCompatEditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
            InputType.TYPE_NUMBER_FLAG_SIGNED
        imeOptions = EditorInfo.IME_ACTION_DONE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    }

    private fun button(text: String, onClick: () -> Unit): MaterialButton = MaterialButton(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun halfRow(views: List<View>): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun syncFromTimeline() {
        spec = timeline.transition
        atTo.text = selectionLabel()
        preview.show(ScrollEffect.CUSTOM, spec, replay = false)
    }

    private fun refresh(play: Boolean) {
        timeline.transition = spec
        preview.show(ScrollEffect.CUSTOM, spec, replay = play)
        populateInspector()
    }

    private fun populateInspector() {
        val selected = timeline.selected
        val chip = selected?.let { selection ->
            (if (selection.incoming) spec.incoming else spec.outgoing).getOrNull(selection.index)
        }
        val enabled = chip != null
        channel.isEnabled = enabled
        ease.isEnabled = enabled
        fromField.isEnabled = enabled
        valueField.isEnabled = enabled
        addBox.isEnabled = enabled
        populating = true
        if (chip != null) {
            channel.setSelection(channels.indexOf(chip.channel))
            ease.setSelection(eases.indexOf(chip.ease))
            fromField.setText(chip.from.toString())
            valueField.setText(chip.value.toString())
            addBox.isChecked = chip.add
        } else {
            fromField.setText("")
            valueField.setText("")
            addBox.isChecked = false
        }
        populating = false
        atTo.text = selectionLabel()
    }

    private fun selectionLabel(): String {
        val selected = timeline.selected ?: return getString(R.string.custom_transition_builder_no_chip)
        val chip = (if (selected.incoming) spec.incoming else spec.outgoing).getOrNull(selected.index)
            ?: return getString(R.string.custom_transition_builder_no_chip)
        return getString(
            R.string.custom_transition_builder_span,
            if (selected.incoming) getString(R.string.custom_transition_builder_incoming)
            else getString(R.string.custom_transition_builder_outgoing),
            chip.at,
            chip.to
        )
    }

    private fun updateSelected(change: (CustomTransition.Chip) -> CustomTransition.Chip) {
        val selected = timeline.selected ?: return
        val chips = (if (selected.incoming) spec.incoming else spec.outgoing).toMutableList()
        if (selected.index !in chips.indices) return
        chips[selected.index] = change(chips[selected.index])
        spec = if (selected.incoming) spec.copy(incoming = chips) else spec.copy(outgoing = chips)
        timeline.transition = spec
        atTo.text = selectionLabel()
        preview.show(ScrollEffect.CUSTOM, spec, replay = false)
    }

    private fun addChip() {
        val incoming = timeline.selected?.incoming ?: true
        val chip = CustomTransition.Chip(
            at = 0f,
            to = 0.2f,
            channel = channels.getOrNull(channel.selectedItemPosition) ?: CustomTransition.Channel.MOVE_X,
            from = 0f,
            value = 0f
        )
        val chips = if (incoming) spec.incoming + chip else spec.outgoing + chip
        spec = if (incoming) spec.copy(incoming = chips) else spec.copy(outgoing = chips)
        timeline.transition = spec
        timeline.selected = TransitionTimelineView.Selection(incoming, chips.size - 1)
        refresh(play = false)
    }

    private fun duplicate() {
        val selected = timeline.selected ?: return
        val chips = (if (selected.incoming) spec.incoming else spec.outgoing).toMutableList()
        val chip = chips.getOrNull(selected.index) ?: return
        val copy = ChipEdit.move(chip, chip.at + 0.05f)
        chips += copy
        spec = if (selected.incoming) spec.copy(incoming = chips) else spec.copy(outgoing = chips)
        timeline.transition = spec
        timeline.selected = TransitionTimelineView.Selection(selected.incoming, chips.size - 1)
        refresh(play = false)
    }

    private fun delete() {
        val selected = timeline.selected ?: return
        val chips = (if (selected.incoming) spec.incoming else spec.outgoing).toMutableList()
        if (selected.index !in chips.indices) return
        chips.removeAt(selected.index)
        spec = if (selected.incoming) spec.copy(incoming = chips) else spec.copy(outgoing = chips)
        timeline.selected = null
        refresh(play = false)
    }

    private fun loadExample() {
        spec = CustomTransition.example
        timeline.selected = null
        refresh(play = true)
    }

    private fun resetSpec() {
        spec = CustomTransition()
        timeline.selected = null
        refresh(play = true)
    }

    private fun importDialog() {
        val edit = AppCompatEditText(this).apply {
            setText(CustomTransitionStore.raw(this@CustomTransitionBuilderActivity) ?: CustomTransition.encode(spec))
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(16), dp(8), dp(16), dp(8))
            minLines = 8
            maxLines = 16
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.custom_transition_builder_import)
            .setView(edit)
            .setPositiveButton(R.string.custom_transition_builder_load) { _, _ ->
                val parsed = CustomTransition.parse(edit.text?.toString().orEmpty())
                if (parsed == null) {
                    Toast.makeText(this, R.string.labs_custom_transition_invalid, Toast.LENGTH_LONG).show()
                } else {
                    spec = parsed
                    timeline.selected = null
                    refresh(play = true)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun save() {
        if (spec.validated() == null) {
            Toast.makeText(this, R.string.labs_custom_transition_invalid, Toast.LENGTH_LONG).show()
            return
        }
        if (!CustomTransitionStore.save(this, CustomTransition.encode(spec))) {
            Toast.makeText(this, R.string.labs_custom_transition_invalid, Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            LauncherPreferences(applicationContext).set(Pref.scrollEffect, ScrollEffect.CUSTOM)
        }
        Toast.makeText(this, R.string.custom_transition_builder_saved, Toast.LENGTH_SHORT).show()
    }

    private fun share() {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, CustomTransition.encode(spec))
        startActivity(Intent.createChooser(send, getString(R.string.labs_custom_transition_share)))
    }

    private fun channelName(channel: CustomTransition.Channel): String = when (channel) {
        CustomTransition.Channel.MOVE_X -> getString(R.string.custom_transition_channel_x)
        CustomTransition.Channel.MOVE_Y -> getString(R.string.custom_transition_channel_y)
        CustomTransition.Channel.SCALE -> getString(R.string.custom_transition_channel_scale)
        CustomTransition.Channel.ALPHA -> getString(R.string.custom_transition_channel_fade)
        CustomTransition.Channel.ROLL -> getString(R.string.custom_transition_channel_roll)
        CustomTransition.Channel.PITCH -> getString(R.string.custom_transition_channel_pitch)
        CustomTransition.Channel.YAW -> getString(R.string.custom_transition_channel_yaw)
    }

    private fun easeName(ease: CustomTransition.Ease): String = when (ease) {
        CustomTransition.Ease.LINEAR -> getString(R.string.custom_transition_ease_linear)
        CustomTransition.Ease.EASE_IN -> getString(R.string.custom_transition_ease_in)
        CustomTransition.Ease.EASE_OUT -> getString(R.string.custom_transition_ease_out)
        CustomTransition.Ease.EASE_IN_OUT -> getString(R.string.custom_transition_ease_in_out)
        CustomTransition.Ease.OVERSHOOT -> getString(R.string.custom_transition_ease_overshoot)
    }

    private fun iconColour(index: Int): Int = ICON_COLOURS[index % ICON_COLOURS.size]

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        val ICON_COLOURS = intArrayOf(
            0xFF3F72AF.toInt(), 0xFF2E7D5B.toInt(), 0xFFA34E44.toInt(),
            0xFF7B4FA0.toInt(), 0xFFB8860B.toInt(), 0xFF4A6FA5.toInt()
        )
    }
}

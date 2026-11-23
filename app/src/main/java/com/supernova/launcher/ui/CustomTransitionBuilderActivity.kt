package com.supernova.launcher.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.supernova.launcher.R
import com.supernova.launcher.data.ChipEdit
import com.supernova.launcher.data.CustomTransition
import com.supernova.launcher.data.LauncherPreferences
import com.supernova.launcher.data.Pref
import com.supernova.launcher.data.TransitionPresets
import com.supernova.launcher.ui.settings.preview.MotionPreviewParts
import com.supernova.launcher.ui.settings.preview.PageEffectPreviewView
import kotlinx.coroutines.launch

// The Labs transition builder: a live preview, a zoomable two-lane timeline
// and an inspector for the selected chip, in cards. The JSON is still the
// storage and the share format; this only edits it.
class CustomTransitionBuilderActivity : AppCompatActivity() {

    private class Field(val layout: TextInputLayout, val edit: TextInputEditText)

    private class Dropdown(
        val layout: TextInputLayout,
        val view: MaterialAutoCompleteTextView,
        val items: List<String>
    )

    private lateinit var preview: PageEffectPreviewView
    private lateinit var timeline: TransitionTimelineView
    private lateinit var timelineScroll: HorizontalScrollView
    private lateinit var zoomLabel: TextView
    private lateinit var presets: ChipGroup
    private lateinit var chipSummary: TextView
    private lateinit var chipEmpty: TextView
    private lateinit var channelDrop: Dropdown
    private lateinit var easeDrop: Dropdown
    private lateinit var fromField: Field
    private lateinit var valueField: Field
    private lateinit var atField: Field
    private lateinit var toField: Field
    private lateinit var stackSwitch: SwitchCompat
    private lateinit var duplicateButton: MaterialButton
    private lateinit var deleteButton: MaterialButton
    private lateinit var nameField: Field

    private var spec: CustomTransition = CustomTransition()
    private var populating = false

    private val channels = CustomTransition.Channel.entries
    private val eases = CustomTransition.Ease.entries

    private val accent by lazy { MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary, ContextCompat.getColor(this, R.color.accent)) }
    private val ink by lazy { ContextCompat.getColor(this, R.color.textPrimary) }
    private val inkSoft by lazy { ContextCompat.getColor(this, R.color.textSecondary) }
    private val hairline by lazy { ContextCompat.getColor(this, R.color.settingsDivider) }
    private val pill by lazy { ContextCompat.getColor(this, R.color.settingsPillBg) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        spec = CustomTransitionStore.load(this) ?: TransitionPresets.nod
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val background = ContextCompat.getColor(this, R.color.background)
        window.setBackgroundDrawable(ColorDrawable(background))
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = background
        window.navigationBarColor = background
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        // Everything scrolls, and the bottom padding grows with the keyboard,
        // so a field is never left under it.
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(background)
        }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.setPadding(
                bars.left + dp(16), bars.top + dp(12), bars.right + dp(16),
                maxOf(bars.bottom, ime) + dp(20)
            )
            insets
        }
        setContentView(scroll)

        root.addView(topBar())
        root.addView(previewCard())
        root.addView(timelineCard())
        root.addView(chipCard())
        root.addView(specCard())

        preview.onProgress = { timeline.playhead = it }
        timeline.onChanged = {
            spec = timeline.transition
            preview.show(ScrollEffect.CUSTOM, spec, replay = false)
            syncAtTo()
            presets.clearCheck()
        }
        timeline.onSelected = { populate() }
        timeline.onZoomChanged = { showZoom(it) }
        timelineScroll.addOnLayoutChangeListener { _, left, _, right, _, _, _, _, _ ->
            val width = right - left
            if (width > 0 && timeline.baseWidthPx != width) timeline.baseWidthPx = width
        }
        showZoom(timeline.zoom)
        refresh(play = true)
    }

    // --- cards -------------------------------------------------------------

    private fun topBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        bar.addView(
            button("", Kind.TEXT) { finish() }.apply {
                icon = ContextCompat.getDrawable(this@CustomTransitionBuilderActivity, R.drawable.ic_back)
                iconTint = ColorStateList.valueOf(ink)
                iconGravity = MaterialButton.ICON_GRAVITY_START
                iconPadding = 0
                contentDescription = getString(R.string.custom_transition_back)
                minWidth = dp(40)
                setPadding(dp(8), 0, dp(8), 0)
            },
            LinearLayout.LayoutParams(dp(44), dp(44))
        )
        bar.addView(
            TextView(this).apply {
                text = getString(R.string.custom_transition_builder)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(ink)
                setPadding(dp(4), 0, dp(8), 0)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        bar.addView(button(getString(R.string.save), Kind.FILLED) { save() })
        return bar
    }

    private fun previewCard(): View {
        val card = card(getString(R.string.custom_transition_section_preview))
        val frame = FrameLayout(this).apply {
            background = MotionPreviewParts.wallpaperBackground(this@CustomTransitionBuilderActivity, dp(16).toFloat())
            clipToOutline = true
        }
        preview = PageEffectPreviewView(this, previewIcons(), 4)
        frame.addView(preview, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        card.addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(200)))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(2))
        }
        row.addView(button(getString(R.string.custom_transition_replay), Kind.TEXT) { preview.replay() })
        row.addView(
            TextView(this).apply {
                text = getString(R.string.custom_transition_preview_hint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(inkSoft)
                gravity = Gravity.END
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        card.addView(row)

        card.addView(
            TextView(this).apply {
                text = getString(R.string.custom_transition_presets_hint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                setTextColor(inkSoft)
                setPadding(0, dp(8), 0, dp(6))
            }
        )
        presets = ChipGroup(this).apply {
            isSingleSelection = true
            isSingleLine = true
            setChipSpacing(dp(8))
        }
        TransitionPresets.all.forEach { preset ->
            val chip = Chip(this).apply {
                id = View.generateViewId()
                text = preset.name
                isCheckable = true
                isCheckedIconVisible = true
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(ink)
                chipCornerRadius = dp(18).toFloat()
                chipBackgroundColor = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(ColorUtils.setAlphaComponent(accent, 46), pill)
                )
                setOnClickListener { loadPreset(preset) }
            }
            presets.addView(chip)
        }
        val scroller = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(presets, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        card.addView(scroller)
        return card
    }

    private fun timelineCard(): View {
        val card = card(getString(R.string.custom_transition_section_timeline))
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setPadding(0, 0, 0, dp(8))
        }
        controls.addView(
            View(this),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        controls.addView(smallButton("−", getString(R.string.custom_transition_zoom_out)) { timeline.zoom /= 1.5f })
        zoomLabel = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(inkSoft)
            gravity = Gravity.CENTER
            minWidth = dp(48)
        }
        controls.addView(zoomLabel)
        controls.addView(smallButton("+", getString(R.string.custom_transition_zoom_in)) { timeline.zoom *= 1.5f })
        controls.addView(button(getString(R.string.custom_transition_fit), Kind.TEXT) {
            timeline.zoom = 1f
            timelineScroll.smoothScrollTo(0, 0)
        })
        card.addView(controls)

        timeline = TransitionTimelineView(this)
        timelineScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(timeline, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        card.addView(timelineScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(hint(getString(R.string.custom_transition_timeline_hint), top = dp(8)))
        card.addView(hint(getString(R.string.custom_transition_lane_rule), top = dp(4)))
        return card
    }

    private fun chipCard(): View {
        val card = card(getString(R.string.custom_transition_builder_chip))
        chipSummary = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            text = getString(R.string.custom_transition_builder_no_chip)
        }
        card.addView(chipSummary)
        chipEmpty = hint(getString(R.string.custom_transition_chip_empty), top = dp(2))
        card.addView(chipEmpty)

        channelDrop = dropdown(getString(R.string.custom_transition_builder_channel), channels.map { channelName(it) }) { position ->
            updateSelected { it.copy(channel = channels[position]) }
        }
        easeDrop = dropdown(getString(R.string.custom_transition_builder_easing), eases.map { easeName(it) }) { position ->
            updateSelected { it.copy(ease = eases[position]) }
        }
        card.addView(equalRow(listOf(channelDrop.layout, easeDrop.layout)))

        fromField = numberField(getString(R.string.custom_transition_builder_from)) { value ->
            updateSelected { it.copy(from = value) }
        }
        valueField = numberField(getString(R.string.custom_transition_builder_value)) { value ->
            updateSelected { it.copy(value = value) }
        }
        card.addView(equalRow(listOf(fromField.layout, valueField.layout)))

        atField = numberField(getString(R.string.custom_transition_at)) { value ->
            updateSelected { ChipEdit.resizeStart(it, value) }
        }
        toField = numberField(getString(R.string.custom_transition_to)) { value ->
            updateSelected { ChipEdit.resizeEnd(it, value) }
        }
        card.addView(equalRow(listOf(atField.layout, toField.layout)))

        // The app's own switch, as every other toggle: Material's Material3
        // switch asked for a text-on layout this theme does not give it.
        stackSwitch = SwitchCompat(this).apply {
            showText = false
            textOn = ""
            textOff = ""
            text = getString(R.string.custom_transition_stack_switch)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ink)
            setPadding(0, dp(10), 0, dp(2))
            setOnCheckedChangeListener { _, checked -> if (!populating) updateSelected { it.copy(add = checked) } }
        }
        card.addView(stackSwitch)

        val add = button(getString(R.string.custom_transition_builder_add_chip), Kind.TONAL) { addChip() }
        duplicateButton = button(getString(R.string.custom_transition_builder_duplicate), Kind.OUTLINED) { duplicate() }
        deleteButton = button(getString(R.string.custom_transition_builder_delete), Kind.TEXT) { delete() }.apply {
            setTextColor(ContextCompat.getColor(this@CustomTransitionBuilderActivity, R.color.accentRed))
        }
        card.addView(equalRow(listOf(add, duplicateButton, deleteButton)))
        return card
    }

    private fun specCard(): View {
        val card = card(getString(R.string.custom_transition_section_spec))
        nameField = numberField(getString(R.string.custom_transition_name), numeric = false) { }
        nameField.edit.inputType = InputType.TYPE_CLASS_TEXT
        nameField.edit.doAfterTextChanged {
            if (!populating) {
                spec = spec.copy(name = it?.toString()?.trim().orEmpty())
                presets.clearCheck()
            }
        }
        card.addView(nameField.layout)
        card.addView(
            equalRow(
                listOf(
                    button(getString(R.string.custom_transition_builder_import), Kind.OUTLINED) { importDialog() },
                    button(getString(R.string.labs_custom_transition_share), Kind.OUTLINED) { share() },
                    button(getString(R.string.custom_transition_builder_reset), Kind.TEXT) { resetSpec() }
                )
            )
        )
        card.addView(hint(getString(R.string.custom_transition_builder_values), top = dp(10)))
        return card
    }

    // --- pieces ------------------------------------------------------------

    private fun card(title: String): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(ContextCompat.getColor(this@CustomTransitionBuilderActivity, R.color.surface))
                setStroke(density().toInt().coerceAtLeast(1), hairline)
            }
        }
        column.addView(
            TextView(this).apply {
                text = title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(inkSoft)
                letterSpacing = 0.04f
                setPadding(0, 0, 0, dp(10))
            }
        )
        column.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(12)
        }
        return column
    }

    private fun hint(text: String, top: Int = 0): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
        setTextColor(inkSoft)
        setPadding(0, top, 0, 0)
    }

    private enum class Kind { FILLED, TONAL, OUTLINED, TEXT }

    private fun button(text: String, kind: Kind, onClick: () -> Unit): MaterialButton {
        val attribute = when (kind) {
            Kind.FILLED, Kind.TONAL -> com.google.android.material.R.attr.materialButtonStyle
            Kind.OUTLINED -> com.google.android.material.R.attr.materialButtonOutlinedStyle
            Kind.TEXT -> android.R.attr.borderlessButtonStyle
        }
        return MaterialButton(this, null, attribute).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            insetTop = 0
            insetBottom = 0
            minHeight = dp(40)
            minimumHeight = dp(40)
            setPadding(dp(14), 0, dp(14), 0)
            cornerRadius = dp(20)
            when (kind) {
                Kind.TONAL -> {
                    backgroundTintList = ColorStateList.valueOf(pill)
                    setTextColor(ink)
                    rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(ink, 30))
                }
                Kind.OUTLINED -> {
                    strokeWidth = dp(1)
                    strokeColor = ColorStateList.valueOf(hairline)
                    setTextColor(ink)
                    backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                }
                Kind.TEXT -> setTextColor(accent)
                Kind.FILLED -> Unit
            }
            setOnClickListener { onClick() }
        }
    }

    private fun smallButton(text: String, description: String, onClick: () -> Unit): MaterialButton =
        button(text, Kind.OUTLINED, onClick).apply {
            contentDescription = description
            minWidth = dp(40)
            setPadding(dp(8), 0, dp(8), 0)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        }

    private fun equalRow(views: List<View>): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        views.forEachIndexed { index, view ->
            row.addView(
                view,
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (index < views.size - 1) marginEnd = dp(8)
                }
            )
        }
        return row
    }

    private fun dropdown(label: String, items: List<String>, onPick: (Int) -> Unit): Dropdown {
        val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle).apply {
            hint = label
            setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
        }
        val view = MaterialAutoCompleteTextView(this).apply {
            inputType = InputType.TYPE_NULL
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ink)
            setSimpleItems(items.toTypedArray())
            setOnItemClickListener { _, _, position, _ -> if (!populating) onPick(position) }
        }
        layout.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return Dropdown(layout, view, items)
    }

    private fun numberField(label: String, numeric: Boolean = true, onValue: (Float) -> Unit): Field {
        val layout = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = label
            setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat())
        }
        val edit = TextInputEditText(this).apply {
            if (numeric) {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(ink)
            setSingleLine()
            doAfterTextChanged { text ->
                if (populating || !numeric) return@doAfterTextChanged
                text?.toString()?.toFloatOrNull()?.let(onValue)
            }
            setOnFocusChangeListener { view, focused ->
                if (focused) view.post {
                    view.requestRectangleOnScreen(android.graphics.Rect(0, 0, view.width, view.height), true)
                }
            }
        }
        layout.addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return Field(layout, edit)
    }

    private fun previewIcons(): List<Drawable> {
        val manager = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val icons = runCatching { manager.queryIntentActivities(intent, 0) }.getOrNull().orEmpty()
            .take(12)
            .mapNotNull { runCatching { it.loadIcon(manager) }.getOrNull() }
        if (icons.size >= 6) return icons
        // Circles, not ovals: an intrinsic square size keeps them round in a
        // cell that is wider than it is tall.
        val size = dp(36)
        return List(12) { index ->
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setSize(size, size)
                setColor(ICON_COLOURS[index % ICON_COLOURS.size])
            }
        }
    }

    // --- state -------------------------------------------------------------

    private fun refresh(play: Boolean) {
        timeline.transition = spec
        preview.show(ScrollEffect.CUSTOM, spec, replay = play)
        populate()
        populating = true
        nameField.edit.setText(spec.name)
        populating = false
        syncPresets()
    }

    private fun syncPresets() {
        val matching = TransitionPresets.matching(spec)
        presets.clearCheck()
        if (matching != null) {
            val index = TransitionPresets.all.indexOf(matching)
            (presets.getChildAt(index) as? Chip)?.let { presets.check(it.id) }
        }
    }

    private fun populate() {
        val selected = timeline.selected
        val chip = selected?.let { (if (it.incoming) spec.incoming else spec.outgoing).getOrNull(it.index) }
        val enabled = chip != null
        listOf(channelDrop, easeDrop).forEach {
            it.layout.isEnabled = enabled
            it.view.isEnabled = enabled
        }
        listOf(fromField, valueField, atField, toField).forEach {
            it.layout.isEnabled = enabled
            it.edit.isEnabled = enabled
        }
        stackSwitch.isEnabled = enabled
        duplicateButton.isEnabled = enabled
        deleteButton.isEnabled = enabled
        chipEmpty.visibility = if (enabled) View.GONE else View.VISIBLE
        populating = true
        if (chip != null && selected != null) {
            channelDrop.view.setText(channelDrop.items[channels.indexOf(chip.channel)], false)
            easeDrop.view.setText(easeDrop.items[eases.indexOf(chip.ease)], false)
            fromField.edit.setText(chip.from.toString())
            valueField.edit.setText(chip.value.toString())
            atField.edit.setText(chip.at.toString())
            toField.edit.setText(chip.to.toString())
            stackSwitch.isChecked = chip.add
            chipSummary.text = getString(
                R.string.custom_transition_builder_span,
                laneName(selected.incoming),
                chip.at,
                chip.to
            )
        } else {
            channelDrop.view.setText("", false)
            easeDrop.view.setText("", false)
            listOf(fromField, valueField, atField, toField).forEach { it.edit.setText("") }
            stackSwitch.isChecked = false
            chipSummary.text = getString(R.string.custom_transition_builder_no_chip)
        }
        populating = false
    }

    private fun syncAtTo() {
        val selected = timeline.selected ?: return
        val chip = (if (selected.incoming) spec.incoming else spec.outgoing).getOrNull(selected.index) ?: return
        populating = true
        atField.edit.setText(chip.at.toString())
        toField.edit.setText(chip.to.toString())
        populating = false
        chipSummary.text = getString(R.string.custom_transition_builder_span, laneName(selected.incoming), chip.at, chip.to)
    }

    private fun laneName(incoming: Boolean): String = getString(
        if (incoming) R.string.custom_transition_builder_incoming else R.string.custom_transition_builder_outgoing
    )

    private fun updateSelected(change: (CustomTransition.Chip) -> CustomTransition.Chip) {
        val selected = timeline.selected ?: return
        val chips = (if (selected.incoming) spec.incoming else spec.outgoing).toMutableList()
        if (selected.index !in chips.indices) return
        chips[selected.index] = change(chips[selected.index])
        spec = if (selected.incoming) spec.copy(incoming = chips) else spec.copy(outgoing = chips)
        timeline.transition = spec
        preview.show(ScrollEffect.CUSTOM, spec, replay = false)
        presets.clearCheck()
        chipSummary.text = getString(
            R.string.custom_transition_builder_span,
            laneName(selected.incoming),
            chips[selected.index].at,
            chips[selected.index].to
        )
    }

    private fun loadPreset(preset: CustomTransition) {
        spec = preset
        timeline.selected = null
        refresh(play = true)
    }

    private fun addChip() {
        val incoming = timeline.selected?.incoming ?: true
        val channel = channels.getOrNull(channelDrop.view.listSelection) ?: CustomTransition.Channel.MOVE_X
        // A new chip starts at the playhead and does something sensible for
        // its channel, rather than a flat line the user has to guess at.
        val at = timeline.playhead.coerceIn(0f, 0.8f)
        val chip = when (channel) {
            CustomTransition.Channel.MOVE_X -> CustomTransition.Chip(at, at + 0.2f, channel, 0f, -0.15f, CustomTransition.Ease.EASE_OUT)
            CustomTransition.Channel.MOVE_Y -> CustomTransition.Chip(at, at + 0.2f, channel, 0f, 0.08f, CustomTransition.Ease.EASE_OUT)
            CustomTransition.Channel.SCALE -> CustomTransition.Chip(at, at + 0.2f, channel, 1f, 1.05f, CustomTransition.Ease.EASE_OUT)
            CustomTransition.Channel.ALPHA -> CustomTransition.Chip(at, at + 0.2f, channel, 1f, 0f, CustomTransition.Ease.EASE_IN)
            else -> CustomTransition.Chip(at, at + 0.2f, channel, 0f, 10f, CustomTransition.Ease.EASE_OUT)
        }
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
        chips += ChipEdit.move(chip, chip.at + 0.05f)
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

    private fun resetSpec() {
        spec = CustomTransition(name = getString(R.string.custom_transition_builder))
        timeline.selected = null
        refresh(play = true)
    }

    private fun showZoom(zoom: Float) {
        zoomLabel.text = getString(R.string.custom_transition_zoom, (zoom * 100).toInt())
    }

    private fun importDialog() {
        val edit = TextInputEditText(this).apply {
            setText(CustomTransitionStore.raw(this@CustomTransitionBuilderActivity) ?: CustomTransition.encode(spec))
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setPadding(dp(16), dp(12), dp(16), dp(12))
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
        val named = if (spec.name.isBlank()) spec.copy(name = getString(R.string.custom_transition_builder)) else spec
        if (named.validated() == null || !CustomTransitionStore.save(this, CustomTransition.encode(named))) {
            Toast.makeText(this, R.string.labs_custom_transition_invalid, Toast.LENGTH_LONG).show()
            return
        }
        spec = named
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

    private fun dp(value: Int): Int = (value * density()).toInt()

    private fun density(): Float = resources.displayMetrics.density

    private companion object {
        val ICON_COLOURS = intArrayOf(
            0xFF3F72AF.toInt(), 0xFF2E7D5B.toInt(), 0xFFA34E44.toInt(),
            0xFF7B4FA0.toInt(), 0xFFB8860B.toInt(), 0xFF4A6FA5.toInt()
        )
    }
}

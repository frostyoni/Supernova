package com.supernova.launcher.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import com.supernova.launcher.R
import com.supernova.launcher.data.LauncherItem
import com.supernova.launcher.data.WidgetStackMember
import com.supernova.launcher.data.WidgetStacks
import com.supernova.launcher.databinding.ItemStackMemberBinding
import com.supernova.launcher.databinding.SheetStackManageBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

// Manage stack: every widget of a workspace stack in the order the cell
// swipes through, an arrow to move one and a button to take it out, and Add
// widget for one more. Nothing waits for a Save: each row writes at once and
// the list is drawn again from the model, so the sheet shows what the cell
// does.
class StackManageSheet private constructor(
    private val context: Context,
    private val viewModel: LauncherViewModel,
    private val widgets: WidgetController,
    private val itemId: String
) {

    private val binding = SheetStackManageBinding.inflate(LayoutInflater.from(context))
    private val inflater = LayoutInflater.from(context)
    private val manager = AppWidgetManager.getInstance(context)
    private val pm = context.packageManager
    private val dialog = Sheets.open(context)

    private fun open(scope: CoroutineScope) {
        dialog.setContentView(binding.root)
        binding.addWidget.setOnClickListener {
            // The picker opens over this sheet; a member it binds arrives in
            // the list above through the model.
            live()?.let(widgets::showPickerForStack)
        }
        dialog.show()
        // Every write lands in the model after the sheet opens, so the list
        // follows the model rather than the taps; the first emission draws it.
        val watching = scope.launch(start = CoroutineStart.LAZY) { viewModel.state.collect { render() } }
        dialog.setOnDismissListener { watching.cancel() }
        watching.start()
    }

    // Read live, so a write made a moment ago is what the next render lists.
    private fun live(): LauncherItem? =
        viewModel.state.value.model.items.firstOrNull { it.id == itemId }

    private fun render() {
        val current = live()
        // Gone from the model while the sheet was up (removed from another
        // window): there is nothing left to curate.
        if (current == null) {
            dialog.dismiss()
            return
        }
        val order = WidgetStacks.order(current)
        val empty = order.isEmpty()
        binding.stackTitle.text = context.getString(R.string.stack_manage_title, order.size)
        binding.stackScroll.visibility = if (empty) View.GONE else View.VISIBLE
        binding.stackEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        // An empty stack has no widget to add beside: it is a cell to take
        // off the home screen.
        binding.addWidget.visibility = if (empty) View.GONE else View.VISIBLE
        val canAdd = StackManageRows.canAdd(order.size)
        binding.addWidget.isEnabled = canAdd
        binding.addWidget.alpha = if (canAdd) 1f else DISABLED_ALPHA
        binding.addWidgetHint.text = if (canAdd) {
            context.getString(R.string.stack_manage_add_desc)
        } else {
            context.getString(R.string.stack_manage_full, WidgetStacks.MAX_MEMBERS)
        }
        bindRows(current, order)
    }

    private fun bindRows(current: LauncherItem, order: List<WidgetStackMember>) {
        binding.stackRows.removeAllViews()
        StackManageRows.rows(order.size, current.widgetStackFront).forEach { row ->
            val rowView = ItemStackMemberBinding.inflate(inflater, binding.stackRows, false)
            bindRow(rowView, order[row.index], row)
            binding.stackRows.addView(rowView.root)
        }
    }

    private fun bindRow(
        rowView: ItemStackMemberBinding,
        member: WidgetStackMember,
        row: StackManageRows.Row
    ) {
        val info = member.appWidgetId?.let { id -> runCatching { manager.getAppWidgetInfo(id) }.getOrNull() }
        // A widget whose provider is gone keeps its row: it can still be
        // moved out of the way and removed.
        val icon = info?.let { info ->
            runCatching { info.loadIcon(context, context.resources.displayMetrics.densityDpi) }.getOrNull()
        }
            ?: AppCompatResources.getDrawable(context, R.drawable.ic_menu_widgets)
        rowView.memberIcon.setImageDrawable(icon)
        val label = info?.let { info -> runCatching { info.loadLabel(pm) }.getOrNull() }
        rowView.memberLabel.text = label?.toString()?.takeIf { it.isNotBlank() }
            ?: member.provider.ifBlank { context.getString(R.string.widget) }
        rowView.memberFront.text = if (row.front) context.getString(R.string.stack_manage_front) else ""
        // The ends of the list cannot move outward.
        rowView.memberMoveUp.isEnabled = row.canMoveUp
        rowView.memberMoveUp.alpha = if (row.canMoveUp) 1f else DISABLED_ALPHA
        rowView.memberMoveUp.setOnClickListener {
            viewModel.moveStackMember(itemId, row.index, row.index - 1)
        }
        rowView.memberMoveDown.isEnabled = row.canMoveDown
        rowView.memberMoveDown.alpha = if (row.canMoveDown) 1f else DISABLED_ALPHA
        rowView.memberMoveDown.setOnClickListener {
            viewModel.moveStackMember(itemId, row.index, row.index + 1)
        }
        rowView.memberRemove.setOnClickListener {
            // The id the stack drops is deleted here, or the orphan sweep
            // finds a widget nothing points at.
            viewModel.removeStackMemberAt(itemId, row.index) { freed ->
                freed?.let(widgets::releaseWidgetId)
            }
        }
    }

    companion object {
        fun show(
            context: Context,
            scope: CoroutineScope,
            viewModel: LauncherViewModel,
            widgets: WidgetController,
            item: LauncherItem
        ) = StackManageSheet(context, viewModel, widgets, item.id).open(scope)

        private const val DISABLED_ALPHA = 0.4f
    }
}

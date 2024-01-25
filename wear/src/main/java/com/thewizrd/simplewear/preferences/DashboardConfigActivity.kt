package com.thewizrd.simplewear.preferences

import android.content.DialogInterface
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewConfigurationCompat
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup
import androidx.recyclerview.widget.ItemTouchHelper
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.activities.AppCompatLiteActivity
import com.thewizrd.simplewear.adapters.AddButtonAdapter
import com.thewizrd.simplewear.adapters.DashBattStatusItemAdapter
import com.thewizrd.simplewear.adapters.TileActionAdapter
import com.thewizrd.simplewear.databinding.LayoutDashboardConfigBinding
import com.thewizrd.simplewear.helpers.AcceptDenyDialog
import com.thewizrd.simplewear.helpers.TileActionsItemTouchCallback
import kotlin.math.roundToInt

class DashboardConfigActivity : AppCompatLiteActivity() {
    companion object {
        private val MAX_BUTTONS = Actions.entries.size
        private val DEFAULT_TILES = Actions.entries
    }

    private lateinit var binding: LayoutDashboardConfigBinding
    private lateinit var concatAdapter: ConcatAdapter
    private lateinit var dashBattStatAdapter: DashBattStatusItemAdapter
    private lateinit var actionAdapter: TileActionAdapter
    private lateinit var addButtonAdapter: AddButtonAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutDashboardConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tileGridLayout.layoutManager = GridLayoutManager(this, 3).also { layoutMgr ->
            layoutMgr.spanSizeLookup = object : SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return if (concatAdapter.getItemViewType(position) == DashBattStatusItemAdapter.ITEM_TYPE) {
                        3
                    } else {
                        1
                    }
                }
            }
        }

        dashBattStatAdapter = DashBattStatusItemAdapter().apply {
            isVisible = Settings.isShowBatStatus()
        }
        actionAdapter = TileActionAdapter()
        addButtonAdapter = AddButtonAdapter()

        binding.tileGridLayout.adapter = ConcatAdapter(
            ConcatAdapter.Config.Builder()
                .setIsolateViewTypes(false)
                .build(),
            dashBattStatAdapter,
            actionAdapter
        ).also {
            concatAdapter = it
        }

        itemTouchHelper = ItemTouchHelper(TileActionsItemTouchCallback(actionAdapter))
        itemTouchHelper.attachToRecyclerView(binding.tileGridLayout)

        actionAdapter.onLongClickListener = {
            itemTouchHelper.startDrag(it)
        }

        actionAdapter.onListChanged = {
            if (it.size >= MAX_BUTTONS) {
                concatAdapter.removeAdapter(addButtonAdapter)
            } else {
                concatAdapter.addAdapter(addButtonAdapter)
            }
        }

        val config = Settings.getDashboardConfig()

        config?.let {
            actionAdapter.submitActions(it)
        } ?: run {
            actionAdapter.submitActions(DEFAULT_TILES)
        }

        addButtonAdapter.setOnClickListener {
            val allowedActions = Actions.entries.toMutableList()
            // Remove current actions
            allowedActions.removeAll(actionAdapter.getActions())

            AddActionDialogBuilder(this, allowedActions)
                .setOnActionSelectedListener(object :
                    AddActionDialogBuilder.OnActionSelectedListener {
                    override fun onActionSelected(action: Actions) {
                        actionAdapter.addAction(action)
                    }
                })
                .show()
        }

        binding.resetButton.setOnClickListener {
            AcceptDenyDialog.Builder(this) { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        actionAdapter.submitActions(DEFAULT_TILES)
                        Settings.setDashboardConfig(null)

                        dashBattStatAdapter.isVisible = true
                        Settings.setShowBatStatus(true)
                    }
                }
            }
                .setMessage(R.string.message_reset_to_default)
                .show()
        }

        binding.saveButton.setOnClickListener {
            val currentList = actionAdapter.getActions()
            Settings.setDashboardConfig(currentList)

            Settings.setShowBatStatus(dashBattStatAdapter.isVisible)

            // Close activity
            finish()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val currSelection = actionAdapter.getSelection()
            if (currSelection != null) {
                val r = Rect().also {
                    currSelection.getGlobalVisibleRect(it)
                }
                if (!r.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    actionAdapter.clearSelection()
                }
            } else {
                dashBattStatAdapter.getSelection()?.let { battView ->
                    val r = Rect().also {
                        battView.getGlobalVisibleRect(it)
                    }
                    if (!r.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                        dashBattStatAdapter.isChecked = false
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)) {
            // Don't forget the negation here
            val delta = -event.getAxisValue(MotionEventCompat.AXIS_SCROLL) *
                    ViewConfigurationCompat.getScaledVerticalScrollFactor(
                        ViewConfiguration.get(this), this
                    )

            // Swap these axes if you want to do horizontal scrolling instead
            binding.root.scrollBy(0, delta.roundToInt())

            return true
        }
        return super.onGenericMotionEvent(event)
    }
}
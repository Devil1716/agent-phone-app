package com.gemma.agentphone.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class CommandBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {
    private val input = TextInputEditText(context)
    private val runButton = MaterialButton(context)
    private val stopButton = MaterialButton(context)

    init {
        orientation = HORIZONTAL
        input.layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        runButton.text = "Run"
        stopButton.text = "Stop"
        addView(input)
        addView(runButton)
        addView(stopButton)
    }

    fun setCommand(value: String) {
        input.setText(value)
    }

    fun getCommand(): String = input.text?.toString().orEmpty()

    fun setOnRunClickListener(listener: OnClickListener) {
        runButton.setOnClickListener(listener)
    }

    fun setOnStopClickListener(listener: OnClickListener) {
        stopButton.setOnClickListener(listener)
    }
}

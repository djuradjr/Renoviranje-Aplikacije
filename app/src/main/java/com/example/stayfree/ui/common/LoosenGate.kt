package com.example.stayfree.ui.common

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.stayfree.R
import com.example.stayfree.util.PinGate
import com.example.stayfree.util.PinPrompt
import com.example.stayfree.util.StrictGate
import com.example.stayfree.util.TimeUtils
import kotlinx.coroutines.launch

/**
 * The single path for every change that weakens protection: a block switched off, a limit
 * raised, a rule deleted, a session ended early. Strict mode refuses it; otherwise the PIN,
 * when one is set, is asked once per screen. Tightening never comes through here.
 */
class LoosenGate(
    private val fragment: Fragment,
    private val strictGate: StrictGate,
    private val pinGate: PinGate
) {
    private var pinUnlocked = false

    /** [pin] false for settings the PIN does not cover but strict mode still locks. */
    fun run(pin: Boolean = true, onDenied: () -> Unit = {}, action: () -> Unit) {
        val scope = fragment.viewLifecycleOwner.lifecycleScope
        scope.launch {
            val remaining = strictGate.remainingMs()
            if (remaining > 0L) {
                val context = fragment.requireContext()
                Toast.makeText(
                    context,
                    context.getString(R.string.strict_locked_toast, TimeUtils.formatLongDuration(remaining)),
                    Toast.LENGTH_SHORT
                ).show()
                onDenied()
                return@launch
            }
            if (!pin || pinUnlocked || !pinGate.isPinSet()) {
                action()
                return@launch
            }
            PinPrompt.show(fragment.requireContext(), scope, pinGate, onCancel = onDenied) {
                pinUnlocked = true
                action()
            }
        }
    }
}

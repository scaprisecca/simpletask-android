package nl.mpcjanssen.simpletask

import android.content.Context
import android.graphics.Rect
import android.text.Editable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText

class SelectionTrackingEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {
    var onSelectionChangedListener: ((Int, Int) -> Unit)? = null

    override fun getText(): Editable {
        return super.getText() ?: Editable.Factory.getInstance().newEditable("")
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        if (hasFocus()) {
            onSelectionChangedListener?.invoke(selStart, selEnd)
        }
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        if (focused) {
            onSelectionChangedListener?.invoke(selectionStart, selectionEnd)
        }
    }
}

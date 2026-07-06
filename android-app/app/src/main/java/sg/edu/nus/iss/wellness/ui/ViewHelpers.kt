package sg.edu.nus.iss.wellness.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import retrofit2.HttpException
import sg.edu.nus.iss.wellness.R
import java.io.IOException

/**
 * Shared view-building helpers used by every authenticated screen.
 *
 * @author SA62 Team
 */
enum class ButtonStyle {
    PRIMARY,
    SECONDARY,
    DESTRUCTIVE
}
fun Activity.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

fun rounded(color: Int, radius: Int, stroke: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius.toFloat()
        setColor(color)
        stroke?.let { setStroke(1, it) }
    }

fun TextView.centered(): TextView = apply {
    gravity = Gravity.CENTER
    textAlignment = View.TEXT_ALIGNMENT_CENTER
}

fun LinearLayout.LayoutParams.withBottomMargin(value: Int): LinearLayout.LayoutParams = apply {
    bottomMargin = value
}

fun LinearLayout.LayoutParams.withEndMargin(value: Int): LinearLayout.LayoutParams = apply {
    marginEnd = value
}
fun Activity.title(text: String, size: Int): TextView =
    TextView(this).apply {
        this.text = text
        textSize = size.toFloat()
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(getColor(R.color.text_primary))
        setPadding(0, 0, 0, dp(6))
    }

fun Activity.accent(text: String): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(getColor(R.color.primary_dark))
        setPadding(0, 0, 0, dp(6))
    }

fun Activity.body(text: String): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(getColor(R.color.text_secondary))
        setLineSpacing(0f, 1.1f)
        setPadding(0, 0, 0, dp(6))
    }

fun Activity.caption(text: String): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(getColor(R.color.text_secondary))
        setPadding(0, dp(4), 0, dp(4))
    }

fun Activity.pill(text: String): TextView =
    TextView(this).apply {
        this.text = text
        textSize = 12f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(getColor(R.color.primary_dark))
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = rounded(getColor(R.color.bg_subtle), dp(16))
        layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f).withEndMargin(dp(8))
    }
fun Activity.card(
    fillColor: Int = getColor(R.color.bg_surface),
    stroke: Int = getColor(R.color.border_default)
): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
        background = rounded(fillColor, dp(16), stroke)
        elevation = dp(1).toFloat()
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .withBottomMargin(dp(14))
    }

fun Activity.chatBubble(label: String, message: String, user: Boolean): LinearLayout =
    card(
        fillColor = if (user) getColor(R.color.primary) else getColor(R.color.bg_surface),
        stroke = if (user) getColor(R.color.primary) else getColor(R.color.border_default)
    ).apply {
        addView(caption(label).apply { setTextColor(if (user) Color.WHITE else getColor(R.color.text_secondary)) })
        addView(body(message).apply { setTextColor(if (user) Color.WHITE else getColor(R.color.text_primary)) })
    }

fun Activity.infoCard(heading: String, detail: String): LinearLayout =
    card().apply {
        addView(accent(heading))
        addView(body(detail))
    }

fun Activity.horizontal(): LinearLayout =
    LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .withBottomMargin(dp(12))
    }

fun Activity.input(hint: String, value: String, inputType: Int = android.text.InputType.TYPE_CLASS_TEXT): EditText =
    EditText(this).apply {
        this.hint = hint
        setText(value)
        this.inputType = inputType
        textSize = 16f
        setTextColor(getColor(R.color.text_primary))
        setHintTextColor(getColor(R.color.text_secondary))
        setSingleLine(hint != "Notes")
        minHeight = dp(60)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = rounded(getColor(R.color.bg_surface), dp(12), getColor(R.color.border_default))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .withBottomMargin(dp(12))
    }
fun Activity.styledButton(text: String, style: ButtonStyle, width: Int, height: Int, onClick: () -> Unit): Button =
    Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = if (height <= dp(48)) 14f else 16f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(
            when (style) {
                ButtonStyle.PRIMARY, ButtonStyle.DESTRUCTIVE -> getColor(R.color.text_on_primary)
                ButtonStyle.SECONDARY -> getColor(R.color.primary)
            }
        )
        backgroundTintList = null
        setBackgroundResource(
            when (style) {
                ButtonStyle.PRIMARY -> R.drawable.bg_button_primary
                ButtonStyle.SECONDARY -> R.drawable.bg_button_secondary
                ButtonStyle.DESTRUCTIVE -> R.drawable.bg_button_destructive
            }
        )
        minHeight = dp(40)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(width, height).withBottomMargin(dp(12))
    }

fun Activity.addButton(container: LinearLayout, text: String, style: ButtonStyle, onClick: () -> Unit) {
    container.addView(styledButton(text, style, ViewGroup.LayoutParams.MATCH_PARENT, dp(56), onClick))
}

fun Activity.smallButton(text: String, style: ButtonStyle, onClick: () -> Unit): Button =
    styledButton(text, style, 0, dp(48), onClick).apply {
        layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).withEndMargin(dp(8))
    }

fun Activity.headerIconButton(text: String, style: ButtonStyle, onClick: () -> Unit): Button =
    styledButton(text, style, ViewGroup.LayoutParams.WRAP_CONTENT, dp(40), onClick).apply {
        textSize = 12f
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
            .withEndMargin(dp(6))
    }
fun Activity.addStateBlock(container: LinearLayout, title: String, detail: String, icon: String, error: Boolean = false) {
    val block = card(
        fillColor = if (error) Color.rgb(254, 242, 242) else getColor(R.color.bg_surface),
        stroke = if (error) Color.rgb(254, 202, 202) else getColor(R.color.border_default)
    )
    block.gravity = Gravity.CENTER_HORIZONTAL
    val mark = TextView(this).apply {
        text = icon
        textSize = 20f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = rounded(if (error) getColor(R.color.error) else getColor(R.color.primary), dp(32))
        layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).withBottomMargin(dp(10))
    }
    block.addView(mark)
    block.addView(title(title, 20).centered())
    block.addView(body(detail).centered())
    container.addView(block)
}

fun Activity.showError(container: LinearLayout, title: String, detail: String) {
    container.removeAllViews()
    addStateBlock(container, title, detail, "!", true)
}

fun apiErrorMessage(prefix: String, throwable: Throwable): String = when (throwable) {
    is HttpException -> when (throwable.code()) {
        401, 403 -> "$prefix. Please log out and log in again."
        503 -> "$prefix. Check Python AI service and Ollama."
        else -> "$prefix. Backend returned HTTP ${throwable.code()}."
    }
    is IOException -> "$prefix. Check that the backend is reachable from the emulator."
    else -> "$prefix. ${throwable.javaClass.simpleName}."
}

fun Activity.highlightTab(buttons: List<Button>, selected: Button) {
    buttons.forEach { button ->
        button.backgroundTintList = null
        button.setBackgroundResource(if (button == selected) R.drawable.bg_button_secondary else android.R.color.transparent)
        button.setTextColor(if (button == selected) getColor(R.color.primary) else getColor(R.color.text_secondary))
        button.isAllCaps = false
    }
}


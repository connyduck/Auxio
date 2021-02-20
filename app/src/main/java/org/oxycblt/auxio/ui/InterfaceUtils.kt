package org.oxycblt.auxio.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Point
import android.graphics.drawable.AnimatedVectorDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.oxycblt.auxio.R
import org.oxycblt.auxio.logE
import org.oxycblt.auxio.playback.PlaybackViewModel

// --- VIEW CONFIGURATION ---

/**
 * Disable an image button.
 */
fun ImageButton.disable() {
    if (isEnabled) {
        imageTintList = R.color.inactive_color.toStateList(context)
        isEnabled = false
    }
}

/**
 * Set a [TextView] text color, without having to resolve the resource.
 */
fun TextView.setTextColorResource(@ColorRes color: Int) {
    setTextColor(color.toColor(context))
}

/**
 * Apply accents to a [MaterialButton] manually.
 * Yes, I could change my theming to Material but that would have so many second-and-third order
 * effects that I *really* dont want to deal with it. This hack works.
 * @param highlighted Whether the MaterialButton has an "Unimportant" style or not.
 * Required because you cant determine a style of a view before API 29
 */
fun MaterialButton.applyAccents(highlighted: Boolean) {
    if (highlighted) {
        backgroundTintList = Accent.get().color.toStateList(context)
    } else {
        setTextColor(Accent.get().color.toColor(context))
    }
}

// --- CONVENIENCE ---

/**
 * Convenience method for getting a plural.
 * @param pluralsRes Resource for the plural
 * @param value Int value for the plural.
 * @return The formatted string requested
 */
fun Context.getPlural(@PluralsRes pluralsRes: Int, value: Int): String {
    return resources.getQuantityString(pluralsRes, value, value)
}

/**
 * Shortcut to get a [LayoutInflater] from a [Context]
 */
val Context.inflater: LayoutInflater get() = LayoutInflater.from(this)

/**
 * Shortcut to get an [AnimatedVectorDrawable] from a [Context]
 */
fun Context.getAnimatedDrawable(@DrawableRes drawableRes: Int): AnimatedVectorDrawable {
    return ContextCompat.getDrawable(this, drawableRes) as AnimatedVectorDrawable
}

/**
 * Create a [Toast] from a [String]
 * @param context [Context] required to create the toast
 */
fun String.createToast(context: Context) {
    Toast.makeText(context.applicationContext, this, Toast.LENGTH_SHORT).show()
}

/**
 * Ensure that a not-null [AppCompatActivity] will be returned.
 * @throws IllegalStateException When there is no [AppCompatActivity] or if the activity is null
 */
fun Fragment.requireCompatActivity(): AppCompatActivity {
    val activity = requireActivity()

    if (activity is AppCompatActivity) {
        return activity
    } else {
        error("Required AppCompatActivity, got ${activity::class.simpleName} instead.")
    }
}

/**
 * Resolve a color.
 * @param context [Context] required
 * @return The resolved color, black if the resolving process failed.
 */
@ColorInt
fun Int.toColor(context: Context): Int {
    return try {
        ContextCompat.getColor(context, this)
    } catch (e: Resources.NotFoundException) {
        logE("Attempted color load failed.")

        // Default to the emergency color [Black] if the loading fails.
        ContextCompat.getColor(context, android.R.color.black)
    }
}

/**
 * Resolve a color and turn it into a [ColorStateList]
 * @param context [Context] required
 * @return The resolved color as a [ColorStateList]
 * @see toColor
 */
fun Int.toStateList(context: Context): ColorStateList = ColorStateList.valueOf(toColor(context))

// --- CONFIGURATION ---

/**
 * Check if edge is on. Really a glorified version check.
 * @return Whether edge is on.
 */
fun isEdgeOn(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1

/**
 * Determine if the device is currently in landscape.
 * @param resources [Resources] required
 */
fun isLandscape(resources: Resources): Boolean {
    return resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
}

/**
 * Determine if we are in tablet mode or not
 */
fun isTablet(resources: Resources): Boolean {
    val layout = resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK

    return layout == Configuration.SCREENLAYOUT_SIZE_XLARGE ||
        layout == Configuration.SCREENLAYOUT_SIZE_LARGE
}

/**
 * Determine if the tablet is XLARGE, ignoring normal tablets.
 */
fun isXLTablet(resources: Resources): Boolean {
    val layout = resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK

    return layout == Configuration.SCREENLAYOUT_SIZE_XLARGE
}

/**
 * Get the span count for most RecyclerViews. These probably work right on most displays. Trust me.
 */
fun RecyclerView.getSpans(): Int {
    return if (isLandscape(resources)) {
        if (isXLTablet(resources)) 3 else 2
    } else {
        if (isXLTablet(resources)) 2 else 1
    }
}

/**
 * Check if we are in the "Irregular" landscape mode (e.g landscape, but nav bar is on the sides)
 * Used to disable most of edge-to-edge if that's the case, as I cant get it to work on this mode.
 * @return True if we are in the irregular landscape mode, false if not.
 */
fun Activity.isIrregularLandscape(): Boolean {
    return isLandscape(resources) &&
        !isSystemBarOnBottom(this)
}

/**
 * Check if the system bars are on the bottom.
 * @return If the system bars are on the bottom, false if no.
 */
@Suppress("DEPRECATION")
private fun isSystemBarOnBottom(activity: Activity): Boolean {
    val realPoint = Point()
    val metrics = DisplayMetrics()

    var width = 0
    var height = 0

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.display?.let { display ->
            display.getRealSize(realPoint)

            activity.windowManager.currentWindowMetrics.bounds.also {
                width = it.width()
                height = it.height()
            }
        }
    } else {
        (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).apply {
            defaultDisplay.getRealSize(realPoint)
            defaultDisplay.getMetrics(metrics)

            width = metrics.widthPixels
            height = metrics.heightPixels
        }
    }

    val config = activity.resources.configuration
    val canMove = (width != height && config.smallestScreenWidthDp < 600)

    return (!canMove || width < height)
}

// --- FRAGMENT NONSENSE ---

/**
 * Use reflection to fix a memory leak in the [Fragment] source code where the focused view will
 * never be cleared. I can't believe I have to do this.
 */
fun Fragment.fixAnimInfoLeak() {
    try {
        Fragment::class.java.getDeclaredMethod("setFocusedView", View::class.java).apply {
            isAccessible = true
            invoke(this@fixAnimInfoLeak, null)
        }
    } catch (e: Exception) {
        logE("mAnimationInfo leak fix failed.")
    }
}

/**
 * Shortcut for handling a file intent.
 * @return True if the file intent was pushed to [playbackModel], false if not
 */
fun Fragment.handleFileIntent(playbackModel: PlaybackViewModel): Boolean {
    val intent = requireActivity().intent

    if (intent != null && intent.action == Intent.ACTION_VIEW) {
        playbackModel.playWithIntent(intent, requireContext())

        return true
    }

    return false
}

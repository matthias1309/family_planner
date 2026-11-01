package de.familyplanner.android.core.ui.avatar

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.abs
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

private const val RENDER_SIZE_DP = 200
private const val MIN_OPAQUE_SHARE = 0.3
private const val SKIN_TOLERANCE = 12
private const val LOAD_TIMEOUT_MILLIS = 5_000L

/** Spike evidence for ADR-0017: renders the sample looks with AndroidSVG and writes PNGs to build/avatar-spike. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AvatarImageSpikeTest {
    @get:Rule
    val compose = createComposeRule()

    private val outDir = File("build/avatar-spike").apply { mkdirs() }

    @Test
    fun `REQ-GAME-030 free avatar renders with its skin color`() = renderAndCheck("free", SampleLooks.free, 0xEDB98A)

    @Test
    fun `REQ-GAME-030 hat avatar renders with its skin color`() = renderAndCheck("hat", SampleLooks.hat, 0xAE5D29)

    @Test
    fun `REQ-GAME-030 glasses and beard avatar renders with its skin color`() =
        renderAndCheck("glasses-beard", SampleLooks.glassesBeard, 0x614335)

    @Test
    fun `REQ-GAME-030 graphic shirt avatar renders with its skin color`() =
        renderAndCheck("graphic-shirt", SampleLooks.graphicShirt, 0xFFDBB4)

    private fun renderAndCheck(
        name: String,
        look: AvatarLook,
        skin: Int,
    ) {
        var bitmap: Bitmap? = null
        val millis =
            measureTimeMillis {
                compose.setContent { AvatarImage(look, name, Modifier.size(RENDER_SIZE_DP.dp), RENDER_SIZE_DP.dp) }
                compose.waitUntil(LOAD_TIMEOUT_MILLIS) {
                    AvatarDefinition.peek() != null &&
                        containsColor(compose.onRoot().captureToImage().asAndroidBitmap(), skin)
                }
                bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            }
        val captured = checkNotNull(bitmap)
        File(outDir, "$name.png").outputStream().use { captured.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("avatar-spike $name: ${millis}ms")
        assertTrue(opaqueShare(captured) > MIN_OPAQUE_SHARE, "$name is mostly empty")
        assertTrue(containsColor(captured, skin), "$name has no skin color")
    }

    private fun pixelsOf(bitmap: Bitmap): IntArray =
        IntArray(bitmap.width * bitmap.height).also {
            bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        }

    private fun opaqueShare(bitmap: Bitmap): Double {
        val pixels = pixelsOf(bitmap)
        return pixels.count { Color.alpha(it) > 0 }.toDouble() / pixels.size
    }

    private fun containsColor(
        bitmap: Bitmap,
        rgb: Int,
    ): Boolean {
        val (r, g, b) = Triple(rgb shr 16 and 0xFF, rgb shr 8 and 0xFF, rgb and 0xFF)
        return pixelsOf(bitmap).any {
            Color.alpha(it) > 0 &&
                abs(Color.red(it) - r) < SKIN_TOLERANCE &&
                abs(Color.green(it) - g) < SKIN_TOLERANCE &&
                abs(Color.blue(it) - b) < SKIN_TOLERANCE
        }
    }
}

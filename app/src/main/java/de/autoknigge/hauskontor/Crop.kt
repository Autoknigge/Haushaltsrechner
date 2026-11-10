package de.autoknigge.hauskontor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Foto verschieben und zoomen, bis nur die Ziffern im Rahmen stehen. Gelesen wird, was im Rahmen zu sehen ist. */
@Composable
fun CropDialog(bmp: Bitmap, onCancel: () -> Unit, onDone: (Bitmap?) -> Unit) {
    val img = remember(bmp) { bmp.asImageBitmap() }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }

    fun crop(): Bitmap? {
        if (frame.width == 0 || frame.height == 0) return null
        val s0 = min(frame.width.toFloat() / bmp.width, frame.height.toFloat() / bmp.height)
        val s = s0 * zoom
        val left = frame.width / 2f + off.x - bmp.width * s / 2f
        val top = frame.height / 2f + off.y - bmp.height * s / 2f
        val x0 = max(0f, -left / s)
        val y0 = max(0f, -top / s)
        val x1 = min(bmp.width.toFloat(), (frame.width - left) / s)
        val y1 = min(bmp.height.toFloat(), (frame.height - top) / s)
        val w = (x1 - x0).toInt()
        val h = (y1 - y0).toInt()
        if (w < 30 || h < 20) return null
        return Bitmap.createBitmap(bmp, x0.toInt(), y0.toInt(), w, h)
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = Forest, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Ziffern einrahmen", fontSize = 18.sp, color = Gold)
                Text("Mit zwei Fingern zoomen, mit einem verschieben. Nur der Zählerstand sollte im Rahmen stehen, möglichst groß und ohne Spiegelung.", fontSize = 13.sp, color = Ink)
                Canvas(
                    Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(10.dp)).border(2.dp, Gold, RoundedCornerShape(10.dp))
                        .onSizeChanged { frame = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, z, _ ->
                                zoom = (zoom * z).coerceIn(1f, 14f)
                                off += pan
                            }
                        }
                ) {
                    val s = min(size.width / bmp.width, size.height / bmp.height) * zoom
                    val dw = bmp.width * s
                    val dh = bmp.height * s
                    val left = size.width / 2f + off.x - dw / 2f
                    val top = size.height / 2f + off.y - dh / 2f
                    drawImage(
                        img, srcOffset = IntOffset.Zero, srcSize = IntSize(bmp.width, bmp.height),
                        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1))
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(modifier = Modifier.weight(1f), onClick = { onDone(crop()) }) { Text("Ausschnitt lesen", maxLines = 1) }
                    OutlinedButton(modifier = Modifier.weight(1f), onClick = { onDone(null) }) { Text("Ganzes Foto", maxLines = 1, color = Color.White) }
                }
                TextButton(onClick = onCancel) { Text("Abbrechen", color = Ink) }
            }
        }
    }
}

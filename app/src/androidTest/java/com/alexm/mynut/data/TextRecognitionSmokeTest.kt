package com.alexm.mynut.data

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextRecognitionSmokeTest {

    @Test
    fun recognizesTextOnLabelPhoto() = runTest {
        // androidTest/assets are bundled into the test APK (package com.alexm.mynut.test),
        // not the app-under-test APK — use the instrumentation context to read them
        // (see LlamaTextEngineTest for the same lesson learned in Task 5).
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val imageBytes = testContext.assets.open("label_sample.jpg").readBytes()
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text

        assertTrue("aucun texte détecté sur label_sample.jpg", text.isNotBlank())
    }
}

package com.example.colorsensor

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ColorPickerDialogFragment
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.View
import com.example.colorsensor.utils.PaintFinder
import android.view.MotionEvent
import java.util.*
import kotlinx.coroutines.*

class ColorChangerActivity : AppCompatActivity(), ColorPickerDialogFragment.OnColorSelectedListener {

    private lateinit var rgbValueText: TextView
    private lateinit var imageView: ImageView
    private lateinit var colorsButton: Button
    private lateinit var undoButton: Button
    private lateinit var resetButton: Button
    private lateinit var originalBitmap: Bitmap
    private lateinit var modifiedBitmap: Bitmap
    private lateinit var colorBox: View

    // Default selected color
    private var selectedColor: Int = Color.WHITE

    // Stack to store bitmap history for undo functionality
    private val bitmapHistory: Stack<Bitmap> = Stack()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.color_changer)

        // Initialize views
        rgbValueText = findViewById(R.id.rgbValueText)
        imageView = findViewById(R.id.imageView)
        colorsButton = findViewById(R.id.colorsButton)
        undoButton = findViewById(R.id.undoButton)
        resetButton = findViewById(R.id.resetButton)
        colorBox = findViewById(R.id.colorBox)

        // Retrieve the image URI from intent
        val imageUriString = intent.getStringExtra("image_uri")
        if (imageUriString == null) {
            Log.e("ColorChangerActivity", "No imageUri provided")
            finish()
            return
        }

        try {
            val imageUri = Uri.parse(imageUriString)
            val inputStream = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) {
                Log.e("ColorChangerActivity", "Failed to decode bitmap from URI")
                finish()
                return
            }

            originalBitmap = bitmap
            val config = originalBitmap.config ?: Bitmap.Config.ARGB_8888
            modifiedBitmap = originalBitmap.copy(config, true)
            imageView.setImageBitmap(modifiedBitmap)

        } catch (e: Exception) {
            Log.e("ColorChangerActivity", "Error loading image from URI", e)
            finish()
        }

        // Open color picker when button is clicked
        colorsButton.setOnClickListener {
            val dialog = ColorPickerDialogFragment()
            dialog.show(supportFragmentManager, "ColorPickerDialog")
        }

        // Detect taps on the image
        imageView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val imageMatrix = imageView.imageMatrix
                val drawable = imageView.drawable ?: return@setOnTouchListener true

                val inverse = android.graphics.Matrix()
                imageMatrix.invert(inverse)

                val touchPoint = floatArrayOf(event.x, event.y)
                inverse.mapPoints(touchPoint)

                val x = touchPoint[0].toInt()
                val y = touchPoint[1].toInt()

                if (x in 0 until modifiedBitmap.width && y in 0 until modifiedBitmap.height) {
                    val tappedColor = modifiedBitmap.getPixel(x, y)
                    Log.d("ColorChangerActivity", "Tapped Color: $tappedColor at ($x, $y)")

                    // Save the current bitmap before making changes for undo functionality
                    bitmapHistory.push(modifiedBitmap.copy(modifiedBitmap.config ?: Bitmap.Config.ARGB_8888, true))

                    // Replace similar pixels with selected color
                    modifiedBitmap = replaceColorInBitmap(modifiedBitmap, tappedColor, selectedColor)
                    imageView.setImageBitmap(modifiedBitmap)
                }
            }
            true
        }

        // Undo button
        undoButton.setOnClickListener {
            if (bitmapHistory.isNotEmpty()) {
                // Pop the last bitmap from history and set it back
                modifiedBitmap = bitmapHistory.pop()
                imageView.setImageBitmap(modifiedBitmap)
            }
        }

        // Reset button
        resetButton.setOnClickListener {
            modifiedBitmap = originalBitmap.copy(originalBitmap.config ?: Bitmap.Config.ARGB_8888, true)
            imageView.setImageBitmap(modifiedBitmap)
            bitmapHistory.clear() // Clear history since we reset to original
        }
    }

    override fun onColorSelected(color: Int) {
        selectedColor = color // Store selected color
        colorBox.setBackgroundColor(color)

        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)

        val targetColor = PaintFinder.PaintColor("Selected", "Current", r, g, b)
        val closestPaint = PaintFinder.findClosestPaint(targetColor, this)

        rgbValueText.text = if (closestPaint != null) {
            "Closest Paint: ${closestPaint.name}"
        } else {
            "No close match found"
        }

        updateColorInfo(color, colorBox)
    }

    // Determines if two colors are similar within a given tolerance
    private fun isColorSimilar(color1: Int, color2: Int, tolerance: Int): Boolean {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)

        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)

        return (Math.abs(r1 - r2) < tolerance &&
                Math.abs(g1 - g2) < tolerance &&
                Math.abs(b1 - b2) < tolerance)
    }

    // Replaces pixels similar to targetColor with newColor
    private fun replaceColorInBitmap(
        bitmap: Bitmap,
        targetColor: Int,
        newColor: Int,
        opacity: Int = 200,
        tolerance: Int = 80
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val newBitmap = Bitmap.createBitmap(width, height, bitmap.config ?: Bitmap.Config.ARGB_8888)

        // We will process the pixels in blocks (rows) to speed up the operation
        val pixels = IntArray(width)
        for (y in 0 until height) {
            // Get pixels for the current row (this is faster than getting one pixel at a time)
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1)

            for (x in 0 until width) {
                val pixelColor = pixels[x]

                if (isColorSimilar(pixelColor, targetColor, tolerance)) {
                    // Extract RGB values from the new color
                    val newRed = Color.red(newColor)
                    val newGreen = Color.green(newColor)
                    val newBlue = Color.blue(newColor)

                    // Modify the new color's alpha (opacity) value
                    val newAlpha = opacity // Opacity is set between 0 and 255 (fully transparent to fully opaque)

                    // Get the original pixel's alpha and blend with the new opacity
                    val originalAlpha = Color.alpha(pixelColor)

                    // Soft blending of the colors
                    val blendedAlpha = ((originalAlpha * (255 - opacity)) + (newAlpha * opacity)) / 255

                    // Gradual blending of colors (this is key to make it look realistic)
                    val blendedRed = (Color.red(pixelColor) * (255 - opacity) + newRed * opacity) / 255
                    val blendedGreen = (Color.green(pixelColor) * (255 - opacity) + newGreen * opacity) / 255
                    val blendedBlue = (Color.blue(pixelColor) * (255 - opacity) + newBlue * opacity) / 255

                    // Create the new blended color
                    val blendedColor = Color.argb(blendedAlpha, blendedRed, blendedGreen, blendedBlue)

                    // Set the blended color to the pixel
                    pixels[x] = blendedColor
                }
            }

            // Set the modified row of pixels to the new bitmap
            newBitmap.setPixels(pixels, 0, width, 0, y, width, 1)
        }
        return newBitmap
    }


    private fun updateColorInfo(
        color: Int,
        colorBox: View
    ) {
        // Extract RGB values from the color
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        // Using the RGB values to search the database
        val targetColor = PaintFinder.PaintColor("", "", red, green, blue)
        val closestPaint = PaintFinder.findClosestPaint(targetColor, this)

        // Set the XML values to the correct paint and RGB when found
        if (closestPaint != null) {
            val closestPaintColor = Color.rgb(closestPaint.r, closestPaint.g, closestPaint.b)
            colorBox.setBackgroundColor(closestPaintColor)

            // Make the box clickable and route to PaintInfoActivity
            colorBox.setOnClickListener {
                val intent = Intent(this, PaintInfoActivity::class.java)
                // Pass the paint color and name
                intent.putExtra("selected_color", closestPaintColor)
                intent.putExtra("color_name", closestPaint.name)
                startActivity(intent)
            }
        }
    }
}

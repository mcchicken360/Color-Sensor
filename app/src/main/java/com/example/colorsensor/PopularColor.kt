package com.example.colorsensor

import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Magnifier
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

//testing
class PopularColor : AppCompatActivity() {
    private var magnifier: Magnifier? = null
    private var isMagnifierActive = false // Track magnifier state
    var favColors : MutableList<String> = mutableListOf()
    private lateinit var firestore: FirebaseFirestore
    var selectedFav = ""

    // Create a magnifier tutorial
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.popular_color) // Ensure correct layout file

        val imageView: ImageView = findViewById(R.id.imageView2)
        val hexMessage = findViewById<TextView>(R.id.textView9)
        val textRGB = findViewById<TextView>(R.id.textView11)
        val zoomButton: Button = findViewById(R.id.button) // Button to toggle magnifier
        // Load the original bitmap
        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.blank_wall)
        // Define the region to change (xStart, yStart, width, height)
        val targetRegion = Rect(0, 125, 1228, 809) // Adjust these values as needed

        // Create a magnifier tutorial
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            magnifier = Magnifier.Builder(imageView)
                .setInitialZoom(2.3f)
                .setElevation(10.0f)
                .setCornerRadius(20.0f)
                .setSize(100, 100)
                .build()
        }

        // Toggle magnifier on button press
        zoomButton.setOnClickListener {
            isMagnifierActive = !isMagnifierActive // Toggle state

            if (isMagnifierActive) {
                zoomButton.text = "Disable Magnifier"
            } else {
                zoomButton.text = "Enable Magnifier"
                magnifier?.dismiss()
            }
        }

        // Move the magnifier while touching the screen
        imageView.setOnTouchListener { _, event ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isMagnifierActive) {
                when (event.action) {
                    MotionEvent.ACTION_MOVE, MotionEvent.ACTION_DOWN -> {
                        magnifier?.show(event.rawX, event.y)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        magnifier?.dismiss()
                    }
                }
            }
            true
        }

        // Set random colors for 25 buttons
        for (i in 1..25) {
            val newRed = Random.nextInt(0, 256)
            val newGreen = Random.nextInt(0, 256)
            val newBlue = Random.nextInt(0, 256)


            val firestore = FirebaseFirestore.getInstance()

            // get all favorite color
            firestore.collection("users")
                .get()
                .addOnSuccessListener { documents ->
                    // store favorite color in a list
                    val allFavoriteColors = mutableListOf<String>()

                    for (document in documents) {
                        val favoriteColors = document.get("favoriteColors") as? List<String>
                        if (favoriteColors != null) {
                            allFavoriteColors.addAll(favoriteColors)
                        }
                    }

                    if (allFavoriteColors.isNotEmpty()) {
                        // Remove duplicates and join into a string to display
                        val uniqueColors = allFavoriteColors.toSet().joinToString(", ")
                        Toast.makeText(this, "All Favorite Colors: $uniqueColors", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "No favorite colors found", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("Firestore", "Error getting favorite colors: ", exception)
                    Toast.makeText(this, "Failed to fetch favorite colors", Toast.LENGTH_SHORT).show()
                }

            // Create a random color
            val color = Color.argb(255, newRed, newGreen, newBlue)

            // Find the view by its ID and set the background color
            val resID = resources.getIdentifier("button$i", "id", packageName)
            findViewById<Button>(resID)?.setBackgroundColor(color)
        }

        // Handle color selection from buttons
        for (i in 1..25) {
            val buttonId = resources.getIdentifier("button$i", "id", packageName)
            val button = findViewById<Button>(buttonId)
            // press any 1 - 25 button
            button?.setOnClickListener {
                val backgroundColor = (button.background as ColorDrawable).color
                val colorHex = String.format("#%06X", 0xFFFFFF and backgroundColor)
                val viewColor: View = findViewById(R.id.viewColor11)

                // Set the TextView text to show the button's background color
                hexMessage.text = "Hex: $colorHex"
                // Get rgb value
                val red = Color.red(backgroundColor)
                val green = Color.green(backgroundColor)
                val blue = Color.blue(backgroundColor)
                textRGB.text = "RGB: ($red, $green, $blue)"

                viewColor.setBackgroundColor(Color.argb(Color.alpha(backgroundColor), red, green, blue))

                // CHANGE WALL COLOR
                val newColor = Color.parseColor(colorHex)
                val modifiedBitmap = fillRegionWithColor(bitmap, targetRegion, newColor)
                imageView.setImageBitmap(modifiedBitmap)
            }
        }
    }

    // Function to fill a specific region with a new color
    fun fillRegionWithColor(bitmap: Bitmap, region: Rect, newColor: Int): Bitmap {
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        for (x in region.left until region.right) {
            for (y in region.top until region.bottom) {
                mutableBitmap.setPixel(x, y, newColor)
            }
        }

        return mutableBitmap
    }

    // Data class for defining a rectangular area
    data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun displayColors(favColorContainer : LinearLayout){
        favColorContainer.removeAllViews()
        for (color in favColors){
            val textView = TextView(this)
            textView.text = color
            firestore.collection("paints")
                .whereEqualTo("name", color)  // Query by username
                .get()
                .addOnSuccessListener { paints ->
                    if (!paints.isEmpty) {
                        for(paint in paints){
                            val hex = paint.get("hex") as String
                            val rgbInfo = hex.removePrefix("rgb(").removeSuffix(")").split(",")
                            val red = rgbInfo[0].trim().toInt()
                            val green = rgbInfo[1].trim().toInt()
                            val blue = rgbInfo[2].trim().toInt()

                            textView.setBackgroundColor(Color.rgb(red,green,blue))
                        }
                    }
                }
        }
    }
}

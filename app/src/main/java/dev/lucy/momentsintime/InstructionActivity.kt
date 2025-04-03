package dev.lucy.momentsintime

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.viewpager2.widget.ViewPager2
import java.time.LocalDate

class InstructionActivity : AppCompatActivity() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var startExperimentButton: Button
    private lateinit var pageIndicatorLayout: LinearLayout
    
    private val instructionPages = listOf(
        "Welcome to the Brain Recording Experiment!\n\n" +
        "This experiment will help us understand how the brain processes visual information.\n\n" +
        "Please follow all instructions carefully.",
        
        "During the experiment, you will watch a series of short videos.\n\n" +
        "After each video, you will be asked to describe what you saw.\n\n" +
        "Speak clearly and naturally when describing the videos.",
        
        "The experiment consists of 3 blocks with 5 trials each.\n\n" +
        "You will have 3 seconds to respond after each video.\n\n" +
        "When you're ready, press the Start button to begin the experiment."
    )
    
    private var participantId: Int = -1
    private var dateString: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_instruction)
        
        participantId = intent.getIntExtra("PARTICIPANT_ID", -1)
        dateString = intent.getStringExtra("DATE") ?: LocalDate.now().toString()
        
        // Initialize views
        viewPager = findViewById(R.id.instructionViewPager)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)
        startExperimentButton = findViewById(R.id.startExperimentButton)
        pageIndicatorLayout = findViewById(R.id.pageIndicator)
        
        // Set up the adapter
        val adapter = InstructionPagerAdapter(instructionPages)
        viewPager.adapter = adapter
        
        // Set up page indicator dots
        setupPageIndicator()
        
        // Set up button click listeners
        setupButtonListeners()
        
        // Set up page change callback
        setupPageChangeCallback()
    }
    
    private fun setupPageIndicator() {
        // Create indicator dots
        for (i in instructionPages.indices) {
            val dot = ImageView(this)
            dot.setImageDrawable(
                ContextCompat.getDrawable(
                    this,
                    if (i == 0) android.R.drawable.ic_menu_more else android.R.drawable.ic_menu_close_clear_cancel
                )
            )
            
            // Set layout parameters
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(8, 0, 8, 0)
            dot.layoutParams = params
            
            // Add to layout
            pageIndicatorLayout.addView(dot)
        }
    }
    
    private fun setupButtonListeners() {
        previousButton.setOnClickListener {
            val currentItem = viewPager.currentItem
            if (currentItem > 0) {
                viewPager.currentItem = currentItem - 1
            }
        }
        
        nextButton.setOnClickListener {
            val currentItem = viewPager.currentItem
            if (currentItem < instructionPages.size - 1) {
                viewPager.currentItem = currentItem + 1
            }
        }
        
        startExperimentButton.setOnClickListener {
            navigateToExperiment()
        }
    }
    
    private fun setupPageChangeCallback() {
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageIndicator(position)
                updateButtonVisibility(position)
            }
        })
    }
    
    private fun updatePageIndicator(position: Int) {
        for (i in 0 until pageIndicatorLayout.childCount) {
            val dot = pageIndicatorLayout.getChildAt(i) as ImageView
            dot.setImageDrawable(
                ContextCompat.getDrawable(
                    this@InstructionActivity,
                    if (i == position) android.R.drawable.ic_menu_more else android.R.drawable.ic_menu_close_clear_cancel
                )
            )
        }
    }
    
    private fun updateButtonVisibility(position: Int) {
        // Show/hide Previous button
        previousButton.visibility = if (position > 0) View.VISIBLE else View.INVISIBLE
        
        // Show/hide Next and Start buttons
        if (position == instructionPages.size - 1) {
            nextButton.visibility = View.GONE
            startExperimentButton.visibility = View.VISIBLE
        } else {
            nextButton.visibility = View.VISIBLE
            startExperimentButton.visibility = View.GONE
        }
    }
    
    private fun navigateToExperiment() {
        val intent = Intent(this, ExperimentActivity::class.java).apply {
            putExtra("PARTICIPANT_ID", participantId)
            putExtra("DATE", dateString)
        }
        startActivity(intent)
        finish() // Close this activity for a smooth transition
    }
}

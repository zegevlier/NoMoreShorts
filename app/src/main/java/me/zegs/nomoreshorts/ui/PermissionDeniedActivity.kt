package me.zegs.nomoreshorts.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import me.zegs.nomoreshorts.R

class PermissionDeniedActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission_denied)

        setupViews()
    }

    private fun setupViews() {
        val titleText = findViewById<TextView>(R.id.titleTextDenied)
        val descriptionText = findViewById<TextView>(R.id.descriptionTextDenied)
        val tryAgainButton = findViewById<Button>(R.id.tryAgainButton)

        titleText.text = getString(R.string.permission_denied_title)
        descriptionText.text = getString(R.string.permission_denied_description)
        tryAgainButton.text = getString(R.string.try_again)

        tryAgainButton.setOnClickListener {
            // Go back to the permission request screen
            val intent = Intent(this, PermissionRequestActivity::class.java)
            startActivity(intent)
            finish()
        }

        supportActionBar?.title = getString(R.string.permission_denied_title)
    }
}

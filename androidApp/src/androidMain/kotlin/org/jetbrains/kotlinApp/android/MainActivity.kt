package org.jetbrains.kotlinApp.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.jetbrains.kotlinApp.R
import org.jetbrains.kotlinApp.App
import org.jetbrains.kotlinApp.ApplicationContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = ApplicationContext(
            application,
            R.mipmap.ic_launcher,
        )

        setContent {
            App(context)
        }
    }
}

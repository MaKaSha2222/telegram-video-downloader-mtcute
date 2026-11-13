package space.manus.tgvidapp.t145521605804137

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }
}

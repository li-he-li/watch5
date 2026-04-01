package com.heartrate.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.heartrate.phone.data.PhoneBleRelayController
import com.heartrate.phone.data.PhoneWebSocketRelayController
import com.heartrate.phone.data.persistence.HeartRateDao
import com.heartrate.phone.data.persistence.HeartRateExportManager
import com.heartrate.phone.ui.PhoneApp
import com.heartrate.shared.presentation.viewmodel.HeartRateViewModel
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private val sharedViewModel: HeartRateViewModel by inject()
    private val bleRelayController: PhoneBleRelayController by inject()
    private val webSocketRelayController: PhoneWebSocketRelayController by inject()
    private val heartRateDao: HeartRateDao by inject()
    private val exportManager: HeartRateExportManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PhoneApp(
                    viewModel = sharedViewModel,
                    bleRelayController = bleRelayController,
                    webSocketRelayController = webSocketRelayController,
                    heartRateDao = heartRateDao,
                    exportManager = exportManager
                )
            }
        }
    }

    override fun onDestroy() {
        sharedViewModel.detachUi()
        super.onDestroy()
    }
}

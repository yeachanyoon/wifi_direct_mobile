package com.example.wifi_direct_moblie
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.widget.Toast

class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val activity: MainActivity // UI 업데이트 등을 위해 Activity 참조
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Wi-Fi P2P 기능 On/Off 상태 변경
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    Toast.makeText(context, "Wi-Fi Direct 켜짐", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Wi-Fi Direct 꺼짐", Toast.LENGTH_SHORT).show()
                }
            }

            // [중요] 주변 피어 목록 변경
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                // 피어 목록이 변경됨. 실제 목록은 manager.requestPeers()로 요청해야 함
                // (5단계에서 자세히 다룸)
                activity.requestPeers()
            }

            // [중요] Wi-Fi P2P 연결 상태 변경
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                // 연결 상태가 변경됨
                // (7단계에서 자세히 다룸)
                manager.requestConnectionInfo(channel, activity) // 연결 정보 요청
            }

            // 이 기기의 P2P 정보 변경
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                // ...
            }
        }
    }
}
package com.example.wifi_direct_moblie
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.widget.Toast
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDevice

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
                    activity.updateWifiDirectStatus(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
                } else {
                    Toast.makeText(context, "Wi-Fi Direct 꺼짐", Toast.LENGTH_SHORT).show()
                    activity.updateWifiDirectStatus(state == WifiP2pManager.WIFI_P2P_STATE_ENABLED)
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
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)

                if (networkInfo?.isConnected == true) {
                    // 연결됨 -> MainActivity에 연결 정보 요청
                    manager.requestConnectionInfo(channel, activity)
                } else {
                    // 연결 끊김 -> MainActivity UI 초기화
                    activity.clearConnectionInfo()
                }
            }

            // 이 기기의 P2P 정보 변경
            // [!] 이 기기의 P2P 정보 변경 (이 케이스 추가)
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> { // intentFilter에 이미 있음
                val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                if (device != null) {
                    // MainActivity에 이 기기의 MAC 주소 전달
                    activity.setMyDeviceAddress(device)
                }
        }
     }
    }
}
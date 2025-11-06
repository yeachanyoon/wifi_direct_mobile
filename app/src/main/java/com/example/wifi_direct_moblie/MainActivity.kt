package com.example.wifi_direct_moblie

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build // 같은 번들 임
import androidx.core.app.ActivityCompat
//api 사용
import android.content.Context
import android.os.Looper
import android.content.IntentFilter
//리시버
import android.content.BroadcastReceiver
import android.content.Intent
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.*
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService


class MainActivity : AppCompatActivity(), WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener {
    lateinit var manager: WifiP2pManager
    lateinit var channel: WifiP2pManager.Channel
    private val intentFilter = IntentFilter()
    lateinit var receiver: WiFiDirectBroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Android 13 (TIRAMISU) 이상 런타임 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                != PackageManager.PERMISSION_GRANTED
            ) {

                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES),
                    1 // 요청 코드
                )

        }
            // P2P 매니저와 채널 초기화
            manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
            channel = manager.initialize(this, Looper.getMainLooper(), null)
        }

        // 4단계에서 만든 리시버 클래스 초기화
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)

        // 리시버가 수신할 인텐트 필터 설정
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        // [UI] 버튼 클릭 시 피어 탐색 시작 (activity_main.xml에 버튼 추가 필요)
        val discoverButton = findViewById<Button>(R.id.discoverButton) // 예시 ID
        discoverButton.setOnClickListener {
            startPeerDiscovery()
        }
    }
    // 피어 탐색 시작
    private fun startPeerDiscovery() {
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(this@MainActivity, "주변 기기 탐색 시작", Toast.LENGTH_SHORT).show()
            }
            override fun onFailure(reason: Int) {
                Toast.makeText(this@MainActivity, "탐색 실패: $reason", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // [중요] 리시버 등록 및 해제 (Activity 생명주기)
    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
    }

    // [중요] 4단계의 리시버가 호출할 메소드
    fun requestPeers() {
        manager.requestPeers(channel, this) // 'this'가 PeerListListener
    }

    // [중요] 피어 목록 콜백
    override fun onPeersAvailable(peers: WifiP2pDeviceList) {
        // !! 이곳으로 탐색된 기기 목록(peers)이 들어옵니다 !!
        // 이 peers.deviceList를 UI(예: RecyclerView)에 표시합니다.
        // 이 튜토리얼에서는 로그로 출력합니다.
        Log.d("WiFiDirect", "발견된 피어 수: ${peers.deviceList.size}")
        peers.deviceList.forEach { device ->
            Log.d("WiFiDirect", " - ${device.deviceName} (${device.deviceAddress})")
        }
    }

    fun connectToPeer(device: WifiP2pDevice) {
        val config = WifiP2pConfig()
        config.deviceAddress = device.deviceAddress
        config.wps.setup = WpsInfo.PBC

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // 연결 요청 성공 (실제 연결은 리시버가 알려줌)
                Toast.makeText(this@MainActivity, "${device.deviceName}에 연결 요청", Toast.LENGTH_SHORT).show()
            }
            override fun onFailure(reason: Int) {
                Toast.makeText(this@MainActivity, "연결 요청 실패: $reason", Toast.LENGTH_SHORT).show()
            }
        })
    }
    override fun onConnectionInfoAvailable(info: WifiP2pInfo) {

        // 그룹 오너(서버)의 IP 주소
        val groupOwnerAddress = info.groupOwnerAddress.hostAddress

        if (info.groupFormed && info.isGroupOwner) {
            // [역할: 서버 (Group Owner)]
            // 이 기기가 그룹 오너(서버)입니다.
            // ServerSocket을 열고 클라이언트의 연결을 기다립니다.
            Log.d("WiFiDirect", "나는 그룹 오너 (서버) 입니다.")
            // 예: startServerTask()

        } else if (info.groupFormed) {
            // [역할: 클라이언트]
            // 이 기기가 클라이언트입니다.
            // 알아낸 groupOwnerAddress (서버 IP)로 Socket 연결을 시도합니다.
            Log.d("WiFiDirect", "나는 클라이언트 입니다. 서버 IP: $groupOwnerAddress")
            // 예: startClientSocketTask(groupOwnerAddress)
        }
    }
}
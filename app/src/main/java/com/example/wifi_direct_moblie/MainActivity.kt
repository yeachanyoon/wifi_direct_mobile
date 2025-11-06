package com.example.wifi_direct_moblie

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import android.content.Context
import android.os.Looper
import android.content.IntentFilter
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.*
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
// import androidx.core.content.ContextCompat.getSystemService // 원본 파일에 있었으나 사용되지 않음

class MainActivity : AppCompatActivity(), WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener {
    lateinit var manager: WifiP2pManager
    lateinit var channel: WifiP2pManager.Channel
    private val intentFilter = IntentFilter()
    lateinit var receiver: WiFiDirectBroadcastReceiver
    // --- [ 1. 변수 추가 ] ---
    lateinit var peerListView: ListView
    lateinit var listAdapter: ArrayAdapter<String>

    private val peerDeviceList = mutableListOf<WifiP2pDevice>() // 기기 원본 목록
    private val peerDeviceNames = mutableListOf<String>()      // 기기 이름 목록 (UI 표시용)
    // --- [ 변수 추가 끝 ] ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // --- [수정된 부분 1: 모든 초기화 코드를 IF문 밖으로 이동] ---
        // P2P 매니저와 채널 초기화
        manager = getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, Looper.getMainLooper(), null)

        // 리시버 클래스 초기화
        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)

        // 리시버가 수신할 인텐트 필터 설정
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        // 버튼 리스너 설정
        val discoverButton = findViewById<Button>(R.id.discoverButton)
        discoverButton.setOnClickListener {
            startPeerDiscovery()
        }
        // --- [수정 완료] ---

        // --- [ 2. ListView 및 어댑터 초기화 ] ---
        peerListView = findViewById(R.id.peerListView)
        // 간단한 기본 리스트 아이템 템플릿 사용
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, peerDeviceNames)
        peerListView.adapter = listAdapter

        // [!] 리스트 뷰 항목 클릭 리스너 설정
        peerListView.setOnItemClickListener { parent, view, position, id ->
            // 사용자가 클릭한 위치(position)의 기기를 가져옴
            val selectedDevice = peerDeviceList[position]
            // 해당 기기에 연결 시도
            connectToPeer(selectedDevice)
        }
        // Android 13 (TIRAMISU) 이상 런타임 권한 요청 (권한 요청만 IF문 안에 둠)
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
        }
    } // [END OF onCreate]

    // 피어 탐색 시작
    private fun startPeerDiscovery() {
        // 참고: 권한이 거부된 상태에서 이 버튼을 누르면 discoverPeers가 실패(onFailure)할 수 있습니다.
        // 튜토리얼이므로 이 부분의 상세한 권한 처리는 생략합니다.
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Toast.makeText(this@MainActivity, "주변 기기 탐색 시작", Toast.LENGTH_SHORT).show()
                }

                override fun onFailure(reason: Int) {
                    Toast.makeText(this@MainActivity, "탐색 실패: $reason", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            Toast.makeText(this, "NEARBY_WIFI_DEVICES 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
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
        // 권한 체크 추가
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            manager.requestPeers(channel, this) // 'this'가 PeerListListener
        }
    }


    // [중요] 피어 목록 콜백
    override fun onPeersAvailable(peers: WifiP2pDeviceList) {
        // 기존 목록 초기화
        peerDeviceList.clear()
        peerDeviceNames.clear()

        // 새로 받은 기기 목록(peers)으로 리스트 채우기
        peerDeviceList.addAll(peers.deviceList)
        peers.deviceList.forEach { device ->
            peerDeviceNames.add(device.deviceName ?: "Unknown Device") // 이름이 없으면 "Unknown Device"
        }

        // [!] 어댑터에 데이터가 변경되었음을 알림 (UI 갱신)
        listAdapter.notifyDataSetChanged()
        // !! 이곳으로 탐색된 기기 목록(peers)이 들어옵니다 !!
        Log.d("WiFiDirect", "발견된 피어 수: ${peers.deviceList.size}")
        peers.deviceList.forEach { device ->
            Log.d("WiFiDirect", " - ${device.deviceName} (${device.deviceAddress})")
        }
        // TODO: 여기서 peers.deviceList를 UI(RecyclerView)에 표시하고
        // 사용자가 클릭하면 connectToPeer(device)를 호출해야 합니다.
    }

    fun connectToPeer(device: WifiP2pDevice) {
        val config = WifiP2pConfig()
        config.deviceAddress = device.deviceAddress
        config.wps.setup = WpsInfo.PBC

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    // 연결 요청 성공 (실제 연결은 리시버가 알려줌)
                    Toast.makeText(
                        this@MainActivity,
                        "${device.deviceName}에 연결 요청",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onFailure(reason: Int) {
                    Toast.makeText(
                        this@MainActivity,
                        "연결 요청 실패: $reason",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        } else {
            Toast.makeText(this, "NEARBY_WIFI_DEVICES 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onConnectionInfoAvailable(info: WifiP2pInfo) {

        // 그룹 오너(서버)의 IP 주소
        val groupOwnerAddress = info.groupOwnerAddress.hostAddress

        if (info.groupFormed && info.isGroupOwner) {
            // [역할: 서버 (Group Owner)]
            Log.d("WiFiDirect", "나는 그룹 오너 (서버) 입니다.")
            // 예: startServerTask()

        } else if (info.groupFormed) {
            // [역할: 클라이언트]
            Log.d("WiFiDirect", "나는 클라이언트 입니다. 서버 IP: $groupOwnerAddress")
            // 예: startClientSocketTask(groupOwnerAddress)
        }
    }
} // <-- [!] 원본 파일에 있던 불필요한 } 제거됨
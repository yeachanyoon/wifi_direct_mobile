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
import android.widget.*
import android.widget.ImageView // [!] import 추가
import androidx.activity.result.contract.ActivityResultContracts // [!] import 추가
import com.google.zxing.BarcodeFormat // [!] import 추가
import com.google.zxing.MultiFormatWriter // [!] import 추가
import com.journeyapps.barcodescanner.ScanContract // [!] import 추가
import com.journeyapps.barcodescanner.ScanOptions // [!] import 추가
import android.graphics.Bitmap // [!] import 추가
import android.net.ConnectivityManager // [!] import 추가
import android.net.Network // [!] import 추가
import android.net.NetworkCapabilities // [!] import 추가
import android.net.NetworkRequest // [!] import 추가
import android.net.wifi.WifiNetworkSpecifier // [!] import 추가
// import androidx.core.content.ContextCompat.getSystemService // 원본 파일에 있었으나 사용되지 않음

class MainActivity : AppCompatActivity(), WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener, WifiP2pManager.GroupInfoListener {
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
    // [!] 1. TextView 변수 추가
    lateinit var statusTextView: TextView
    lateinit var connectionInfoTextView: TextView
    lateinit var qrCodeImageView: ImageView //이미지
    lateinit var emptyView: TextView


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
        // --- [!] QR 버튼 리스너 추가 ---
        val createGroupButton = findViewById<Button>(R.id.createGroupButton)
        createGroupButton.setOnClickListener {
            createGroup()
        }

        val scanQRButton = findViewById<Button>(R.id.scanQRButton)
        scanQRButton.setOnClickListener {
            startQRScanner()
        }
        // --- [ 리스너 추가 끝 ] ---
        // --- [ 2. ListView 및 어댑터 초기화 ] ---
        peerListView = findViewById(R.id.peerListView)
        // 간단한 기본 리스트 아이템 템플릿 사용
        listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, peerDeviceNames)
        peerListView.adapter = listAdapter
        val emptyTextView = findViewById<TextView>(R.id.emptyView)
        // [!] ListView에 emptyView를 설정합니다.
        peerListView.emptyView = emptyTextView
        // [!] 리스트 뷰 항목 클릭 리스너 설정
        peerListView.setOnItemClickListener { parent, view, position, id ->
            // 사용자가 클릭한 위치(position)의 기기를 가져옴
            val selectedDevice = peerDeviceList[position]
            // 해당 기기에 연결 시도
            connectToPeer(selectedDevice)
        }
        // [!] 2. TextView 초기화
        statusTextView = findViewById(R.id.statusTextView)
        connectionInfoTextView = findViewById(R.id.connectionInfoTextView)
        qrCodeImageView = findViewById(R.id.qrCodeImageView) // [!] 추가
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
            connectionInfoTextView.text = "Role: Server (Group Owner)" // [!] 텍스트 업데이트
            // 예: startServerTask()

        } else if (info.groupFormed) {
            // [역할: 클라이언트]
            Log.d("WiFiDirect", "나는 클라이언트 입니다. 서버 IP: $groupOwnerAddress")
            connectionInfoTextView.text = "Role: Client\nServer IP: $groupOwnerAddress" // [!] 텍스트 업데이트
            // 예: startClientSocketTask(groupOwnerAddress)
        }
    }

    /** Wi-Fi Direct 활성화 상태 텍스트 업데이트 */
    fun updateWifiDirectStatus(isEnabled: Boolean) {
        statusTextView.text = if (isEnabled) {
            "Wi-Fi Direct: Enabled"
        } else {
            "Wi-Fi Direct: Disabled"
        }
    }

    /** 연결 정보 텍스트 초기화 (연결 끊겼을 때) */
    fun clearConnectionInfo() {
        connectionInfoTextView.text = "Connection: None"
    }
    // --- [!] 4. QR 코드 관련 변수 및 함수들 추가 ---

    // [!] QR 스캐너 실행 및 결과 처리를 위한 런처
    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            // QR 코드 스캔 성공
            val scannedData = result.contents
            Log.d("WiFiDirect", "Scanned data: $scannedData")

            // "WIFI:T:WPA;S:SSID;P:PASSWORD;;" 형식 파싱 시도
            try {
                // S:(SSID) 와 P:(PASSWORD) 사이의 값을 추출
                val ssid = scannedData.substringAfter("S:").substringBefore(";")
                val password = scannedData.substringAfter("P:").substringBefore(";")

                if (ssid.isEmpty() || password.isEmpty()) {
                    throw Exception("Invalid format")
                }

                Toast.makeText(this, "Connecting to $ssid...", Toast.LENGTH_SHORT).show()
                // 5단계: 스캔한 정보로 Wi-Fi Direct 그룹에 연결
                connectToWifiDirectGroup(ssid, password)

            } catch (e: Exception) {
                Log.e("WiFiDirect", "Invalid QR Code format: $scannedData", e)
                Toast.makeText(this, "Invalid QR Code format", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Scan cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    /** [수신자] 1. P2P 그룹 생성 요청 (QR 생성 버튼 클릭 시) */
    private fun createGroup() {
        // API 33 이상은 NEARBY_WIFI_DEVICES, 미만은 ACCESS_FINE_LOCATION 권한이 필요할 수 있으나,
        // 여기서는 기존 코드의 권한 체크 로직을 재사용합니다.
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        ) {
            // 기존 연결이 있다면 해제
            manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("WiFiDirect", "Previous group removed. Creating new group.")
                    // 그룹 생성 시작
                    startGroupCreation()
                }
                override fun onFailure(reason: Int) {
                    Log.d("WiFiDirect", "Previous group removal failed ($reason). Creating new group anyway.")
                    // 실패해도 새 그룹 생성 시도
                    startGroupCreation()
                }
            })
        } else {
            Toast.makeText(this, "NEARBY_WIFI_DEVICES 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    /** [수신자] 1-1. 실제 그룹 생성 로직 */
    private fun startGroupCreation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            Toast.makeText(this, "권한이 없어 그룹을 생성할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        manager.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Toast.makeText(this@MainActivity, "Group creation started...", Toast.LENGTH_SHORT).show()
                // 성공 시 GroupInfoListener(onGroupInfoAvailable)가 호출되길 기다림
                // [!] 중요: createGroup() 성공 직후가 아니라,
                // WIFI_P2P_CONNECTION_CHANGED_ACTION 브로드캐스트 수신 후
                // requestConnectionInfo 또는 requestGroupInfo를 호출해야 합니다.
                // (WiFiDirectBroadcastReceiver에서 처리)
            }
            override fun onFailure(reason: Int) {
                Toast.makeText(this@MainActivity, "Group creation failed: $reason", Toast.LENGTH_SHORT).show()
            }
        })
    }


    /** [수신자] 2. 그룹 정보 콜백 (BroadcastReceiver가 requestGroupInfo() 호출 시) */
    override fun onGroupInfoAvailable(group: WifiP2pGroup?) {
        if (group != null) {
            val ssid = group.networkName
            val passphrase = group.passphrase
            Log.d("WiFiDirect", "Group Info Available. SSID: $ssid, Pass: $passphrase")

            // 3. QR 코드 생성
            val qrData = "WIFI:T:WPA;S:$ssid;P:$passphrase;;"
            generateQRCode(qrData)

            // UI 업데이트 (서버 역할)
            connectionInfoTextView.text = "Role: Server (Group Owner)\nSSID: $ssid"

            // TODO: 여기서 startServerTask() (서버 소켓 열기)를 호출해야 합니다.

        } else {
            Log.d("WiFiDirect", "Group Info is null")
        }
    }

    /** [수신자] 3. QR 코드 비트맵 생성 및 표시 */
    private fun generateQRCode(text: String) {
        try {
            val writer = MultiFormatWriter()
            val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 250, 250)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                }
            }
            qrCodeImageView.setImageBitmap(bmp)
            qrCodeImageView.visibility = ImageView.VISIBLE // QR 코드 보여주기
            peerListView.visibility = ListView.GONE // 목록 숨기기
            emptyView.visibility = TextView.GONE // 빈 텍스트 숨기기
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show()
        }
    }

    /** [송신자] 1. QR 스캐너 시작 */
    private fun startQRScanner() {
        // QR 스캔 시 기존 연결/그룹 해제
        manager.removeGroup(channel, null)

        val options = ScanOptions()
        options.setPrompt("Scan a Wi-Fi Direct QR Code")
        options.setBeepEnabled(true)
        options.setOrientationLocked(false)
        qrScannerLauncher.launch(options)
    }

    /** [송신자] 2. (Android 10 이상) 스캔한 정보로 네트워크 연결 */
    private fun connectToWifiDirectGroup(ssid: String, passphrase: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(passphrase)
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .setNetworkSpecifier(specifier)
                .build()

            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    Log.d("WiFiDirect", "Network connected! $network")
                    // [!] 연결 성공! 이 네트워크(GO)에 소켓 연결 시도
                    // GO의 IP는 거의 항상 192.168.49.1 입니다.
                    runOnUiThread {
                        connectionInfoTextView.text = "Role: Client\nConnected to GO: $ssid"
                        // TODO: startClientSocketTask("192.168.49.1")
                    }
                }
                override fun onUnavailable() {
                    super.onUnavailable()
                    Log.d("WiFiDirect", "Network connection unavailable.")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Connection failed or timed out", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            // 네트워크 연결 요청 (타임아웃과 함께)
            connectivityManager.requestNetwork(request, networkCallback, 30000) // 30초 타임아웃
        } else {
            // (Android 9 이하에서는 WifiManager.addNetwork() 등 레거시 방식 사용 필요)
            Toast.makeText(this, "QR connection only supported on Android 10+", Toast.LENGTH_LONG).show()
        }
    }
    // --- [ 함수 추가 끝 ] ---

} // <--
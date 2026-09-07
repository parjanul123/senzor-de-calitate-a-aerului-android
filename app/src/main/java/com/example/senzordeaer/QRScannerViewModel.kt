package com.example.senzordeaer

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

sealed class QRScannerState {
    object Idle : QRScannerState()
    object Scanning : QRScannerState()
    object Processing : QRScannerState()
    data class RequestFound(val request: WebLoginRequest) : QRScannerState()
    data class Success(val message: String) : QRScannerState()
    data class Error(val message: String) : QRScannerState()
}

class QRScannerViewModel(private val repository: SupabaseRepository = SupabaseRepository()) : ViewModel() {

    private val _state = MutableStateFlow<QRScannerState>(QRScannerState.Idle)
    val state: StateFlow<QRScannerState> = _state

    fun onQrCodeScanned(qrContent: String) {
        if (_state.value !is QRScannerState.Scanning) return

        val raw = qrContent.trim()
        Log.d("QRScanner", "Scanned content: '$raw'")

        // Extragem token-ul din URL dacă e cazul (format: https://.../?token=XYZ sau https://.../login/XYZ)
        val token = try {
            val uri = Uri.parse(raw)
            uri.getQueryParameter("token")
                ?: uri.lastPathSegment?.takeIf { it.isNotBlank() && raw.startsWith("http") }
                ?: raw
        } catch (e: Exception) {
            raw
        }.trim()

        Log.d("QRScanner", "Extracted token: '$token'")

        _state.value = QRScannerState.Processing

        viewModelScope.launch {
            try {
                val request = repository.getLoginRequest(token)
                Log.d("QRScanner", "Lookup result: found=${request != null}, status=${request?.status}")
                if (request != null && request.status == "pending") {
                    val now = Clock.System.now()
                    val expiresAt = Instant.parse(request.expires_at)
                    
                    if (now < expiresAt) {
                        _state.value = QRScannerState.RequestFound(request)
                    } else {
                        _state.value = QRScannerState.Error("Codul QR a expirat.")
                    }
                } else {
                    _state.value = QRScannerState.Error("Cod invalid sau deja utilizat.")
                }
            } catch (e: Exception) {
                Log.e("QRScanner", "Error fetching login request", e)
                _state.value = QRScannerState.Error("Eroare la comunicarea cu serverul.")
            }
        }
    }

    fun approveLogin(requestId: String, userId: String) {
        if (_state.value is QRScannerState.Processing && _state.value !is QRScannerState.RequestFound) return
        _state.value = QRScannerState.Processing
        
        viewModelScope.launch {
            val success = repository.updateLoginRequestStatus(requestId, "approved", userId)
            if (success) {
                _state.value = QRScannerState.Success("Conectarea a fost aprobată. Website-ul se va loga automat.")
            } else {
                _state.value = QRScannerState.Error("Nu s-a putut aproba conectarea. Verifică internetul.")
            }
        }
    }

    fun rejectLogin(requestId: String) {
        viewModelScope.launch {
            repository.updateLoginRequestStatus(requestId, "rejected")
            _state.value = QRScannerState.Scanning
        }
    }

    fun startScanning() {
        if (_state.value is QRScannerState.Idle || _state.value is QRScannerState.Error || _state.value is QRScannerState.Success) {
            _state.value = QRScannerState.Scanning
        }
    }
    
    fun resetState() {
        _state.value = QRScannerState.Scanning
    }
}

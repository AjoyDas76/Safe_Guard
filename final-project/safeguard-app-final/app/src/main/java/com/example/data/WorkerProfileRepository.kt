package com.example.data

import android.content.Context
import com.example.model.WorkerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object WorkerProfileRepository {
    private const val PREFS_NAME = "worker_profiles_prefs"
    private const val KEY_PROFILES_JSON = "key_profiles_json"
    private const val KEY_ACTIVE_WORKER_ID = "key_active_worker_id"

    private val defaultWorkers = listOf(
        WorkerProfile(
            workerId = "WKR-01",
            workerName = "Rahim Uddin",
            phone = "+880 1711-234567",
            email = "rahim.safety@safeguard.io",
            bloodGroup = "O+",
            assignedVestId = "VEST-LoRa-01",
            loraNode = "worker1",
            loraFrequency = "915.0 MHz (LoRa P2P)",
            department = "Hazardous Drilling & Unit 4",
            shift = "Day Shift (08:00 - 20:00)",
            batchId = "SV-2026-014",
            emergencyContactName = "Dr. Farhana Akter",
            emergencyContactPhone = "+880 1999-911911",
            emergencyRelation = "Site Chief Medical Officer",
            secondaryContactName = "Rafiqul Hasan (Safety Lead)",
            secondaryContactPhone = "+880 1812-334455",
            medicalNotes = "Asthma inhaler required. High heat stress sensitive.",
            isActive = true
        ),
        WorkerProfile(
            workerId = "WKR-02",
            workerName = "Kamrul Hasan",
            phone = "+880 1822-456789",
            email = "kamrul.hasan@safeguard.io",
            bloodGroup = "A+",
            assignedVestId = "VEST-LoRa-02",
            loraNode = "worker2",
            loraFrequency = "915.0 MHz (LoRa P2P)",
            department = "Confined Space Tank 3",
            shift = "Night Shift (20:00 - 08:00)",
            batchId = "SV-2026-015",
            emergencyContactName = "Nasima Akter",
            emergencyContactPhone = "+880 1811-987654",
            emergencyRelation = "Spouse / Next of Kin",
            secondaryContactName = "Control Room Dispatch",
            secondaryContactPhone = "+880 1999-911912",
            medicalNotes = "No known allergies. Certified enclosed space diver.",
            isActive = false
        ),
        WorkerProfile(
            workerId = "WKR-03",
            workerName = "Selim Reza",
            phone = "+880 1933-567890",
            email = "selim.reza@safeguard.io",
            bloodGroup = "B+",
            assignedVestId = "VEST-LoRa-03",
            loraNode = "worker3",
            loraFrequency = "915.0 MHz (LoRa P2P)",
            department = "Catalytic Cracking Unit",
            shift = "Day Shift (08:00 - 20:00)",
            batchId = "SV-2026-016",
            emergencyContactName = "Anwar Hossain",
            emergencyContactPhone = "+880 1744-112233",
            emergencyRelation = "Brother",
            secondaryContactName = "Emergency Response Team Lead",
            secondaryContactPhone = "+880 1999-911913",
            medicalNotes = "Blood pressure monitored bi-weekly.",
            isActive = false
        ),
        WorkerProfile(
            workerId = "WKR-04",
            workerName = "Tanvir Ahmed",
            phone = "+880 1655-678901",
            email = "tanvir.ahmed@safeguard.io",
            bloodGroup = "AB+",
            assignedVestId = "VEST-LoRa-04",
            loraNode = "worker4",
            loraFrequency = "915.0 MHz (LoRa P2P)",
            department = "Pipeline Maintenance Corridor",
            shift = "Rotation (12:00 - 00:00)",
            batchId = "SV-2026-017",
            emergencyContactName = "Site Paramedic Station",
            emergencyContactPhone = "+880 1999-911910",
            emergencyRelation = "On-site Paramedic",
            secondaryContactName = "Faridul Alam (Foreman)",
            secondaryContactPhone = "+880 1833-221100",
            medicalNotes = "Diabetic (Carries glucose emergency kit).",
            isActive = false
        )
    )

    private val _workers = MutableStateFlow<List<WorkerProfile>>(defaultWorkers)
    val workers: StateFlow<List<WorkerProfile>> = _workers.asStateFlow()

    private val _activeWorker = MutableStateFlow(defaultWorkers[0])
    val activeWorker: StateFlow<WorkerProfile> = _activeWorker.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_PROFILES_JSON, null)
        val activeId = prefs.getString(KEY_ACTIVE_WORKER_ID, "WKR-01")

        if (!jsonStr.isNullOrEmpty()) {
            try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<WorkerProfile>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        WorkerProfile(
                            workerId = obj.optString("workerId", "WKR-${i + 1}"),
                            workerName = obj.optString("workerName", "Worker"),
                            phone = obj.optString("phone", ""),
                            email = obj.optString("email", ""),
                            bloodGroup = obj.optString("bloodGroup", "O+"),
                            assignedVestId = obj.optString("assignedVestId", "VEST-01"),
                            loraNode = obj.optString("loraNode", "worker1"),
                            loraFrequency = obj.optString("loraFrequency", "915.0 MHz"),
                            department = obj.optString("department", "Operations"),
                            shift = obj.optString("shift", "Day"),
                            batchId = obj.optString("batchId", "BATCH-01"),
                            emergencyContactName = obj.optString("emergencyContactName", ""),
                            emergencyContactPhone = obj.optString("emergencyContactPhone", ""),
                            emergencyRelation = obj.optString("emergencyRelation", ""),
                            secondaryContactName = obj.optString("secondaryContactName", ""),
                            secondaryContactPhone = obj.optString("secondaryContactPhone", ""),
                            medicalNotes = obj.optString("medicalNotes", ""),
                            isActive = obj.optString("workerId") == activeId
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    _workers.value = list
                    val selected = list.find { it.workerId == activeId } ?: list[0]
                    _activeWorker.value = selected.copy(isActive = true)
                    return
                }
            } catch (_: Exception) {}
        }
        _workers.value = defaultWorkers
        _activeWorker.value = defaultWorkers[0]
        saveToPrefs()
    }

    private fun saveToPrefs() {
        val ctx = appContext ?: return
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (w in _workers.value) {
            val obj = JSONObject().apply {
                put("workerId", w.workerId)
                put("workerName", w.workerName)
                put("phone", w.phone)
                put("email", w.email)
                put("bloodGroup", w.bloodGroup)
                put("assignedVestId", w.assignedVestId)
                put("loraNode", w.loraNode)
                put("loraFrequency", w.loraFrequency)
                put("department", w.department)
                put("shift", w.shift)
                put("batchId", w.batchId)
                put("emergencyContactName", w.emergencyContactName)
                put("emergencyContactPhone", w.emergencyContactPhone)
                put("emergencyRelation", w.emergencyRelation)
                put("secondaryContactName", w.secondaryContactName)
                put("secondaryContactPhone", w.secondaryContactPhone)
                put("medicalNotes", w.medicalNotes)
            }
            array.put(obj)
        }
        prefs.edit()
            .putString(KEY_PROFILES_JSON, array.toString())
            .putString(KEY_ACTIVE_WORKER_ID, _activeWorker.value.workerId)
            .commit()
    }

    fun setActiveWorker(workerId: String) {
        val currentList = _workers.value
        val updated = currentList.map { it.copy(isActive = (it.workerId == workerId)) }
        _workers.value = updated
        val active = updated.find { it.workerId == workerId } ?: updated[0]
        _activeWorker.value = active
        saveToPrefs()
    }

    fun saveOrUpdateWorker(profile: WorkerProfile) {
        val currentList = _workers.value.toMutableList()
        val index = currentList.indexOfFirst { it.workerId == profile.workerId }
        if (index >= 0) {
            currentList[index] = profile
        } else {
            currentList.add(profile)
        }
        val activeId = _activeWorker.value.workerId
        val updated = currentList.map { it.copy(isActive = (it.workerId == activeId)) }
        _workers.value = updated
        if (_activeWorker.value.workerId == profile.workerId) {
            _activeWorker.value = profile.copy(isActive = true)
        }
        saveToPrefs()
    }

    fun deleteWorker(workerId: String) {
        val currentList = _workers.value.filterNot { it.workerId == workerId }
        if (currentList.isNotEmpty()) {
            val newActiveId = if (_activeWorker.value.workerId == workerId) {
                currentList[0].workerId
            } else {
                _activeWorker.value.workerId
            }
            val updated = currentList.map { it.copy(isActive = (it.workerId == newActiveId)) }
            _workers.value = updated
            _activeWorker.value = updated.find { it.workerId == newActiveId } ?: updated[0]
            saveToPrefs()
        }
    }
}

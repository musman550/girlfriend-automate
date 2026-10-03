package com.musfira.smsai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val scope = MainScope()
    private var loading = false
    private var uiProvider = Prefs.PROVIDER_GROQ
    private var modelGroq = ""
    private var modelGemini = ""
    private val contacts = mutableListOf<Contact>()

    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvLog: TextView
    private lateinit var tvMuted: TextView
    private lateinit var switchEnable: MaterialSwitch
    private lateinit var switchFallback: MaterialSwitch
    private lateinit var switchApprove: MaterialSwitch
    private lateinit var switchHours: MaterialSwitch
    private lateinit var chipContacts: ChipGroup
    private lateinit var rgProvider: RadioGroup
    private lateinit var etAddContact: EditText
    private lateinit var etGroqKey: EditText
    private lateinit var etGeminiKey: EditText
    private lateinit var etModel: EditText
    private lateinit var etOwnerName: EditText
    private lateinit var etIntro: EditText
    private lateinit var etPersona: EditText
    private lateinit var etCooldown: EditText
    private lateinit var etMaxHour: EditText
    private lateinit var etDelayMin: EditText
    private lateinit var etDelayMax: EditText
    private lateinit var etStartHour: EditText
    private lateinit var etEndHour: EditText
    private lateinit var etStopWords: EditText
    private lateinit var etUrgentWords: EditText

    private val smsPerms = arrayOf(
        Manifest.permission.READ_SMS,
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS
    )

    private val pickContact = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data ?: return@registerForActivityResult
        try {
            contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ), null, null, null
            )?.use {
                if (it.moveToFirst()) addContact(Contact(it.getString(0).orEmpty(), it.getString(1).orEmpty()))
            }
        } catch (e: Exception) {
            toast("Contact nahi parh saka, number khud likh dein")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        tvStatus = findViewById(R.id.tvStatus)
        tvStats = findViewById(R.id.tvStats)
        tvLog = findViewById(R.id.tvLog)
        tvMuted = findViewById(R.id.tvMuted)
        switchEnable = findViewById(R.id.switchEnable)
        switchFallback = findViewById(R.id.switchFallback)
        switchApprove = findViewById(R.id.switchApprove)
        switchHours = findViewById(R.id.switchHours)
        chipContacts = findViewById(R.id.chipContacts)
        rgProvider = findViewById(R.id.rgProvider)
        etAddContact = findViewById(R.id.etAddContact)
        etGroqKey = findViewById(R.id.etGroqKey)
        etGeminiKey = findViewById(R.id.etGeminiKey)
        etModel = findViewById(R.id.etModel)
        etOwnerName = findViewById(R.id.etOwnerName)
        etIntro = findViewById(R.id.etIntro)
        etPersona = findViewById(R.id.etPersona)
        etCooldown = findViewById(R.id.etCooldown)
        etMaxHour = findViewById(R.id.etMaxHour)
        etDelayMin = findViewById(R.id.etDelayMin)
        etDelayMax = findViewById(R.id.etDelayMax)
        etStartHour = findViewById(R.id.etStartHour)
        etEndHour = findViewById(R.id.etEndHour)
        etStopWords = findViewById(R.id.etStopWords)
        etUrgentWords = findViewById(R.id.etUrgentWords)

        loadToUi()

        rgProvider.setOnCheckedChangeListener { _, id ->
            if (loading) return@setOnCheckedChangeListener
            storeModelText()
            uiProvider = if (id == R.id.rbGemini) Prefs.PROVIDER_GEMINI else Prefs.PROVIDER_GROQ
            etModel.setText(if (uiProvider == Prefs.PROVIDER_GEMINI) modelGemini else modelGroq)
        }

        switchEnable.setOnCheckedChangeListener { _, checked ->
            if (loading) return@setOnCheckedChangeListener
            if (checked) {
                saveFromUi()
                val need = requiredPerms().filter {
                    ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }
                if (need.isNotEmpty()) ActivityCompat.requestPermissions(this, need.toTypedArray(), 100)
                prefs.pausedUntil = 0L
            }
            prefs.enabled = checked
            refreshStatus()
        }

        findViewById<MaterialButton>(R.id.btnAddContact).setOnClickListener {
            val t = etAddContact.text.toString().trim()
            if (t.isEmpty()) return@setOnClickListener
            val isNumber = t.none { it.isLetter() } && ChatRepository.digits(t).length >= 7
            addContact(if (isNumber) Contact("", t) else Contact(t, ""))
            etAddContact.setText("")
        }

        findViewById<MaterialButton>(R.id.btnPickContact).setOnClickListener {
            pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
        }

        findViewById<MaterialButton>(R.id.btnUnmute).setOnClickListener {
            prefs.clearMuted()
            refreshStatus()
            toast("Saare mute hat gaye")
        }

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            saveFromUi()
            toast("Saved")
            refreshStatus()
        }

        findViewById<MaterialButton>(R.id.btnPause).setOnClickListener {
            val labels = arrayOf("30 minute", "1 ghanta", "3 ghante", "8 ghante", "24 ghante")
            val mins = longArrayOf(30, 60, 180, 480, 1440)
            AlertDialog.Builder(this)
                .setTitle("Kitni der ke liye pause?")
                .setItems(labels) { _, i ->
                    prefs.pausedUntil = System.currentTimeMillis() + mins[i] * 60_000L
                    refreshStatus()
                }.show()
        }

        findViewById<MaterialButton>(R.id.btnResume).setOnClickListener {
            prefs.pausedUntil = 0L
            refreshStatus()
        }

        findViewById<MaterialButton>(R.id.btnStop).setOnClickListener {
            prefs.enabled = false
            setSwitch(false)
            refreshStatus()
        }

        findViewById<MaterialButton>(R.id.btnRemove).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Saare contacts remove karein?")
                .setMessage("Auto-reply band ho jayega. Naye contacts baad mein add kar sakte hain.")
                .setPositiveButton("Haan") { _, _ ->
                    contacts.clear()
                    prefs.setContacts(contacts)
                    prefs.enabled = false
                    setSwitch(false)
                    renderChips()
                    refreshStatus()
                }
                .setNegativeButton("Nahi", null).show()
        }

        findViewById<MaterialButton>(R.id.btnHistory).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("History")
                .setMessage(prefs.historyText())
                .setPositiveButton("OK", null)
                .setNeutralButton("Clear") { _, _ -> prefs.clearHistory(); toast("History saaf") }
                .show()
        }

        findViewById<MaterialButton>(R.id.btnModels).setOnClickListener {
            saveFromUi()
            val p = uiProvider
            toast("Models la raha hun...")
            scope.launch {
                try {
                    val list = ModelCatalog.list(prefs, p)
                    if (list.isEmpty()) { toast("Koi model nahi mila"); return@launch }
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Model chunein")
                        .setItems(list.toTypedArray()) { _, i -> etModel.setText(list[i]) }
                        .show()
                } catch (e: Exception) {
                    toast("Models nahi aaye: ${e.message}")
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnTest).setOnClickListener { btn ->
            saveFromUi()
            btn.isEnabled = false
            toast("Test chal raha hai...")
            scope.launch {
                val msg = try {
                    val r = AiService.reply(prefs, emptyList(), "Assalam o alaikum, kya haal hai?")
                    "OK\n\n" + SmsManagerHelper.compose(prefs.resolvedIntro(), r) +
                        "\n\nModel: ${prefs.modelFor(prefs.provider)}"
                } catch (e: Exception) {
                    "FAIL: ${e.message}"
                }
                btn.isEnabled = true
                loadToUi()
                AlertDialog.Builder(this@MainActivity).setTitle("API test").setMessage(msg)
                    .setPositiveButton("OK", null).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun requiredPerms(): List<String> =
        smsPerms.toList() + if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()

    private fun hasSmsPerms() = smsPerms.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun setSwitch(on: Boolean) {
        loading = true
        switchEnable.isChecked = on
        loading = false
    }

    private fun addContact(c: Contact) {
        val digits = ChatRepository.digits(c.number).takeLast(10)
        val dup = contacts.any {
            (digits.length >= 7 && ChatRepository.digits(it.number).takeLast(10) == digits) ||
                (c.number.isBlank() && it.number.isBlank() && it.name.equals(c.name, true))
        }
        if (dup) { toast("Pehle se add hai"); return }
        contacts.add(c)
        prefs.setContacts(contacts)
        renderChips()
    }

    private fun renderChips() {
        chipContacts.removeAllViews()
        for (c in contacts.toList()) {
            val chip = Chip(this)
            chip.text = c.label()
            chip.isCloseIconVisible = true
            chip.setOnCloseIconClickListener {
                contacts.remove(c)
                prefs.setContacts(contacts)
                renderChips()
            }
            chipContacts.addView(chip)
        }
    }

    private fun storeModelText() {
        val t = etModel.text.toString().trim()
        if (uiProvider == Prefs.PROVIDER_GEMINI) modelGemini = t else modelGroq = t
    }

    private fun loadToUi() {
        loading = true
        contacts.clear()
        contacts.addAll(prefs.contactList())
        renderChips()
        etGroqKey.setText(prefs.groqKey)
        etGeminiKey.setText(prefs.geminiKey)
        modelGroq = prefs.modelFor(Prefs.PROVIDER_GROQ)
        modelGemini = prefs.modelFor(Prefs.PROVIDER_GEMINI)
        uiProvider = prefs.provider
        etModel.setText(if (uiProvider == Prefs.PROVIDER_GEMINI) modelGemini else modelGroq)
        etOwnerName.setText(prefs.ownerName)
        etIntro.setText(prefs.intro)
        etPersona.setText(prefs.persona)
        etCooldown.setText(prefs.cooldownSec.toString())
        etMaxHour.setText(prefs.maxPerHour.toString())
        etDelayMin.setText(prefs.delayMin.toString())
        etDelayMax.setText(prefs.delayMax.toString())
        etStartHour.setText(prefs.startHour.toString())
        etEndHour.setText(prefs.endHour.toString())
        etStopWords.setText(prefs.stopWords)
        etUrgentWords.setText(prefs.urgentWords)
        switchFallback.isChecked = prefs.fallbackEnabled
        switchApprove.isChecked = prefs.approveMode
        switchHours.isChecked = prefs.useHours
        rgProvider.check(if (uiProvider == Prefs.PROVIDER_GEMINI) R.id.rbGemini else R.id.rbGroq)
        switchEnable.isChecked = prefs.enabled
        loading = false
    }

    private fun int(e: EditText, def: Int, lo: Int, hi: Int) =
        (e.text.toString().trim().toIntOrNull() ?: def).coerceIn(lo, hi)

    private fun saveFromUi() {
        storeModelText()
        prefs.setContacts(contacts)
        prefs.groqKey = etGroqKey.text.toString().trim()
        prefs.geminiKey = etGeminiKey.text.toString().trim()
        prefs.provider = uiProvider
        prefs.groqModel = modelGroq
        prefs.geminiModel = modelGemini
        prefs.fallbackEnabled = switchFallback.isChecked
        prefs.ownerName = etOwnerName.text.toString().trim()
        prefs.intro = etIntro.text.toString().trim()
        prefs.persona = etPersona.text.toString().ifBlank { Prefs.DEFAULT_PERSONA }
        prefs.approveMode = switchApprove.isChecked
        prefs.cooldownSec = int(etCooldown, 20, 5, 3600)
        prefs.maxPerHour = int(etMaxHour, 10, 1, 200)
        prefs.delayMin = int(etDelayMin, 5, 0, 600)
        prefs.delayMax = int(etDelayMax, 20, 0, 600)
        prefs.useHours = switchHours.isChecked
        prefs.startHour = int(etStartHour, 22, 0, 23)
        prefs.endHour = int(etEndHour, 8, 0, 23)
        prefs.stopWords = etStopWords.text.toString().trim()
        prefs.urgentWords = etUrgentWords.text.toString().trim()
    }

    private fun refreshStatus() {
        val now = System.currentTimeMillis()
        tvStatus.text = when {
            !prefs.enabled -> "STOPPED"
            now < prefs.pausedUntil -> "PAUSED (" +
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(prefs.pausedUntil)) + " tak)"
            !hasSmsPerms() -> "ON, lekin SMS/Contacts permissions baqi hain"
            else -> "ACTIVE"
        }
        tvStats.text = "Replies bheje: ${prefs.sentCount}  |  Mute contacts: ${prefs.mutedCount()}"
        tvMuted.text = if (prefs.mutedCount() > 0) "${prefs.mutedCount()} contact ne STOP likha hai, unhein reply nahi jata." else ""
        tvLog.text = prefs.lastLog
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshStatus()
    }
}

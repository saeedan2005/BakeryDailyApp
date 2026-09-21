package com.example.bakerydaily.ui

import android.app.Application
import android.app.DatePickerDialog
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.bakerydaily.data.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val DBF = DateTimeFormatter.ISO_LOCAL_DATE
private val UI = DateTimeFormatter.ofPattern("dd/MM/yyyy")
private val AR = Locale("ar")

fun todayDb(): String =
    LocalDate.now().format(DBF)

fun uiDate(db: String): String =
    runCatching {
        LocalDate.parse(db, DBF).format(UI)
    }.getOrDefault(db)

fun parseUiDate(value: String): String? =
    runCatching {
        LocalDate.parse(value, UI).format(DBF)
    }.getOrNull()

fun dayName(db: String): String =
    runCatching {
        LocalDate.parse(db, DBF)
            .dayOfWeek
            .getDisplayName(TextStyle.FULL, AR)
    }.getOrDefault("")

fun normalizeName(s: String): String =
    s.trim()
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)

fun formatNumber(value: Double): String =
    if (value == 0.0) {
        "0"
    } else {
        String.format(Locale.US, "%.2f", value)
            .trimEnd('0')
            .trimEnd('.')
    }

private fun Context.pickDate(
    initialDb: String,
    onPicked: (String) -> Unit
) {
    val initial = runCatching {
        LocalDate.parse(initialDb, DBF)
    }.getOrDefault(LocalDate.now())

    DatePickerDialog(
        this,
        { _, y, m, d ->
            onPicked(
                LocalDate.of(y, m + 1, d).format(DBF)
            )
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth
    ).apply {
        datePicker.maxDate = System.currentTimeMillis()
    }.show()
}

object SettingsStore {

    private const val PREFS = "bakery_settings"
    private const val NIGHT = "night_mode"

    private var context: Context? = null

    fun init(c: Context) {
        context = c.applicationContext
    }

    fun isNightMode(): Boolean =
        context
            ?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.getBoolean(NIGHT, false)
            ?: false

    fun setNightMode(value: Boolean) {
        context
            ?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(NIGHT, value)
            ?.apply()
    }
}

/* -------------------------------------------------------------------------- */
/* Bakery ViewModel                                                           */
/* -------------------------------------------------------------------------- */

class BakeryVM(
    app: Application
) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)

    val list = db.bakeryDao().observeAll()

    var error by mutableStateOf<String?>(null)

    fun save(
        id: Long,
        number: String,
        name: String,
        base: String,
        factor: String,
        done: () -> Unit
    ) = viewModelScope.launch {

        error = null

        val n = number.trim()
        val nm = name.trim()
        val br = base.toLongOrNull()
        val cf = factor.toLongOrNull()

        when {
            n.isBlank() -> {
                error = "رقم المخبز حقل إجباري"
                return@launch
            }

            !n.all(Char::isDigit) -> {
                error = "رقم المخبز يجب أن يتكون من أرقام فقط"
                return@launch
            }

            nm.isBlank() -> {
                error = "اسم المخبز حقل إجباري"
                return@launch
            }

            br == null || br <= 0 -> {
                error = "قيمة الإيراد الأساسية يجب أن تكون عدداً صحيحاً موجباً"
                return@launch
            }

            cf == null || cf <= 0 -> {
                error = "معامل البيع النقدي يجب أن يكون عدداً صحيحاً موجباً"
                return@launch
            }

            db.bakeryDao().numberExists(n, id) > 0 -> {
                error = "رقم المخبز موجود مسبقاً"
                return@launch
            }

            db.bakeryDao().nameExists(normalizeName(nm), id) > 0 -> {
                error = "اسم المخبز موجود مسبقاً"
                return@launch
            }
        }

        try {
            if (id == 0L) {

                db.bakeryDao().insert(
                    Bakery(
                        number = n,
                        name = nm,
                        normalizedName = normalizeName(nm),
                        baseRevenue = br!!,
                        cashFactor = cf!!
                    )
                )

            } else {

                val existing = db.bakeryDao().get(id)

                if (existing == null) {
                    error = "لم يتم العثور على المخبز"
                    return@launch
                }

                db.bakeryDao().update(
                    existing.copy(
                        number = n,
                        name = nm,
                        normalizedName = normalizeName(nm),
                        baseRevenue = br!!,
                        cashFactor = cf!!
                    )
                )
            }

            done()

        } catch (_: SQLiteConstraintException) {

            error = "رقم المخبز أو اسم المخبز مستخدم مسبقاً"

        } catch (_: Exception) {

            error = "تعذر حفظ بيانات المخبز"
        }
    }

    fun delete(
        bakery: Bakery,
        done: () -> Unit
    ) = viewModelScope.launch {

        try {
            db.bakeryDao().delete(bakery)
            done()

        } catch (_: Exception) {

            error = "تعذر حذف المخبز"
        }
    }

    fun hasRecords(
        id: Long,
        result: (Boolean) -> Unit
    ) = viewModelScope.launch {

        result(
            db.dailyDao().countForBakery(id) > 0
        )
    }
}

/* -------------------------------------------------------------------------- */
/* Bakery Selection                                                           */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BakerySelection(
    nav: NavController,
    vm: BakeryVM = viewModel()
) {
    val list by vm.list.collectAsState(emptyList())

    var selected by remember {
        mutableStateOf<Bakery?>(null)
    }

    var form by remember {
        mutableStateOf(false)
    }

    var number by remember {
        mutableStateOf("")
    }

    var name by remember {
        mutableStateOf("")
    }

    var base by remember {
        mutableStateOf("500")
    }

    var factor by remember {
        mutableStateOf("9")
    }

    var deleteStep by remember {
        mutableStateOf(0)
    }

    var deleteHasRecords by remember {
        mutableStateOf(false)
    }

    fun add() {
        selected = null
        form = true
        number = ""
        name = ""
        base = "500"
        factor = "9"
        vm.error = null
    }

    fun edit() {
        selected?.let { bakery ->
            form = true
            number = bakery.number
            name = bakery.name
            base = bakery.baseRevenue.toString()
            factor = bakery.cashFactor.toString()
            vm.error = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("اختيار المخبز")
                },
                actions = {
                    IconButton(
                        onClick = {
                            nav.navigate("settings")
                        }
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "الإعدادات"
                        )
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Text(
                text = if (list.isEmpty()) {
                    "لا توجد مخابز. يرجى إضافة مخبز جديد"
                } else {
                    "اختر المخبز"
                },
                fontWeight = FontWeight.Bold
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                items(
                    items = list,
                    key = { it.id }
                ) { bakery ->

                    Button(
                        onClick = {
                            selected = bakery
                            form = false
                            vm.error = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "${bakery.number} - ${bakery.name}"
                        )
                    }
                }
            }

            if (form) {

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        NumericField(
                            label = "رقم المخبز *",
                            value = number
                        ) {
                            number = it
                        }

                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                            },
                            label = {
                                Text("اسم المخبز *")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text(
                            "إعدادات الحساب",
                            fontWeight = FontWeight.Bold
                        )

                        NumericField(
                            label = "قيمة الإيراد الأساسية *",
                            value = base
                        ) {
                            base = it
                        }

                        NumericField(
                            label = "معامل البيع النقدي *",
                            value = factor
                        ) {
                            factor = it
                        }
                    }
                }
            }

            vm.error?.let { message ->

                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            if (form) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    Button(
                        onClick = {
                            vm.save(
                                selected?.id ?: 0L,
                                number,
                                name,
                                base,
                                factor
                            ) {
                                form = false
                                selected = null
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("حفظ")
                    }

                    OutlinedButton(
                        onClick = {
                            form = false
                            vm.error = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء")
                    }
                }

            } else {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    Button(
                        onClick = {
                            add()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("إضافة")
                    }

                    Button(
                        onClick = {
                            edit()
                        },
                        enabled = selected != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("تعديل")
                    }

                    Button(
                        onClick = {
                            selected?.let { bakery ->
                                vm.hasRecords(bakery.id) { hasRecords ->
                                    deleteHasRecords = hasRecords
                                    deleteStep = 1
                                }
                            }
                        },
                        enabled = selected != null,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("حذف")
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                Button(
                    onClick = {
                        selected?.let {
                            nav.navigate("home/${it.id}")
                        }
                    },
                    enabled = selected != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("فتح بيانات المخبز")
                }

                OutlinedButton(
                    onClick = {
                        nav.navigate("settings")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("الإعدادات")
                }
            }
        }
    }

    if (deleteStep > 0) {

        AlertDialog(
            onDismissRequest = {
                deleteStep = 0
            },
            title = {
                Text(
                    if (deleteStep == 1) {
                        "تأكيد حذف المخبز"
                    } else {
                        "تأكيد الحذف مرة أخرى"
                    }
                )
            },
            text = {

                Text(
                    when {
                        deleteStep == 1 && deleteHasRecords ->
                            "سيتم حذف المخبز وجميع سجلاته اليومية المرتبطة به. هل أنت متأكد؟"

                        deleteStep == 1 ->
                            "هل أنت متأكد من حذف هذا المخبز؟"

                        else ->
                            "هذا إجراء نهائي، وسيتم حذف سجلات المخبز نهائياً. هل تريد المتابعة؟"
                    }
                )
            },
            confirmButton = {

                TextButton(
                    onClick = {

                        if (deleteStep == 1 && deleteHasRecords) {

                            deleteStep = 2

                        } else {

                            selected?.let { bakery ->

                                vm.delete(bakery) {
                                    selected = null
                                }
                            }

                            deleteStep = 0
                        }
                    }
                ) {
                    Text("تأكيد")
                }
            },
            dismissButton = {

                TextButton(
                    onClick = {
                        deleteStep = 0
                    }
                ) {
                    Text("إلغاء")
                }
            }
        )
    }
}

/* -------------------------------------------------------------------------- */
/* Record ViewModel                                                           */
/* -------------------------------------------------------------------------- */

class RecordVM(
    app: Application
) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)

    var bakery by mutableStateOf<Bakery?>(null)
    var rec by mutableStateOf<DailyRecord?>(null)
    var error by mutableStateOf<String?>(null)

    fun load(
        id: Long,
        date: String
    ) = viewModelScope.launch {

        bakery = db.bakeryDao().get(id)
        rec = db.dailyDao().byDate(id, date)
        error = null
    }

    fun save(
        r: DailyRecord,
        update: Boolean,
        done: () -> Unit
    ) = viewModelScope.launch {

        error = null

        when {

            r.date > todayDb() -> {
                error = "لا يمكن إنشاء أو تعديل سجل بتاريخ مستقبلي"
                return@launch
            }

            r.revenue < 0 ||
                r.flourKg < 0 ||
                r.loaves < 0 ||
                r.workersMeal < 0 ||
                r.waste < 0 ||
                r.creditSold < 0 -> {

                error = "لا يمكن إدخال قيمة سالبة"
                return@launch
            }

            r.baseRevenueUsed <= 0 ||
                r.cashFactorUsed <= 0 -> {

                error = "إعدادات الحساب غير صحيحة"
                return@launch
            }
        }

        val existing =
            db.dailyDao().byDate(
                r.bakeryId,
                r.date
            )

        if (existing != null && existing.id != r.id) {
            error = "هذا السجل محفوظ مسبقاً"
            return@launch
        }

        try {

            if (update) {

                db.dailyDao().update(r)
                rec = r
                done()

            } else {

                val newId =
                    db.dailyDao().insert(r)

                rec = r.copy(
                    id = newId
                )

                done()
            }

        } catch (_: SQLiteConstraintException) {

            error = "هذا السجل محفوظ مسبقاً"

        } catch (_: Exception) {

            error = "تعذر حفظ السجل"
        }
    }

    fun search(
        id: Long,
        date: String
    ) = viewModelScope.launch {

        val result =
            db.dailyDao().byDate(
                id,
                date
            )

        if (result != null) {

            rec = result
            error = null

        } else {

            error = "لا يوجد سجل في هذا التاريخ"
        }
    }

    fun delete(
        r: DailyRecord,
        done: () -> Unit
    ) = viewModelScope.launch {

        try {

            db.dailyDao().delete(r)
            rec = null
            error = null
            done()

        } catch (_: Exception) {

            error = "تعذر حذف السجل"
        }
    }
}

/* -------------------------------------------------------------------------- */
/* Draft                                                                       */
/* -------------------------------------------------------------------------- */

private data class Draft(
    val date: String,
    val flour: String,
    val loaves: String,
    val meal: String,
    val waste: String,
    val credit: String,
    val revenue: String
)

/* -------------------------------------------------------------------------- */
/* Home                                                                        */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(
    nav: NavController,
    id: Long,
    vm: RecordVM = viewModel()
) {

    val context = LocalContext.current

    var selectedDate by remember {
        mutableStateOf(todayDb())
    }

    var flour by remember {
        mutableStateOf("")
    }

    var loaves by remember {
        mutableStateOf("")
    }

    var meal by remember {
        mutableStateOf("70")
    }

    var waste by remember {
        mutableStateOf("")
    }

    var credit by remember {
        mutableStateOf("")
    }

    var revenue by remember {
        mutableStateOf("")
    }

    var editing by remember {
        mutableStateOf(false)
    }

    var newRecord by remember {
        mutableStateOf(false)
    }

    var baseline by remember {
        mutableStateOf<Draft?>(null)
    }

    var restoreDate by remember {
        mutableStateOf(todayDb())
    }

    var deleteDialog by remember {
        mutableStateOf(false)
    }

    var searchDialog by remember {
        mutableStateOf(false)
    }

    var unsavedDialog by remember {
        mutableStateOf(false)
    }

    var searchValue by remember {
        mutableStateOf(uiDate(todayDb()))
    }

    LaunchedEffect(id) {
        vm.load(
            id,
            todayDb()
        )
    }

    LaunchedEffect(vm.rec?.id) {

        vm.rec?.let { r ->

            selectedDate = r.date
            flour = r.flourKg.toString()
            loaves = r.loaves.toString()
            meal = r.workersMeal.toString()
            waste = r.waste.toString()
            credit = r.creditSold.toString()
            revenue = r.revenue.toString()

            baseline = Draft(
                r.date,
                flour,
                loaves,
                meal,
                waste,
                credit,
                revenue
            )
        }
    }

    val bakery = vm.bakery

    val base =
        if (newRecord) {
            bakery?.baseRevenue ?: 500L
        } else {
            vm.rec?.baseRevenueUsed
                ?: bakery?.baseRevenue
                ?: 500L
        }

    val factor =
        if (newRecord) {
            bakery?.cashFactor ?: 9L
        } else {
            vm.rec?.cashFactorUsed
                ?: bakery?.cashFactor
                ?: 9L
        }

    val fl = flour.toLongOrNull() ?: 0L
    val l = loaves.toLongOrNull() ?: 0L
    val m = meal.toLongOrNull() ?: 0L
    val w = waste.toLongOrNull() ?: 0L
    val c = credit.toLongOrNull() ?: 0L
    val rev = revenue.toLongOrNull() ?: 0L

    val calc = calculateRecord(
        fl,
        l,
        m,
        w,
        c,
        rev,
        base,
        factor
    )

    val currentDraft =
        Draft(
            selectedDate,
            flour,
            loaves,
            meal,
            waste,
            credit,
            revenue
        )

    val dirty =
        editing &&
            baseline != currentDraft

    fun leave() {
        nav.popBackStack()
    }

    fun requestLeave() {

        if (dirty) {
            unsavedDialog = true
        } else {
            leave()
        }
    }

    BackHandler {
        requestLeave()
    }

    Scaffold(
        topBar = {

            TopAppBar(
                title = {
                    Text(
                        bakery?.name ?: "بيانات المخبز"
                    )
                },
                navigationIcon = {

                    IconButton(
                        onClick = {
                            requestLeave()
                        }
                    ) {

                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "رجوع"
                        )
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Text(
                            "التاريخ",
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(
                            Modifier.weight(1f)
                        )

                        if (editing) {

                            OutlinedButton(
                                onClick = {
                                    context.pickDate(
                                        selectedDate
                                    ) {
                                        selectedDate = it
                                    }
                                }
                            ) {

                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null
                                )

                                Spacer(
                                    Modifier.width(4.dp)
                                )

                                Text("تغيير")
                            }
                        }
                    }

                    Text(
                        uiDate(selectedDate)
                    )

                    Text(
                        "اليوم: ${dayName(selectedDate)}"
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                item {
                    NumericField(
                        "كمية الدقيق (كجم)",
                        flour,
                        enabled = editing
                    ) {
                        flour = it
                    }
                }

                item {
                    NumericField(
                        "عدد الأقراص (حبة)",
                        loaves,
                        enabled = editing
                    ) {
                        loaves = it
                    }
                }

                item {
                    NumericField(
                        "عشاء العمال (حبة)",
                        meal,
                        enabled = editing
                    ) {
                        meal = it
                    }
                }

                item {
                    NumericField(
                        "التالف (حبة)",
                        waste,
                        enabled = editing
                    ) {
                        waste = it
                    }
                }

                item {
                    NumericField(
                        "المباع آجل (حبة)",
                        credit,
                        enabled = editing
                    ) {
                        credit = it
                    }
                }

                item {
                    NumericField(
                        "الإيراد (ريال) *",
                        revenue,
                        enabled = editing
                    ) {
                        revenue = it
                    }
                }

                item {
                    MetricCard(
                        "المباع نقداً",
                        "${formatNumber(calc.cashSold)} حبة"
                    )
                }

                item {
                    MetricCard(
                        "الفرق",
                        "${formatNumber(calc.difference)} حبة",
                        if (calc.difference < 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            null
                        }
                    )
                }

                item {
                    MetricCard(
                        "معدل إنتاج الكيس",
                        if (calc.bagRate == null) {
                            "غير متاح"
                        } else {
                            "${formatNumber(calc.bagRate)} حبة/كيس"
                        }
                    )
                }
            }

            vm.error?.let { message ->

                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            if (!editing) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {

                    Button(
                        onClick = {

                            restoreDate = selectedDate
                            newRecord = true
                            editing = true
                            selectedDate = todayDb()

                            flour = ""
                            loaves = ""
                            meal = "70"
                            waste = ""
                            credit = ""
                            revenue = ""

                            baseline = Draft(
                                todayDb(),
                                "",
                                "",
                                "70",
                                "",
                                "",
                                ""
                            )

                            vm.error = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {

                        Icon(
                            Icons.Default.Add,
                            contentDescription = null
                        )

                        Spacer(Modifier.width(3.dp))

                        Text("إضافة")
                    }

                    Button(
                        onClick = {

                            restoreDate = selectedDate
                            newRecord = false
                            editing = true

                            baseline = Draft(
                                selectedDate,
                                flour,
                                loaves,
                                meal,
                                waste,
                                credit,
                                revenue
                            )

                            vm.error = null
                        },
                        enabled = vm.rec != null,
                        modifier = Modifier.weight(1f)
                    ) {

                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null
                        )

                        Spacer(Modifier.width(3.dp))

                        Text("تعديل")
                    }

                    Button(
                        onClick = {
                            deleteDialog = true
                        },
                        enabled = vm.rec != null,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor =
                                MaterialTheme.colorScheme.error
                        )
                    ) {

                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null
                        )

                        Spacer(Modifier.width(3.dp))

                        Text("حذف")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {

                    OutlinedButton(
                        onClick = {

                            searchDialog = true
                            searchValue = uiDate(todayDb())
                            vm.error = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {

                        Icon(
                            Icons.Default.Search,
                            contentDescription = null
                        )

                        Spacer(Modifier.width(3.dp))

                        Text("بحث")
                    }

                    Button(
                        onClick = {
                            nav.navigate("stats/$id")
                        },
                        modifier = Modifier.weight(1f)
                    ) {

                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = null
                        )

                        Spacer(Modifier.width(3.dp))

                        Text("إحصاءات")
                    }
                }

            } else {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {

                    Button(
                        onClick = {

                            if (revenue.isBlank()) {

                                vm.error =
                                    "الإيراد حقل إجباري"

                            } else {

                                val record =
                                    DailyRecord(
                                        vm.rec?.id ?: 0L,
                                        id,
                                        selectedDate,
                                        fl,
                                        l,
                                        m,
                                        w,
                                        c,
                                        rev,
                                        base,
                                        factor
                                    )

                                vm.save(
                                    record,
                                    !newRecord
                                ) {

                                    editing = false
                                    newRecord = false

                                    baseline =
                                        Draft(
                                            selectedDate,
                                            flour,
                                            loaves,
                                            meal,
                                            waste,
                                            credit,
                                            revenue
                                        )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {

                        Icon(
                            Icons.Default.Save,
                            contentDescription = null
                        )

                        Spacer(Modifier.width(3.dp))

                        Text("حفظ")
                    }

                    OutlinedButton(
                        onClick = {

                            editing = false
                            newRecord = false
                            vm.error = null
                            vm.load(
                                id,
                                restoreDate
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء")
                    }
                }
            }
        }
    }

    if (deleteDialog) {

        AlertDialog(
            onDismissRequest = {
                deleteDialog = false
            },
            title = {
                Text("حذف السجل")
            },
            text = {
                Text(
                    "هل أنت متأكد من حذف السجل " +
                        "(بتاريخ السجل المعروض)؟ " +
                        "سيتم حذف السجل نهائياً."
                )
            },
            confirmButton = {

                TextButton(
                    onClick = {

                        deleteDialog = false

                        vm.rec?.let { record ->
                            vm.delete(record) {}
                        }
                    }
                ) {
                    Text("نعم")
                }
            },
            dismissButton = {

                TextButton(
                    onClick = {
                        deleteDialog = false
                    }
                ) {
                    Text("لا")
                }
            }
        )
    }

    if (searchDialog) {

        AlertDialog(
            onDismissRequest = {
                searchDialog = false
            },
            title = {
                Text("البحث عن سجل")
            },
            text = {

                OutlinedButton(
                    onClick = {

                        context.pickDate(
                            parseUiDate(searchValue)
                                ?: todayDb()
                        ) {
                            searchValue = uiDate(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null
                    )

                    Spacer(
                        Modifier.width(6.dp)
                    )

                    Text(searchValue)
                }
            },
            confirmButton = {

                TextButton(
                    onClick = {

                        val parsed =
                            parseUiDate(searchValue)

                        if (parsed != null) {

                            searchDialog = false
                            vm.search(
                                id,
                                parsed
                            )

                        } else {

                            vm.error =
                                "اختر تاريخاً صحيحاً"
                        }
                    }
                ) {
                    Text("بحث")
                }
            },
            dismissButton = {

                TextButton(
                    onClick = {
                        searchDialog = false
                    }
                ) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (unsavedDialog) {

        AlertDialog(
            onDismissRequest = {
                unsavedDialog = false
            },
            title = {
                Text("تغييرات غير محفوظة")
            },
            text = {
                Text(
                    "لديك تغييرات غير محفوظة، " +
                        "هل تريد حفظها قبل الانتقال؟"
                )
            },
            confirmButton = {

                TextButton(
                    onClick = {

                        if (revenue.isBlank()) {

                            vm.error =
                                "الإيراد حقل إجباري"

                        } else {

                            val record =
                                DailyRecord(
                                    vm.rec?.id ?: 0L,
                                    id,
                                    selectedDate,
                                    fl,
                                    l,
                                    m,
                                    w,
                                    c,
                                    rev,
                                    base,
                                    factor
                                )

                            vm.save(
                                record,
                                !newRecord
                            ) {

                                unsavedDialog = false
                                editing = false
                                newRecord = false
                                leave()
                            }
                        }
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {

                Row {

                    TextButton(
                        onClick = {
                            unsavedDialog = false
                            leave()
                        }
                    ) {
                        Text("خروج دون حفظ")
                    }

                    TextButton(
                        onClick = {
                            unsavedDialog = false
                        }
                    ) {
                        Text("إلغاء")
                    }
                }
            }
        )
    }
}

/* -------------------------------------------------------------------------- */
/* Numeric Field                                                               */
/* -------------------------------------------------------------------------- */

@Composable
private fun NumericField(
    label: String,
    value: String,
    enabled: Boolean = true,
    onValue: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = {
            onValue(
                it.filter(Char::isDigit)
            )
        },
        label = {
            Text(label)
        },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/* -------------------------------------------------------------------------- */
/* Metric Card                                                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun MetricCard(
    title: String,
    value: String,
    valueColor: Color? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                title,
                fontWeight = FontWeight.Bold
            )

            Text(
                value,
                fontWeight = FontWeight.Bold,
                color =
                    valueColor
                        ?: MaterialTheme.colorScheme.primary
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/* Statistics                                                                  */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Stats(
    nav: NavController,
    id: Long
) {

    val context = LocalContext.current

    val db = remember {
        AppDatabase.get(context)
    }

    val scope = rememberCoroutineScope()

    var from by remember {
        mutableStateOf("")
    }

    var to by remember {
        mutableStateOf("")
    }

    var output by remember {
        mutableStateOf<StatsResult?>(null)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var searching by remember {
        mutableStateOf(false)
    }

    Scaffold(
        topBar = {

            TopAppBar(
                title = {
                    Text("الإحصاءات")
                },
                navigationIcon = {

                    IconButton(
                        onClick = {
                            nav.popBackStack()
                        }
                    ) {

                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null
                        )
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {

                OutlinedButton(
                    onClick = {

                        context.pickDate(
                            parseUiDate(from)
                                ?: todayDb()
                        ) {
                            from = uiDate(it)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        if (from.isBlank()) {
                            "من تاريخ"
                        } else {
                            from
                        }
                    )
                }

                OutlinedButton(
                    onClick = {

                        context.pickDate(
                            parseUiDate(to)
                                ?: todayDb()
                        ) {
                            to = uiDate(it)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        if (to.isBlank()) {
                            "إلى تاريخ"
                        } else {
                            to
                        }
                    )
                }
            }

            Button(
                onClick = {

                    error = null

                    val start =
                        parseUiDate(from)

                    val end =
                        parseUiDate(to)

                    when {

                        start == null ||
                            end == null -> {

                            error =
                                "يرجى تحديد تاريخ البداية والنهاية"
                        }

                        start > end -> {

                            error =
                                "يجب أن يكون من تاريخ أقل من أو يساوي إلى تاريخ"
                        }

                        end > todayDb() -> {

                            error =
                                "لا يمكن أن تشمل الفترة تاريخاً مستقبلياً"
                        }

                        else -> {

                            scope.launch {

                                searching = true

                                val rows =
                                    db.dailyDao().between(
                                        id,
                                        start,
                                        end
                                    )

                                searching = false

                                if (rows.isEmpty()) {

                                    error =
                                        "لا توجد بيانات لعرضها"

                                } else {

                                    output =
                                        calculateStats(
                                            rows,
                                            from,
                                            to
                                        )
                                }
                            }
                        }
                    }
                },
                enabled = !searching,
                modifier = Modifier.fillMaxWidth()
            ) {

                Icon(
                    Icons.Default.Search,
                    contentDescription = null
                )

                Spacer(
                    Modifier.width(5.dp)
                )

                Text(
                    if (searching) {
                        "جارٍ البحث..."
                    } else {
                        "بحث"
                    }
                )
            }

            error?.let { message ->

                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            if (output != null) {

                val result = output!!

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {

                    item {
                        Text(
                            "الفترة: ${result.from} إلى ${result.to}",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    item {
                        StatLine(
                            "عدد السجلات",
                            result.count.toString()
                        )
                    }

                    item {
                        StatLine(
                            "إجمالي كمية الدقيق",
                            "${result.flour} كجم"
                        )
                    }

                    item {
                        StatLine(
                            "إجمالي عدد الأقراص",
                            "${result.loaves} حبة"
                        )
                    }

                    item {
                        StatLine(
                            "إجمالي عشاء العمال",
                            "${result.meal} حبة"
                        )
                    }

                    item {
                        StatLine(
                            "إجمالي التالف",
                            "${result.waste} حبة"
                        )
                    }

                    item {
                        StatLine(
                            "إجمالي المباع آجل",
                            "${result.credit} حبة"
                        )
                    }

                    item {
                        StatLine(
                            "إجمالي المباع نقداً",
                            "${formatNumber(result.cash)} حبة"
                        )
                    }

                    item {
                        StatLine(
                            "إجمالي الإيراد",
                            "${result.revenue} ريال"
                        )
                    }

                    item {
                        StatLine(
                            "إجمالي الفرق",
                            "${formatNumber(result.difference)} حبة",
                            if (result.difference < 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                null
                            }
                        )
                    }

                    item {
                        StatLine(
                            "معدل إنتاج الكيس",
                            if (result.rate == null) {
                                "غير متاح"
                            } else {
                                "${formatNumber(result.rate)} حبة/كيس"
                            }
                        )
                    }
                }

            } else {

                Spacer(
                    Modifier.weight(1f)
                )
            }

            OutlinedButton(
                onClick = {
                    nav.popBackStack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("رجوع")
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* Statistic Line                                                              */
/* -------------------------------------------------------------------------- */

@Composable
private fun StatLine(
    title: String,
    value: String,
    valueColor: Color? = null
) {

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(title)

            Text(
                value,
                fontWeight = FontWeight.Bold,
                color =
                    valueColor
                        ?: MaterialTheme.colorScheme.primary
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/* Settings                                                                    */
/* -------------------------------------------------------------------------- */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(
    nav: NavController,
    dark: Boolean,
    onDark: (Boolean) -> Unit
) {

    Scaffold(
        topBar = {

            TopAppBar(
                title = {
                    Text("الإعدادات")
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement =
                        Arrangement.SpaceBetween,
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Text("الوضع الليلي")

                    Switch(
                        checked = dark,
                        onCheckedChange = onDark
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(5.dp)
                ) {

                    Text(
                        "معلومات التطبيق",
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "تطبيق المخابز – نسخة تجريبية"
                    )

                    Text(
                        "تصميم التطبيق عبدالعزيز سعيدان"
                    )
                }
            }

            Spacer(
                Modifier.weight(1f)
            )

            Button(
                onClick = {
                    nav.popBackStack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("العودة إلى اختيار المخبز")
            }
        }
    }
}

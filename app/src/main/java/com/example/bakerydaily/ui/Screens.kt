package com.example.bakerydaily.ui

import android.app.Application
import android.app.DatePickerDialog
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
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
fun todayDb(): String = LocalDate.now().format(DBF)
fun uiDate(db: String): String = runCatching { LocalDate.parse(db, DBF).format(UI) }.getOrDefault(db)
fun parseUiDate(value: String): String? = runCatching { LocalDate.parse(value, UI).format(DBF) }.getOrNull()
fun dayName(db: String): String = runCatching { LocalDate.parse(db, DBF).dayOfWeek.getDisplayName(TextStyle.FULL, AR) }.getOrDefault("")
fun normalizeName(s: String) = s.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
fun formatNumber(value: Double): String = if (value == 0.0) "0" else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

private fun Context.pickDate(initialDb: String, onPicked: (String) -> Unit) {
    val initial = runCatching { LocalDate.parse(initialDb, DBF) }.getOrDefault(LocalDate.now())
    DatePickerDialog(this, { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d).format(DBF)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
        datePicker.maxDate = System.currentTimeMillis()
    }.show()
}

object SettingsStore {
    private const val PREFS = "bakery_settings"
    private const val NIGHT = "night_mode"
    private var context: Context? = null
    fun init(c: Context) { context = c.applicationContext }
    fun isNightMode() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getBoolean(NIGHT, false) ?: false
    fun setNightMode(value: Boolean) { context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(NIGHT, value)?.apply() }
}

private class BakeryVM(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    val list = db.bakeryDao().observeAll()
    var error by mutableStateOf<String?>(null)

    fun save(id: Long, number: String, name: String, base: String, factor: String, done: () -> Unit) = viewModelScope.launch {
        error = null
        val n = number.trim(); val nm = name.trim(); val br = base.toLongOrNull(); val cf = factor.toLongOrNull()
        when {
            n.isBlank() -> { error = "رقم المخبز حقل إجباري"; return@launch }
            !n.all(Char::isDigit) -> { error = "رقم المخبز يجب أن يتكون من أرقام فقط"; return@launch }
            nm.isBlank() -> { error = "اسم المخبز حقل إجباري"; return@launch }
            br == null || br <= 0 -> { error = "قيمة الإيراد الأساسية يجب أن تكون عدداً صحيحاً موجباً"; return@launch }
            cf == null || cf <= 0 -> { error = "معامل البيع النقدي يجب أن يكون عدداً صحيحاً موجباً"; return@launch }
            db.bakeryDao().numberExists(n, id) > 0 -> { error = "رقم المخبز موجود مسبقاً"; return@launch }
            db.bakeryDao().nameExists(normalizeName(nm), id) > 0 -> { error = "اسم المخبز موجود مسبقاً"; return@launch }
        }
        try {
            if (id == 0L) db.bakeryDao().insert(Bakery(number = n, name = nm, normalizedName = normalizeName(nm), baseRevenue = br!!, cashFactor = cf!!))
            else db.bakeryDao().get(id)?.let { db.bakeryDao().update(it.copy(number = n, name = nm, normalizedName = normalizeName(nm), baseRevenue = br!!, cashFactor = cf!!)) } ?: run { error = "لم يتم العثور على المخبز"; return@launch }
            done()
        } catch (_: SQLiteConstraintException) { error = "رقم المخبز أو اسم المخبز مستخدم مسبقاً" }
        catch (_: Exception) { error = "تعذر حفظ بيانات المخبز" }
    }

    fun delete(b: Bakery, done: () -> Unit) = viewModelScope.launch { try { db.bakeryDao().delete(b); done() } catch (_: Exception) { error = "تعذر حذف المخبز" } }
    fun hasRecords(id: Long, result: (Boolean) -> Unit) = viewModelScope.launch { result(db.dailyDao().countForBakery(id) > 0) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BakerySelection(nav: NavController, vm: BakeryVM = viewModel()) {
    val list by vm.list.collectAsState(emptyList())
    var selected by remember { mutableStateOf<Bakery?>(null) }
    var form by remember { mutableStateOf(false) }
    var number by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }
    var base by remember { mutableStateOf("500") }; var factor by remember { mutableStateOf("9") }
    var deleteStep by remember { mutableStateOf(0) }; var deleteHasRecords by remember { mutableStateOf(false) }
    fun add() { selected = null; form = true; number = ""; name = ""; base = "500"; factor = "9"; vm.error = null }
    fun edit() { selected?.let { b -> form = true; number = b.number; name = b.name; base = b.baseRevenue.toString(); factor = b.cashFactor.toString(); vm.error = null } }
    Scaffold(topBar = { TopAppBar(title = { Text("اختيار المخبز") }, actions = { IconButton({ nav.navigate("settings") }) { Icon(Icons.Default.Settings, "الإعدادات") } }) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (list.isEmpty()) "لا توجد مخابز. يرجى إضافة مخبز جديد" else "اختر المخبز", fontWeight = FontWeight.Bold)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(list, key = { it.id }) { b ->
                    Button({ selected = b; form = false; vm.error = null }, Modifier.fillMaxWidth()) { Text("${b.number} - ${b.name}") }
                }
            }
            if (form) Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumericField("رقم المخبز *", number) { number = it }
                OutlinedTextField(name, { name = it }, label = { Text("اسم المخبز *") }, modifier = Modifier.fillMaxWidth())
                Text("إعدادات الحساب", fontWeight = FontWeight.Bold)
                NumericField("قيمة الإيراد الأساسية *", base) { base = it }
                NumericField("معامل البيع النقدي *", factor) { factor = it }
            } }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            if (form) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button({ vm.save(selected?.id ?: 0L, number, name, base, factor) { form = false; selected = null } }, Modifier.weight(1f)) { Icon(Icons.Default.Save, null); Text("حفظ") }
                OutlinedButton({ form = false; vm.error = null }, Modifier.weight(1f)) { Text("إلغاء") }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(::add, Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text("إضافة") }
                Button(::edit, enabled = selected != null, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Edit, null); Text("تعديل") }
                Button({ selected?.let { b -> vm.hasRecords(b.id) { has -> deleteHasRecords = has; deleteStep = 1 } } }, enabled = selected != null, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Delete, null); Text("حذف") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button({ selected?.let { nav.navigate("home/${it.id}") } }, enabled = selected != null, Modifier.weight(1f)) { Icon(Icons.Default.FolderOpen, null); Text("فتح بيانات المخبز") }
                OutlinedButton({ nav.navigate("settings") }, Modifier.weight(1f)) { Text("الإعدادات") }
            }
        }
    }
    if (deleteStep > 0) AlertDialog(
        onDismissRequest = { deleteStep = 0 },
        title = { Text(if (deleteStep == 1) "تأكيد حذف المخبز" else "تأكيد الحذف مرة أخرى") },
        text = { Text(if (deleteStep == 1 && deleteHasRecords) "سيتم حذف المخبز وجميع سجلاته اليومية المرتبطة به. هل أنت متأكد؟" else if (deleteStep == 1) "هل أنت متأكد من حذف هذا المخبز؟" else "هذا إجراء نهائي، وسيتم حذف سجلات المخبز نهائياً. هل تريد المتابعة؟") },
        confirmButton = { TextButton({ if (deleteStep == 1 && deleteHasRecords) deleteStep = 2 else { selected?.let { vm.delete(it) { selected = null } }; deleteStep = 0 } }) { Text("تأكيد") } },
        dismissButton = { TextButton({ deleteStep = 0 }) { Text("إلغاء") } }
    )
}

private class RecordVM(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    var bakery by mutableStateOf<Bakery?>(null); var rec by mutableStateOf<DailyRecord?>(null); var error by mutableStateOf<String?>(null)
    fun load(id: Long, date: String) = viewModelScope.launch { bakery = db.bakeryDao().get(id); rec = db.dailyDao().byDate(id, date); error = null }
    fun save(r: DailyRecord, update: Boolean, done: () -> Unit) = viewModelScope.launch {
        error = null
        when {
            r.date > todayDb() -> { error = "لا يمكن إنشاء أو تعديل سجل بتاريخ مستقبلي"; return@launch }
            r.revenue < 0 || r.flourKg < 0 || r.loaves < 0 || r.workersMeal < 0 || r.waste < 0 || r.creditSold < 0 -> { error = "لا يمكن إدخال قيمة سالبة"; return@launch }
            r.baseRevenueUsed <= 0 || r.cashFactorUsed <= 0 -> { error = "إعدادات الحساب غير صحيحة"; return@launch }
        }
        val existing = db.dailyDao().byDate(r.bakeryId, r.date)
        if (existing != null && existing.id != r.id) { error = "هذا السجل محفوظ مسبقاً"; return@launch }
        try {
            if (update) db.dailyDao().update(r) else { val id = db.dailyDao().insert(r); rec = r.copy(id = id); done(); return@launch }
            rec = r; done()
        } catch (_: SQLiteConstraintException) { error = "هذا السجل محفوظ مسبقاً" } catch (_: Exception) { error = "تعذر حفظ السجل" }
    }
    fun search(id: Long, date: String) = viewModelScope.launch { db.dailyDao().byDate(id, date)?.let { rec = it; error = null } ?: run { error = "لا يوجد سجل في هذا التاريخ" } }
    fun delete(r: DailyRecord, done: () -> Unit) = viewModelScope.launch { try { db.dailyDao().delete(r); rec = null; error = null; done() } catch (_: Exception) { error = "تعذر حذف السجل" } }
}

private data class Draft(val date: String, val flour: String, val loaves: String, val meal: String, val waste: String, val credit: String, val revenue: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home(nav: NavController, id: Long, vm: RecordVM = viewModel()) {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(todayDb()) }
    var flour by remember { mutableStateOf("") }; var loaves by remember { mutableStateOf("") }; var meal by remember { mutableStateOf("70") }
    var waste by remember { mutableStateOf("") }; var credit by remember { mutableStateOf("") }; var revenue by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }; var newRecord by remember { mutableStateOf(false) }; var baseline by remember { mutableStateOf<Draft?>(null) }
    var restoreDate by remember { mutableStateOf(todayDb()) }; var deleteDialog by remember { mutableStateOf(false) }; var searchDialog by remember { mutableStateOf(false) }; var unsavedDialog by remember { mutableStateOf(false) }; var searchValue by remember { mutableStateOf(uiDate(todayDb())) }
    LaunchedEffect(id) { vm.load(id, todayDb()) }
    LaunchedEffect(vm.rec?.id) { vm.rec?.let { r -> selectedDate = r.date; flour = r.flourKg.toString(); loaves = r.loaves.toString(); meal = r.workersMeal.toString(); waste = r.waste.toString(); credit = r.creditSold.toString(); revenue = r.revenue.toString(); baseline = Draft(r.date, flour, loaves, meal, waste, credit, revenue) } }
    val b = vm.bakery
    val base = if (newRecord) b?.baseRevenue ?: 500L else vm.rec?.baseRevenueUsed ?: b?.baseRevenue ?: 500L
    val fac = if (newRecord) b?.cashFactor ?: 9L else vm.rec?.cashFactorUsed ?: b?.cashFactor ?: 9L
    val fl = flour.toLongOrNull() ?: 0L; val l = loaves.toLongOrNull() ?: 0L; val m = meal.toLongOrNull() ?: 0L; val w = waste.toLongOrNull() ?: 0L; val c = credit.toLongOrNull() ?: 0L; val rev = revenue.toLongOrNull() ?: 0L
    val calc = calculateRecord(fl, l, m, w, c, rev, base, fac)
    val dirty = editing && baseline != Draft(selectedDate, flour, loaves, meal, waste, credit, revenue)
    fun leave() = nav.popBackStack()
    fun requestLeave() { if (dirty) unsavedDialog = true else leave() }
    BackHandler { requestLeave() }
    Scaffold(topBar = { TopAppBar(title = { Text(b?.name ?: "بيانات المخبز") }, navigationIcon = { IconButton(::requestLeave) { Icon(Icons.Default.ArrowBack, "رجوع") } }) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("التاريخ", fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); if (editing) OutlinedButton({ context.pickDate(selectedDate) { selectedDate = it } }) { Icon(Icons.Default.CalendarMonth, null); Text("تغيير") } }
                Text(uiDate(selectedDate)); Text("اليوم: ${dayName(selectedDate)}")
            } }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item { NumericField("كمية الدقيق (كجم)", flour, enabled = editing) { flour = it } }
                item { NumericField("عدد الأقراص (حبة)", loaves, enabled = editing) { loaves = it } }
                item { NumericField("عشاء العمال (حبة)", meal, enabled = editing) { meal = it } }
                item { NumericField("التالف (حبة)", waste, enabled = editing) { waste = it } }
                item { NumericField("المباع آجل (حبة)", credit, enabled = editing) { credit = it } }
                item { NumericField("الإيراد (ريال) *", revenue, enabled = editing) { revenue = it } }
                item { MetricCard("المباع نقداً", "${formatNumber(calc.cashSold)} حبة") }
                item { MetricCard("الفرق", "${formatNumber(calc.difference)} حبة", if (calc.difference < 0) MaterialTheme.colorScheme.error else null) }
                item { MetricCard("معدل إنتاج الكيس", if (calc.bagRate == null) "غير متاح" else "${formatNumber(calc.bagRate)} حبة/كيس") }
            }
            vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            if (!editing) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Button({ restoreDate = selectedDate; newRecord = true; editing = true; selectedDate = todayDb(); flour = ""; loaves = ""; meal = "70"; waste = ""; credit = ""; revenue = ""; baseline = Draft(todayDb(), "", "", "70", "", "", ""); vm.error = null }, Modifier.weight(1f)) { Icon(Icons.Default.Add, null); Text("إضافة") }
                    Button({ restoreDate = selectedDate; newRecord = false; editing = true; baseline = Draft(selectedDate, flour, loaves, meal, waste, credit, revenue); vm.error = null }, enabled = vm.rec != null, Modifier.weight(1f)) { Icon(Icons.Default.Edit, null); Text("تعديل") }
                    Button({ deleteDialog = true }, enabled = vm.rec != null, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.Delete, null); Text("حذف") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    OutlinedButton({ searchDialog = true; searchValue = uiDate(todayDb()); vm.error = null }, Modifier.weight(1f)) { Icon(Icons.Default.Search, null); Text("بحث") }
                    Button({ nav.navigate("stats/$id") }, Modifier.weight(1f)) { Icon(Icons.Default.BarChart, null); Text("إحصاءات") }
                }
            } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Button({
                    if (revenue.isBlank()) { vm.error = "الإيراد حقل إجباري"; return@Button }
                    val record = DailyRecord(vm.rec?.id ?: 0L, id, selectedDate, fl, l, m, w, c, rev, base, fac)
                    vm.save(record, !newRecord) { editing = false; newRecord = false; baseline = Draft(selectedDate, flour, loaves, meal, waste, credit, revenue) }
                }, Modifier.weight(1f)) { Icon(Icons.Default.Save, null); Text("حفظ") }
                OutlinedButton({ editing = false; newRecord = false; vm.error = null; vm.load(id, restoreDate) }, Modifier.weight(1f)) { Text("إلغاء") }
            }
        }
    }
    if (deleteDialog) AlertDialog(onDismissRequest = { deleteDialog = false }, title = { Text("حذف السجل") }, text = { Text("هل أنت متأكد من حذف السجل (بتاريخ السجل المعروض) سيتم حذف السجل نهائياً؟") }, confirmButton = { TextButton({ deleteDialog = false; vm.rec?.let { vm.delete(it) {} } }) { Text("نعم") } }, dismissButton = { TextButton({ deleteDialog = false }) { Text("لا") } })
    if (searchDialog) AlertDialog(onDismissRequest = { searchDialog = false }, title = { Text("البحث عن سجل") }, text = { OutlinedButton({ context.pickDate(parseUiDate(searchValue) ?: todayDb()) { searchValue = uiDate(it) } }, Modifier.fillMaxWidth()) { Icon(Icons.Default.CalendarMonth, null); Spacer(Modifier.width(6.dp)); Text(searchValue) } }, confirmButton = { TextButton({ parseUiDate(searchValue)?.let { searchDialog = false; vm.search(id, it) } ?: run { vm.error = "اختر تاريخاً صحيحاً" } }) { Text("بحث") } }, dismissButton = { TextButton({ searchDialog = false }) { Text("إلغاء") } })
    if (unsavedDialog) AlertDialog(onDismissRequest = { unsavedDialog = false }, title = { Text("تغييرات غير محفوظة") }, text = { Text("لديك تغييرات غير محفوظة، هل تريد حفظها قبل الانتقال؟") }, confirmButton = { TextButton({ if (revenue.isBlank()) vm.error = "الإيراد حقل إجباري" else { val record = DailyRecord(vm.rec?.id ?: 0L, id, selectedDate, fl, l, m, w, c, rev, base, fac); vm.save(record, !newRecord) { unsavedDialog = false; editing = false; newRecord = false; leave() } } }) { Text("حفظ") } }, dismissButton = { Row { TextButton({ unsavedDialog = false; leave() }) { Text("خروج دون حفظ") }; TextButton({ unsavedDialog = false }) { Text("إلغاء") } } })
}

@Composable
private fun NumericField(label: String, value: String, enabled: Boolean = true, onValue: (String) -> Unit) = OutlinedTextField(value, { onValue(it.filter(Char::isDigit)) }, label = { Text(label) }, enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

@Composable
private fun MetricCard(title: String, value: String, valueColor: Color? = null) { Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title, fontWeight = FontWeight.Bold); Text(value, fontWeight = FontWeight.Bold, color = valueColor ?: MaterialTheme.colorScheme.primary) } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Stats(nav: NavController, id: Long) {
    val context = LocalContext.current; val db = remember { AppDatabase.get(context) }; val scope = rememberCoroutineScope()
    var from by remember { mutableStateOf("") }; var to by remember { mutableStateOf("") }; var output by remember { mutableStateOf<StatsResult?>(null) }; var error by remember { mutableStateOf<String?>(null) }; var searching by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text("الإحصاءات") }, navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, null) } }) }) { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { OutlinedButton({ context.pickDate(parseUiDate(from) ?: todayDb()) { from = uiDate(it) } }, Modifier.weight(1f)) { Text(if (from.isBlank()) "من تاريخ" else from) }; OutlinedButton({ context.pickDate(parseUiDate(to) ?: todayDb()) { to = uiDate(it) } }, Modifier.weight(1f)) { Text(if (to.isBlank()) "إلى تاريخ" else to) } }
            Button({
                error = null; val a = parseUiDate(from); val z = parseUiDate(to)
                if (a == null || z == null) error = "يرجى تحديد تاريخ البداية والنهاية"
                else if (a > z) error = "يجب أن يكون من تاريخ أقل من أو يساوي إلى تاريخ"
                else if (z > todayDb()) error = "لا يمكن أن تشمل الفترة تاريخاً مستقبلياً"
                else scope.launch { searching = true; val rows = db.dailyDao().between(id, a, z); searching = false; if (rows.isEmpty()) error = "لا توجد بيانات لعرضها" else output = calculateStats(rows, from, to) }
            }, enabled = !searching, Modifier.fillMaxWidth()) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(5.dp)); Text(if (searching) "جارٍ البحث..." else "بحث") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            output?.let { r -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { item { Text("الفترة: ${r.from} إلى ${r.to}", fontWeight = FontWeight.Bold) }; item { StatLine("عدد السجلات", r.count.toString()) }; item { StatLine("إجمالي كمية الدقيق", "${r.flour} كجم") }; item { StatLine("إجمالي عدد الأقراص", "${r.loaves} حبة") }; item { StatLine("إجمالي عشاء العمال", "${r.meal} حبة") }; item { StatLine("إجمالي التالف", "${r.waste} حبة") }; item { StatLine("إجمالي المباع آجل", "${r.credit} حبة") }; item { StatLine("إجمالي المباع نقداً", "${formatNumber(r.cash)} حبة") }; item { StatLine("إجمالي الإيراد", "${r.revenue} ريال") }; item { StatLine("إجمالي الفرق", "${formatNumber(r.difference)} حبة", if (r.difference < 0) MaterialTheme.colorScheme.error else null) }; item { StatLine("معدل إنتاج الكيس", if (r.rate == null) "غير متاح" else "${formatNumber(r.rate)} حبة/كيس") } } } ?: Spacer(Modifier.weight(1f))
            OutlinedButton({ nav.popBackStack() }, Modifier.fillMaxWidth()) { Text("رجوع") }
        }
    }
}

@Composable
private fun StatLine(title: String, value: String, valueColor: Color? = null) { Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); Text(value, fontWeight = FontWeight.Bold, color = valueColor ?: MaterialTheme.colorScheme.primary) } } }

@Composable
fun Settings(nav: NavController, dark: Boolean, onDark: (Boolean) -> Unit) { Scaffold(topBar = { TopAppBar(title = { Text("الإعدادات") }) }) { p -> Column(Modifier.fillMaxSize().padding(p).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("الوضع الليلي"); Switch(dark, onDark) } }; Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) { Text("معلومات التطبيق", fontWeight = FontWeight.Bold); Text("تطبيق المخابز – نسخة تجريبية"); Text("تصميم التطبيق عبدالعزيز سعيدان") } }; Spacer(Modifier.weight(1f)); Button({ nav.popBackStack() }, Modifier.fillMaxWidth()) { Text("العودة إلى اختيار المخبز") } } } }

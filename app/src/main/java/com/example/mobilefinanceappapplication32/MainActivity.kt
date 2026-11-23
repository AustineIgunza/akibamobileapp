package com.example.mobilefinanceappapplication32

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.mobilefinanceappapplication32.ui.theme.MobileFinanceAppApplication32Theme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MobileFinanceAppApplication32Theme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { AkibaApp() } } }
    }
}

private fun JSONArray.rows() = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONObject.str(key: String) = optString(key).takeUnless { it == "null" } ?: ""
private fun JSONObject.list(key: String) = optJSONArray(key)?.rows().orEmpty()
private fun money(value: Double, currency: String = "KES") = "$currency ${NumberFormat.getNumberInstance(Locale.US).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(value)}"
private fun JSONObject.money(key: String, currency: String = "KES") = money(optDouble(key), currency)
private fun body(vararg pairs: Pair<String, Any?>) = JSONObject().apply { pairs.forEach { (k, v) -> if (v != null) put(k, v) } }
private fun positive(value: String) = value.toDoubleOrNull()?.takeIf { it > 0 } ?: throw IllegalArgumentException("Enter an amount above zero.")
internal data class Option(val name: String, val value: String)
internal data class Input(val key: String, val label: String, val initial: String = "", val options: List<Option> = emptyList())

@Composable internal fun EntryDialog(title: String, inputs: List<Input>, close: () -> Unit, save: (Map<String, String>) -> Unit) {
    var values by remember(inputs) { mutableStateOf(inputs.associate { it.key to it.initial }) }
    AlertDialog(onDismissRequest = close, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            inputs.forEach { field ->
                if (field.options.isEmpty()) OutlinedTextField(values[field.key].orEmpty(), { values = values + (field.key to it) }, label = { Text(field.label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                else {
                    var expanded by remember(field.key) { mutableStateOf(false) }
                    Text(field.label, style = MaterialTheme.typography.labelMedium)
                    OutlinedButton({ expanded = true }, Modifier.fillMaxWidth()) { Text(field.options.firstOrNull { it.value == values[field.key] }?.name ?: "Choose") }
                    DropdownMenu(expanded, { expanded = false }) { field.options.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { values = values + (field.key to item.value); expanded = false }) } }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { save(values) }) { Text("Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable private fun ExpenseDialog(
    transactionType: String,
    walletOptions: List<Option>,
    categoryOptions: List<Option>,
    categories: List<JSONObject>,
    categorizer: AkibaCategorizer?,
    close: () -> Unit,
    save: (Map<String, String>) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var walletId by remember { mutableStateOf(walletOptions.firstOrNull()?.value.orEmpty()) }
    var categoryId by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var prediction by remember { mutableStateOf<AkibaCategorizer.Prediction?>(null) }
    var showExplanation by remember { mutableStateOf(false) }
    var walletExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }

    fun runPrediction() {
        val amt = amount.toDoubleOrNull()
        if (categorizer != null && amt != null && amt > 0) {
            try {
                val result = categorizer.categorize(amt, LocalDate.now(), description)
                prediction = result
                val match = categories.firstOrNull { it.str("name").equals(result.category, ignoreCase = true) && it.str("type") == transactionType }
                if (match != null) categoryId = match.str("id")
            } catch (_: Exception) { prediction = null }
        } else {
            prediction = null
        }
    }

    AlertDialog(onDismissRequest = close, title = { Text("Add $transactionType") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Wallet picker
            Text("Wallet", style = MaterialTheme.typography.labelMedium)
            OutlinedButton({ walletExpanded = true }, Modifier.fillMaxWidth()) { Text(walletOptions.firstOrNull { it.value == walletId }?.name ?: "Choose") }
            DropdownMenu(walletExpanded, { walletExpanded = false }) { walletOptions.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { walletId = item.value; walletExpanded = false }) } }

            // Amount field — triggers AI prediction on change
            OutlinedTextField(amount, {
                amount = it
                runPrediction()
            }, label = { Text("Amount") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            // Description — triggers AI prediction on change
            OutlinedTextField(description, { description = it; runPrediction() }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            // AI suggestion chip
            if (prediction != null && categorizer != null) {
                val p = prediction!!
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Suggested: ${p.category}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        if (!p.isConfident) {
                            Text("Not sure — please pick the right category below", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        // XAI toggle for project presentation
                        TextButton(onClick = { showExplanation = !showExplanation }, contentPadding = PaddingValues(0.dp)) {
                            Text(if (showExplanation) "Hide details" else "How was this decided?", style = MaterialTheme.typography.labelSmall)
                        }
                        if (showExplanation) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Confidence: ${"%.0f".format(p.confidence * 100)}%", style = MaterialTheme.typography.bodySmall)
                                p.allProbabilities.take(3).forEach { (name, prob) ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(name, style = MaterialTheme.typography.bodySmall)
                                        Text("${"%.0f".format(prob * 100)}%", style = MaterialTheme.typography.bodySmall)
                                    }
                                    LinearProgressIndicator(
                                        progress = { prob.toFloat().coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(4.dp)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(p.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Category picker — user can override AI suggestion
            Text("Category", style = MaterialTheme.typography.labelMedium)
            OutlinedButton({ categoryExpanded = true }, Modifier.fillMaxWidth()) { Text(categoryOptions.firstOrNull { it.value == categoryId }?.name ?: "Choose category") }
            DropdownMenu(categoryExpanded, { categoryExpanded = false }) { categoryOptions.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { categoryId = item.value; categoryExpanded = false }) } }
        }
    }, confirmButton = { TextButton(onClick = { save(mapOf("walletId" to walletId, "amount" to amount, "categoryId" to categoryId, "description" to description)) }) { Text("Save") } }, dismissButton = { TextButton(onClick = close) { Text("Cancel") } })
}

@Composable private fun AkibaApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val session = remember { AkibaSession(context) }
    val api = remember { AkibaApi(session) }
    val categorizer = remember { AkibaCategorizer(context) }
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(session.baseUrl) }
    var user by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var tab by remember { mutableStateOf("Home") }
    var dialog by remember { mutableStateOf("") }
    var register by remember { mutableStateOf(false) }
    var wallets by remember { mutableStateOf(emptyList<JSONObject>()) }
    var transactions by remember { mutableStateOf(emptyList<JSONObject>()) }
    var categories by remember { mutableStateOf(emptyList<JSONObject>()) }
    var goals by remember { mutableStateOf(emptyList<JSONObject>()) }
    var budgets by remember { mutableStateOf(emptyList<JSONObject>()) }
    var conversations by remember { mutableStateOf(emptyList<JSONObject>()) }
    var messages by remember { mutableStateOf(emptyList<JSONObject>()) }
    var conversationId by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf(JSONObject()) }
    var spending by remember { mutableStateOf(JSONObject()) }
    var cashflow by remember { mutableStateOf(emptyList<JSONObject>()) }

    fun run(work: suspend () -> Unit) { scope.launch {
        busy = true; error = ""
        try { work(); dialog = ""; refresh++ } catch (e: Exception) { error = e.message ?: "Something went wrong." }
        finally { busy = false }
    } }

    LaunchedEffect(refresh) {
        loading = true
        try {
            var lastConnectionError: Exception? = null
            for (attempt in 0..2) {
                try {
                    user = api.get("/api/auth/me").optJSONObject("user")
                    lastConnectionError = null
                    break
                } catch (e: ApiFailure) {
                    throw e
                } catch (e: Exception) {
                    lastConnectionError = e
                    if (attempt < 2) delay(1_000L * (attempt + 1))
                }
            }
            lastConnectionError?.let { throw it }
            val failures = mutableListOf<String>()
            suspend fun fetch(path: String, key: String): List<JSONObject> = try { api.get(path).list(key) }
                catch (e: Exception) { failures += "$key: ${e.message}"; emptyList() }
            wallets = fetch("/api/wallets", "wallets")
            transactions = fetch("/api/transactions?limit=100", "transactions")
            categories = fetch("/api/categories", "categories")
            goals = fetch("/api/goals", "goals")
            budgets = fetch("/api/budgets", "budgets")
            conversations = fetch("/api/advisor/conversations", "conversations")
            summary = try { api.get("/api/dashboard/summary").optJSONObject("summary") ?: JSONObject() }
                catch (e: Exception) { failures += "dashboard: ${e.message}"; JSONObject() }
            spending = try { api.get("/api/dashboard/spending-by-category") }
                catch (_: Exception) { JSONObject() }
            cashflow = fetch("/api/dashboard/cashflow", "months")
            if (conversationId.isNotBlank()) messages = try { api.get("/api/advisor/conversations/$conversationId").optJSONObject("conversation")?.list("messages").orEmpty() }
                catch (e: Exception) { failures += "advisor: ${e.message}"; emptyList() }
            if (failures.isNotEmpty()) error = failures.joinToString("\n")
        } catch (e: ApiFailure) { user = null; if (e.status != 401) error = e.message.orEmpty() }
        catch (e: Exception) { user = null; error = e.message.orEmpty() }
        loading = false
    }

    val currency = user?.str("preferredCurrency")?.ifBlank { "KES" } ?: "KES"
    val walletOptions = wallets.map { Option(it.str("name"), it.str("id")) }
    val expenseOptions = categories.filter { it.str("type") == "expense" }.map { Option(it.str("name"), it.str("id")) }
    Scaffold(topBar = { Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Akiba", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        TextButton({ dialog = "settings" }) { Text("Settings") }
    } }, bottomBar = { if (user != null) NavigationBar { listOf("Home", "Activity", "Wallets", "Goals", "Advisor").forEach { page ->
        NavigationBarItem(selected = tab == page, onClick = { tab = page }, icon = { Text(page.take(1)) }, label = { Text(page) })
    } } }) { inset -> Column(Modifier.fillMaxSize().padding(inset)) {
        if (error.isNotBlank()) Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Column(Modifier.padding(12.dp)) {
            Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { error = ""; refresh++ }) { Text("Retry connection") }
        } }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (loading) CircularProgressIndicator(Modifier.padding(24.dp))
        else if (user == null) {
            var name by remember(register) { mutableStateOf("") }; var email by remember(register) { mutableStateOf("") }; var password by remember(register) { mutableStateOf("") }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Spacer(Modifier.height(32.dp)); Text("Your money, in focus.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Track spending, grow savings, and get advice grounded in your finances.")
                if (register) OutlinedTextField(name, { name = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Button(onClick = { run {
                    if (email.isBlank() || password.isBlank() || register && name.isBlank()) throw IllegalArgumentException("Complete all fields.")
                    api.post(if (register) "/api/auth/register" else "/api/auth/login", if (register) body("fullName" to name, "email" to email, "password" to password, "preferredCurrency" to "KES") else body("email" to email, "password" to password))
                } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (register) "Create account" else "Sign in") }
                TextButton({ register = !register }) { Text(if (register) "Already have an account? Sign in" else "New here? Create an account") }
            }
        } else when (tab) {
            "Home" -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Title("Hello, ${user?.str("fullName")?.substringBefore(' ')}", "Here's your money at a glance") }
                item { InfoCard("Total balance", summary.money("totalBalance", currency), "Across ${summary.optInt("walletCount")} wallets") }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ dialog = "expense" }) { Text("Add expense") }; OutlinedButton({ dialog = "income" }) { Text("Add income") } } }
                item { InfoCard("Income this month", summary.optJSONObject("income")?.money("month", currency) ?: money(0.0, currency)) }
                item { InfoCard("Spent this month", summary.optJSONObject("expense")?.money("month", currency) ?: money(0.0, currency)) }
                item { InfoCard("Active goals", "${summary.optJSONObject("goals")?.optInt("active") ?: 0}", "Saved ${summary.optJSONObject("goals")?.money("totalSaved", currency) ?: money(0.0, currency)}") }
                item { Text("Spending by category", style = MaterialTheme.typography.titleLarge) }
                items(spending.list("categories").take(5)) { category -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(category.str("name"), style = MaterialTheme.typography.titleMedium)
                    Text(category.money("amount", currency))
                    LinearProgressIndicator(progress = { (category.optDouble("percentage") / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                } } }
                item { Text("Cash flow", style = MaterialTheme.typography.titleLarge) }
                items(cashflow.takeLast(3).reversed()) { month -> InfoCard(month.str("month"), "In ${month.money("income", currency)}", "Out ${month.money("expense", currency)}") }
                item { Text("Recent activity", style = MaterialTheme.typography.titleLarge) }
                items(transactions.take(5)) { TransactionRow(it, wallets, categories, currency) }
            }
            "Activity" -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Title("Transactions", "Every movement, in one place") }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ dialog = "expense" }) { Text("Add expense") }; OutlinedButton({ dialog = "income" }) { Text("Add income") } } }
                if (transactions.isEmpty()) item { InfoCard("No transactions yet", "Add your first income or expense.") }
                items(transactions) { transaction -> Column {
                    TransactionRow(transaction, wallets, categories, currency)
                    if (transaction.str("type") in listOf("income", "expense")) TextButton({ dialog = "editTransaction:${transaction.str("id")}" }) { Text("Edit transaction") }
                } }
            }
            "Wallets" -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Title("Wallets", "Every place your money lives") }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ dialog = "wallet" }) { Text("Add wallet") }; OutlinedButton({ dialog = "transfer" }) { Text("Transfer") } } }
                if (wallets.isEmpty()) item { InfoCard("No wallets yet", "Create your first wallet.") }
                items(wallets) { wallet -> Column {
                    InfoCard(wallet.str("name"), wallet.money("currentBalance", wallet.str("currency")), wallet.str("type").replace('_', ' '))
                    TextButton({ dialog = "editWallet:${wallet.str("id")}" }) { Text("Edit wallet") }
                } }
            }
            "Goals" -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Title("Goals & budgets", "Make room for what matters") }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ dialog = "goal" }) { Text("New goal") }; OutlinedButton({ dialog = "budget" }) { Text("New budget") } } }
                item { Text("Savings goals", style = MaterialTheme.typography.titleLarge) }
                if (goals.isEmpty()) item { InfoCard("No goals yet", "Set a target to get started.") }
                items(goals) { goal -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(goal.str("name"), style = MaterialTheme.typography.titleMedium)
                    Text("${goal.money("currentAmount", currency)} of ${goal.money("targetAmount", currency)}")
                    Text("${goal.optInt("progress")}% · ${goal.str("status")}")
                    LinearProgressIndicator(progress = { (goal.optDouble("progress") / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    if (goal.str("status") == "active") TextButton({ dialog = "contribute:${goal.str("id")}" }) { Text("Contribute") }
                    TextButton({ dialog = "editGoal:${goal.str("id")}" }) { Text("Edit goal") }
                } } }
                item { Text("Monthly budgets", style = MaterialTheme.typography.titleLarge) }
                if (budgets.isEmpty()) item { InfoCard("No budgets yet", "Set a monthly spending limit.") }
                items(budgets) { budget -> Column {
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(budget.str("categoryName"), style = MaterialTheme.typography.titleMedium)
                        Text("${budget.money("spent", currency)} of ${budget.money("amount", currency)}")
                        LinearProgressIndicator(progress = { (budget.optDouble("percentage") / 100.0).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        Text("${budget.money("remaining", currency)} remaining")
                    } }
                    TextButton({ dialog = "editBudget:${budget.str("id")}" }) { Text("Change limit") }
                } }
            }
            "Advisor" -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Title("Akiba advisor", "Guidance based on your finances")
                OutlinedButton({ dialog = "conversation" }) { Text("New conversation") }
                if (conversationId.isBlank()) LazyColumn { items(conversations) { item -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { conversationId = item.str("id"); refresh++ }) { Text(item.str("title"), Modifier.padding(16.dp)) } } }
                else {
                    TextButton({ conversationId = ""; messages = emptyList() }) { Text("← Conversations") }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(messages) { msg -> InfoCard(if (msg.str("role") == "user") "You" else "Akiba", msg.str("content")) } }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(draft, { draft = it }, label = { Text("Ask a question") }, modifier = Modifier.weight(1f))
                        Button(onClick = { val question = draft.trim(); run { if (question.isBlank()) throw IllegalArgumentException("Write a question first."); api.post("/api/advisor/conversations/$conversationId/messages", body("content" to question)); draft = "" } }, enabled = !busy) { Text("Send") }
                    }
                }
            }
        }
    } }

    val editWallet = wallets.firstOrNull { dialog.endsWith(it.str("id")) }
    val editTransaction = transactions.firstOrNull { dialog.endsWith(it.str("id")) }
    val editGoal = goals.firstOrNull { dialog.endsWith(it.str("id")) }
    val editBudget = budgets.firstOrNull { dialog.endsWith(it.str("id")) }
    when {
        dialog == "settings" -> AlertDialog(onDismissRequest = { address = session.baseUrl; dialog = "" }, title = { Text("Settings") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("API server address")
            OutlinedTextField(address, { address = it }, label = { Text("https://…") }, modifier = Modifier.fillMaxWidth())
            if (user != null) OutlinedButton(onClick = { run { api.post("/api/auth/logout"); session.clearCookie(); user = null; conversationId = "" } }) { Text("Sign out") }
        } }, confirmButton = { TextButton(onClick = { val saved = address.trim().trimEnd('/'); if (saved != session.baseUrl) { session.clearCookie(); session.baseUrl = saved; address = saved; user = null; refresh++ }; dialog = "" }) { Text("Save") } }, dismissButton = { TextButton({ address = session.baseUrl; dialog = "" }) { Text("Cancel") } })
        dialog == "wallet" -> EntryDialog("Add wallet", listOf(Input("name", "Name"), Input("type", "Type", "cash", listOf(Option("Cash", "cash"), Option("Mobile money", "mobile_money"), Option("Bank", "bank"), Option("Savings", "savings"), Option("Other", "custom"))), Input("openingBalance", "Opening balance", "0")), { dialog = "" }) { f -> run {
            api.post("/api/wallets", body("name" to f["name"], "type" to f["type"], "currency" to currency, "openingBalance" to (f["openingBalance"]?.toDoubleOrNull() ?: throw IllegalArgumentException("Enter a valid balance."))))
        } }
        dialog.startsWith("editWallet:") && editWallet != null -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(editWallet.str("name")) }, text = { Text("Manage this wallet") }, confirmButton = { TextButton({ dialog = "renameWallet:${editWallet.str("id")}" }) { Text("Rename") } }, dismissButton = { TextButton({ dialog = "archiveWallet:${editWallet.str("id")}" }) { Text("Archive") } })
        dialog.startsWith("renameWallet:") && editWallet != null -> EntryDialog("Rename wallet", listOf(Input("name", "Name", editWallet.str("name"))), { dialog = "" }) { f -> run { api.patch("/api/wallets/${editWallet.str("id")}", body("name" to f["name"])) } }
        dialog.startsWith("archiveWallet:") && editWallet != null -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Archive wallet?") }, text = { Text("This wallet will leave your active list. Its history remains saved.") }, confirmButton = { TextButton({ run { api.delete("/api/wallets/${editWallet.str("id")}") } }) { Text("Archive") } }, dismissButton = { TextButton({ dialog = "" }) { Text("Cancel") } })
        dialog == "expense" || dialog == "income" -> { val transactionType = dialog; ExpenseDialog(
            transactionType = transactionType,
            walletOptions = walletOptions,
            categoryOptions = listOf(Option("None", "")) + categories.filter { it.str("type") == transactionType }.map { Option(it.str("name"), it.str("id")) },
            categories = categories,
            categorizer = if (transactionType == "expense") categorizer else null,
            close = { dialog = "" }
        ) { f -> run {
            if (f["walletId"].isNullOrBlank()) throw IllegalArgumentException("Create a wallet first.")
            api.post("/api/transactions", body("type" to transactionType, "walletId" to f["walletId"], "amount" to positive(f["amount"].orEmpty()), "categoryId" to f["categoryId"]?.takeIf { it.isNotBlank() }, "description" to f["description"]?.takeIf { it.isNotBlank() }))
        } } }
        dialog.startsWith("editTransaction:") && editTransaction != null -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Transaction") }, text = { Text("${editTransaction.money("amount", currency)} · ${editTransaction.str("description")}") }, confirmButton = { TextButton({ dialog = "changeTransaction:${editTransaction.str("id")}" }) { Text("Edit") } }, dismissButton = { TextButton({ dialog = "removeTransaction:${editTransaction.str("id")}" }) { Text("Delete") } })
        dialog.startsWith("changeTransaction:") && editTransaction != null -> EntryDialog("Edit transaction", listOf(Input("amount", "Amount", editTransaction.optDouble("amount").toString()), Input("description", "Description", editTransaction.str("description"))), { dialog = "" }) { f -> run {
            api.patch("/api/transactions/${editTransaction.str("id")}", body("amount" to positive(f["amount"].orEmpty()), "description" to f["description"].orEmpty()))
        } }
        dialog.startsWith("removeTransaction:") && editTransaction != null -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Delete transaction?") }, text = { Text("The wallet balance will be adjusted by the server.") }, confirmButton = { TextButton({ run { api.delete("/api/transactions/${editTransaction.str("id")}") } }) { Text("Delete") } }, dismissButton = { TextButton({ dialog = "" }) { Text("Cancel") } })
        dialog == "transfer" -> EntryDialog("Transfer", listOf(Input("fromWalletId", "From", walletOptions.firstOrNull()?.value.orEmpty(), walletOptions), Input("toWalletId", "To", walletOptions.getOrNull(1)?.value.orEmpty(), walletOptions), Input("amount", "Amount"), Input("description", "Note (optional)")), { dialog = "" }) { f -> run {
            if (f["fromWalletId"].isNullOrBlank() || f["toWalletId"].isNullOrBlank() || f["fromWalletId"] == f["toWalletId"]) throw IllegalArgumentException("Choose two different wallets.")
            api.post("/api/transfers", body("fromWalletId" to f["fromWalletId"], "toWalletId" to f["toWalletId"], "amount" to positive(f["amount"].orEmpty()), "description" to f["description"]?.takeIf { it.isNotBlank() }))
        } }
        dialog == "goal" -> EntryDialog("New goal", listOf(Input("name", "Name"), Input("targetAmount", "Target amount"), Input("deadline", "Deadline YYYY-MM-DD (optional)")), { dialog = "" }) { f -> run {
            api.post("/api/goals", body("name" to f["name"], "targetAmount" to positive(f["targetAmount"].orEmpty()), "deadline" to f["deadline"]?.takeIf { it.isNotBlank() }))
        } }
        dialog.startsWith("editGoal:") && editGoal != null -> EntryDialog("Edit goal", listOf(Input("name", "Name", editGoal.str("name")), Input("targetAmount", "Target amount", editGoal.optDouble("targetAmount").toString()), Input("status", "Status", editGoal.str("status"), listOf("active", "paused", "completed", "cancelled").map { Option(it.replaceFirstChar(Char::uppercase), it) })), { dialog = "" }) { f -> run {
            api.patch("/api/goals/${editGoal.str("id")}", body("name" to f["name"], "targetAmount" to positive(f["targetAmount"].orEmpty()), "status" to f["status"]))
        } }
        dialog.startsWith("contribute:") -> { val goalId = dialog.substringAfter(':'); EntryDialog("Contribute", listOf(Input("amount", "Amount"), Input("walletId", "Take from wallet (optional)", "", listOf(Option("No wallet", "")) + walletOptions)), { dialog = "" }) { f -> run {
            api.post("/api/goals/$goalId/contributions", body("amount" to positive(f["amount"].orEmpty()), "walletId" to f["walletId"]?.takeIf { it.isNotBlank() }))
        } } }
        dialog == "budget" -> EntryDialog("Monthly budget", listOf(Input("categoryId", "Expense category", expenseOptions.firstOrNull()?.value.orEmpty(), expenseOptions), Input("amount", "Monthly limit")), { dialog = "" }) { f -> run {
            if (f["categoryId"].isNullOrBlank()) throw IllegalArgumentException("Choose an expense category.")
            api.post("/api/budgets", body("categoryId" to f["categoryId"], "amount" to positive(f["amount"].orEmpty())))
        } }
        dialog.startsWith("editBudget:") && editBudget != null -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text(editBudget.str("categoryName")) }, text = { Text("Monthly limit: ${editBudget.money("amount", currency)}") }, confirmButton = { TextButton({ dialog = "changeBudget:${editBudget.str("id")}" }) { Text("Change") } }, dismissButton = { TextButton({ dialog = "removeBudget:${editBudget.str("id")}" }) { Text("Remove") } })
        dialog.startsWith("changeBudget:") && editBudget != null -> EntryDialog("Change budget", listOf(Input("amount", "Monthly limit", editBudget.optDouble("amount").toString())), { dialog = "" }) { f -> run { api.patch("/api/budgets/${editBudget.str("id")}", body("amount" to positive(f["amount"].orEmpty()))) } }
        dialog.startsWith("removeBudget:") && editBudget != null -> AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Remove budget?") }, text = { Text("The category and its transactions will remain available.") }, confirmButton = { TextButton({ run { api.delete("/api/budgets/${editBudget.str("id")}") } }) { Text("Remove") } }, dismissButton = { TextButton({ dialog = "" }) { Text("Cancel") } })
        dialog == "conversation" -> EntryDialog("New conversation", listOf(Input("title", "Topic (optional)")), { dialog = "" }) { f -> run {
            conversationId = api.post("/api/advisor/conversations", body("title" to f["title"]?.takeIf { it.isNotBlank() })).optJSONObject("conversation")?.str("id").orEmpty()
        } }
    }
}

@Composable private fun Title(title: String, subtitle: String) { Column(Modifier.padding(vertical = 8.dp)) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
} }
@Composable private fun InfoCard(title: String, value: String, detail: String = "") { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
    Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    if (detail.isNotBlank()) Text(detail)
} } }
@Composable private fun TransactionRow(item: JSONObject, wallets: List<JSONObject>, categories: List<JSONObject>, currency: String) { Card(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            val category = categories.firstOrNull { it.str("id") == item.str("categoryId") }?.str("name").orEmpty()
            Text(item.str("description").ifBlank { category.ifBlank { item.str("type").replace('_', ' ') } }, fontWeight = FontWeight.Medium)
            Text("${wallets.firstOrNull { it.str("id") == item.str("walletId") }?.str("name").orEmpty()} · ${item.str("transactionDate").take(10)}", style = MaterialTheme.typography.bodySmall)
        }
        Text((if (item.str("type") in listOf("income", "transfer_in")) "+" else "−") + item.money("amount", currency))
    }
} }

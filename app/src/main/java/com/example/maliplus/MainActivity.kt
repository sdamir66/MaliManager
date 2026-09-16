@Composable
fun FinanceApp(db: AppDb) {
    var account by remember { mutableStateOf<Account?>(null) }
    var good by remember { mutableStateOf<Good?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    BackHandler(enabled = account != null || good != null || showSettings) {
        when {
            account != null -> account = null
            good != null -> good = null
            showSettings -> showSettings = false
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        when {
            account != null -> AccountScreen(db, account!!) { account = null }
            good != null -> GoodScreen(db, good!!) { good = null }
            showSettings -> BackupScreen(db) { showSettings = false }
            else -> MainScreen(
                db = db,
                onOpenAccount = { account = it },
                onOpenGood = { good = it },
                onOpenSettings = { showSettings = true },
                onAddAccount = { },
                onAddGood = { }
            )
        }
    }
}

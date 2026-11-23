# Integration test results — 2026-11-18

- PostgreSQL: connection, eight migrations, twelve tables, constraints and wallet reconciliation passed.
- API: temporary account registration/login; wallet create/rename/archive; income create/edit/delete; expense entry; insufficient-funds rejection; transfers; goal contributions; budget create/update/delete; dashboard totals; advisor conversation creation; persistence after login passed.
- Financial assertions: KES 1,000 opening cash; KES 100 expense; KES 200 transfer; KES 50 contribution left cash at KES 650, bank at KES 200, combined balance KES 850. Dashboard expenses were KES 150 and income KES 0 after deleting test income.
- All temporary accounts and their records were removed. Existing financial data was preserved.
- Device check: adb reported no connected devices; no configured AVD was found. An x86_64 system image and WHPX acceleration are available, but the SDK has no avdmanager tool installed. Android UI and real AI replies remain untested.

Re-run backend checks from this folder with `node database-check.cjs`, `node api-database-check.cjs`, and `node financial-flows-check.cjs`. These use the sibling backend's dependencies and environment without printing credentials. API checks require the backend on localhost:4000.

- Android verification: assembleDebug and testDebugUnitTest completed successfully. Device UI tests still require an AVD to be configured in Android Studio or a connected phone.


Online database verification — 2026-11-18

The commented Supabase connection in the backend environment was found to work. The active local backend was restarted with DATABASE_URL supplied from that connection in its process environment; the backend environment file was not edited. Restart it using node Start-Online-Backend.cjs from this Android project after stopping any other backend on port 4000.

Registration, logout, login and all tested protected list/dashboard routes passed against Supabase. The temporary authentication test account was removed.

The configured Railway API URL returned HTTP 404 Application not found. Phone testing currently requires the computer's local backend: set the Android Settings API address to http://192.168.2.67:4000 on the same Wi-Fi. 10.0.2.2 is reserved for the Android emulator and cannot reach this backend from a physical phone. A working hosted HTTPS API is still needed for use away from this computer.

Online financial write tests also passed: wallets, transactions, transfers, goal contributions, budgets, dashboard balance assertions and persistence after login. All temporary test records were removed.

# Akiba for Android

Native Android client for the Akiba finance API. Built with Kotlin and Jetpack Compose.

## Run locally

1. Start the Express API from the `financial-ai-advisor-` project on port 4000, with its database and environment configured.
2. Open this project in Android Studio and run the `app` configuration on an emulator. The `installDebug` task forwards emulator port 4000 to the API on this computer, and the debug app connects through `http://127.0.0.1:4000`.
3. For a physical device or deployed API, open **Settings** and enter the API base URL. Release builds require HTTPS.

The app reuses the API's accounts and data. Its session cookie is encrypted with an Android Keystore key before storage on the device. The app never stores the AI provider key.

## Included flows

- Register, sign in, restore session, sign out
- Dashboard balance, monthly income and spending, category spending, and cash flow
- Create, rename, and archive wallets
- Add, edit, and delete income or expense transactions
- Transfer between wallets
- Create and edit savings goals, and record contributions
- Create, change, and remove monthly budgets
- Create advisor conversations and exchange messages

## Integration notes

The ZIP snapshot supplies the API contracts, but no live backend address. The backend must be reachable from the device to use authenticated features. Password recovery is not exposed by that API. The ZIP's advisor documentation names Claude while its implementation reads DeepSeek configuration; verify the deployed advisor before relying on chat.

package com.example.mobilefinanceappapplication32

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

class EntryDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun typedWalletNameIsVisibleImmediately() {
        compose.setContent {
            EntryDialog(
                title = "Add wallet",
                inputs = listOf(Input("name", "Name")),
                close = {},
                save = {},
            )
        }

        compose.onNode(hasSetTextAction()).performTextInput("Everyday cash")
        compose.onNodeWithText("Everyday cash").assertExists()
    }
}

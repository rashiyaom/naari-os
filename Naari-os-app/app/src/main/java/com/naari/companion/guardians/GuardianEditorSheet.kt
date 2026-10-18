package com.naari.companion.guardians

import android.Manifest
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.naari.companion.designsystem.InlineError
import com.naari.companion.designsystem.NaariBottomSheet
import com.naari.companion.designsystem.NaariSpacing
import com.naari.companion.designsystem.PrimaryButton
import com.naari.companion.designsystem.SecondaryButton

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun GuardianEditorSheet(
    slotIndex: Int,
    name: String,
    phone: String,
    phoneError: String?,
    remainingBytes: Int,
    isSaving: Boolean,
    isSaveEnabled: Boolean,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onContactPicked: (name: String, phone: String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionDeniedNotice by remember { mutableStateOf(false) }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri ->
        if (contactUri != null) {
            try {
                val cursor = context.contentResolver.query(
                    contactUri,
                    arrayOf(
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.DISPLAY_NAME,
                        ContactsContract.Contacts.HAS_PHONE_NUMBER
                    ),
                    null,
                    null,
                    null
                )
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIndex = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        val idIndex = c.getColumnIndex(ContactsContract.Contacts._ID)
                        val hasPhoneIndex = c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                        val pickedName = if (nameIndex >= 0) c.getString(nameIndex).orEmpty() else ""
                        val contactId = if (idIndex >= 0) c.getString(idIndex) else ""
                        val hasPhone = if (hasPhoneIndex >= 0) c.getInt(hasPhoneIndex) > 0 else false

                        var pickedPhone = ""
                        if (hasPhone && contactId.isNotEmpty()) {
                            val phoneCursor = context.contentResolver.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                arrayOf(contactId),
                                null
                            )
                            phoneCursor?.use { pc ->
                                if (pc.moveToFirst()) {
                                    val numIdx = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    if (numIdx >= 0) {
                                        pickedPhone = pc.getString(numIdx).orEmpty()
                                    }
                                }
                            }
                        }
                        onContactPicked(pickedName, pickedPhone)
                    }
                }
            } catch (_: SecurityException) {
                permissionDeniedNotice = true
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDeniedNotice = false
            contactPickerLauncher.launch(null)
        } else {
            permissionDeniedNotice = true
        }
    }

    NaariBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = NaariSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(NaariSpacing.md)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(NaariSpacing.xs)) {
                Text(
                    text = if (slotIndex == 0) "Primary Guardian" else "Secondary Guardian",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (slotIndex == 0) {
                        "Emergency SMS and primary phone call recipient when SOS is triggered"
                    } else {
                        "Secondary fallback recipient if primary guardian is unavailable"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Contact picker launcher button
            SecondaryButton(
                text = "Pick from Contacts",
                onClick = {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_CONTACTS
                    ) == PackageManager.PERMISSION_GRANTED

                    if (hasPerm) {
                        contactPickerLauncher.launch(null)
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                },
                contentDescription = "Import contact from phone address book",
                modifier = Modifier.fillMaxWidth()
            )

            if (permissionDeniedNotice) {
                Text(
                    text = "Contacts permission not granted. Please enter name and phone manually.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Name field
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Guardian Name") },
                    placeholder = { Text("e.g. Maya") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$remainingBytes UTF-8 bytes remaining (max 32)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (remainingBytes == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.End)
                )
            }

            // Phone field
            Column {
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    label = { Text("Phone Number") },
                    placeholder = { Text("+15555550101") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = phoneError != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (phoneError != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    InlineError(message = phoneError)
                }
            }

            // Helper hint if not complete
            if (!isSaveEnabled && (name.isNotBlank() || phone.isNotBlank())) {
                Text(
                    text = if (name.isBlank()) "Please enter a name for the guardian."
                    else if (phone.isBlank()) "Please enter a valid phone number."
                    else if (phoneError != null) phoneError
                    else "Enter complete details to save.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = NaariSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(NaariSpacing.sm)
            ) {
                SecondaryButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    contentDescription = "Cancel guardian editing"
                )
                PrimaryButton(
                    text = if (isSaving) "Saving..." else "Save Guardian",
                    onClick = onSave,
                    enabled = isSaveEnabled && !isSaving,
                    modifier = Modifier.weight(1f),
                    contentDescription = "Save guardian details and sync with watch"
                )
            }
        }
    }
}

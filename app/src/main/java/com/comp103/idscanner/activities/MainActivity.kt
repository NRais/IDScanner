/**
 * @author Isaac Young
 */
package com.comp103.idscanner.activities

import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.bold
import androidx.recyclerview.widget.LinearLayoutManager
import com.comp103.idscanner.Id
import com.comp103.idscanner.R
import com.comp103.idscanner.databinding.MainActivityBinding
import com.comp103.idscanner.databinding.ManualInputBinding
import com.comp103.idscanner.factories.*
import com.comp103.idscanner.itemAdapter.ItemAdapter
import com.comp103.idscanner.util.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.integration.android.IntentIntegrator


/**
 * Main Activity Class
 */
class MainActivity : AppCompatActivity() {

    private lateinit var g: MainActivityBinding
    private lateinit var adapter: ItemAdapter
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var regexOptions: OptionalRegex

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        g = MainActivityBinding.inflate(layoutInflater)
        val view: View = g.root
        setContentView(view)
        setSupportActionBar(g.toolbar)

        getSharedPreferences(this)?.let {
            sharedPreferences = it
        }

        // Create adapter from sharedPreferences
        adapter = if (sharedPreferences.getString(getString(R.string.sp_items), null) != null) {
            itemAdapterFromString(
                this,
                sharedPreferences.getString(
                    getString(R.string.sp_items),
                    null
                )!!
            )
        } else {
            emptyItemAdapter(this)
        }

        // Attach the adapter to the recyclerview to populate items
        g.rvItems.adapter = adapter
        adapter.notifyDataSetChanged()
        // Set layout manager to position the items
        g.rvItems.layoutManager = LinearLayoutManager(this)

        g.fab.setOnClickListener {
            initiateScan()
        }
    }

    /**
     * Ask the user if they are sure that all the IDs should be cleared
     */
    private fun askToClearIds() {
        // Use the Builder class for convenient dialog construction
        val builder =
            MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialog_Rounded)

        builder.setMessage(
            "Are you sure you wish to clear all IDs?"
        )
            .setPositiveButton(
                "Delete"
            ) { _: DialogInterface?, _: Int ->
                adapter.reset()
                clearData(sharedPreferences, getString(R.string.sp_items))
            }
            .setNegativeButton(
                "Cancel"
            ) { _: DialogInterface?, _: Int -> }
        val dialog = builder.create()
        dialog.show()
    }

    /**
     * Manually add an entry
     */
    private fun addManually() {
        // Use the Builder class for convenient dialog construction
        val builder = MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialog_Rounded)
        val manualG: ManualInputBinding = ManualInputBinding.inflate(layoutInflater)

        manualG.editText.requestFocus()

        builder.setMessage("Student ID:")
            .setView(manualG.root)
            .setPositiveButton(
                "Ok"
            ) { _: DialogInterface?, _: Int ->
                addItem(Id(manualG.editText.text.toString()))
            }
            .setNegativeButton(
                "Cancel"
            ) { _: DialogInterface?, _: Int ->
            }
        val dialog = builder.create()
        dialog.show()
        dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        val okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        okButton.isEnabled = true
        manualG.editText.addTextChangedListener(
            getManualAddTextWatcher(
                manualG.editText,
                okButton
            )
        )

    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_add_manually -> {
                addManually()
                true
            }
            R.id.action_send -> {
                emailData()
                true
            }
            R.id.action_clear -> {
                askToClearIds()
                true
            }
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Initiate a scan
     */
    private fun initiateScan() {
        // set up regex
        val regexEnable = sharedPreferences.getBoolean(getString(R.string.sp_regex_enable), true)
        val regexString = sharedPreferences.getString(getString(R.string.sp_regex_string), getString(R.string.default_regex_string))
        regexOptions =
            if (regexEnable && regexString != null) EnabledRegex(regexString) else DisabledRegex()

        val integrator = IntentIntegrator(this)
        integrator.setPrompt("Press back to finish");
        integrator.setOrientationLocked(true);
        integrator.setBeepEnabled(false);
        integrator.captureActivity = (CaptureActivityPortrait::class.java)
        integrator.initiateScan()
    }

    /**
     * Email the data in the adapter
     */
    private fun emailData() {
        val address: String?
        val subject: String?
        if (this::sharedPreferences.isInitialized) {
            address = sharedPreferences.getString(getString(R.string.sp_email_address), null)
            subject = sharedPreferences.getString(
                getString(R.string.sp_email_subject),
                getString(R.string.default_email_subject)
            )
        } else {
            address = null
            subject = null
        }
        sendEmail(this, adapter.itemList, address, subject);
    }

    /**
     * Add an item to the adapter and persistent data storage
     * @param item the Id object to add
     */
    private fun addItem(item: Id) {
        adapter.addItem(item)
        saveData()
    }

    /**
     * Remove an item from the adapter and persistent data storage
     * @param item The Id object to remove
     */
    internal fun removeItem(item: Id) {
        adapter.removeItem(item)
        saveData()
    }

    /**
     * Save the current adapter information to the persistent data storage
     */
    internal fun saveData() {
        saveData(adapter.itemList, sharedPreferences, getString(R.string.sp_items))
    }

    /**
     * Show match failure dialog
     * @param output the string that failed to match
     * @param regex the regex
     */
    private fun showMatchFailureDialog(output: String, regex: Regex) {
        val builder =
            MaterialAlertDialogBuilder(this, R.style.MaterialAlertDialog_Rounded)
        val ssb = SpannableStringBuilder()
            .append("Failed to match ")
            .bold { append(output) }
            .append(" to Regex string ")
            .bold { append(regex.toString()) }
            .append(".\n\nThis behaviour can be changed in settings.\n\nDo you wish to add it anyway?")

        builder.setTitle(getString(R.string.regex_match_failure_title))
        builder.setMessage(
            ssb
        )
            .setPositiveButton(
                "Add"
            ) { _: DialogInterface?, _: Int ->
                addItem(Id(output))
                initiateScan()
            }
            .setNegativeButton(
                "Discard"
            ) { _: DialogInterface?, _: Int ->
                initiateScan()
            }
        val dialog = builder.create()
        dialog.show()
    }

    /**
     * Get the results
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents == null) {
                // User cancelled
            } else {
                val output = result.contents
                if (regexOptions.passes(output)) {
                    addItem(Id(output))
                    initiateScan()
                } else {
                    showMatchFailureDialog(output, (regexOptions as EnabledRegex).regex)
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

}
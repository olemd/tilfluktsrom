package no.naiv.tilfluktsrom.ui

import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import no.naiv.tilfluktsrom.R

/**
 * Full-screen dialog showing app info, privacy statement, and copyright.
 * Static content — all text comes from string resources for offline use and i18n.
 */
class AboutDialog : DialogFragment() {

    companion object {
        const val TAG = "AboutDialog"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.Theme_Tilfluktsrom_Dialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.dialog_about, container, false)
        view.findViewById<TextView>(R.id.about_data_links).apply {
            text = Html.fromHtml(getString(R.string.about_data_links), Html.FROM_HTML_MODE_COMPACT)
            movementMethod = LinkMovementMethod.getInstance()
        }
        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
    }
}

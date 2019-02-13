package org.neriko.promoter

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Context
import android.os.AsyncTask
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_settings.*
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.ArrayList


class SettingsActivity : AppCompatActivity(), TextView.OnEditorActionListener {

    private var shownHelp = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val shortcodes = ArrayList<String>()
        val recyclerAdapter = CustomAdapter(shortcodes)
        val recyclerView = findViewById<RecyclerView>(R.id.profile_pictures).apply {
            isNestedScrollingEnabled = false
            layoutManager = GridLayoutManager(this@SettingsActivity, 3)
            adapter = recyclerAdapter
        }

        floatingActionButton.setOnClickListener {
            DownloadProfileShortcodes(shortcodes, recyclerAdapter).execute(instagram_name.text.toString())
            hideKeyboard()
            floatingActionButton.hide()
            if (!shownHelp) {
                Snackbar.make(settings_layout, getString(R.string.click_on_photo), Snackbar.LENGTH_INDEFINITE).setAction(R.string.close) { }.show()
                shownHelp = true
            }
        }

        instagram_name.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrEmpty()) {
                    floatingActionButton.hide()
                } else {
                    floatingActionButton.show()
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        instagram_name.setOnEditorActionListener(this)
    }

    private fun hideKeyboard() {
        val view = this.currentFocus
        if (view != null) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
        System.out.println("Clicked! " + (actionId == EditorInfo.IME_ACTION_SEARCH))
        if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            floatingActionButton.performClick()
            return true
        }

        return false
    }

    private inner class CustomAdapter(val shortcodes: ArrayList<String>): RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomAdapter.ViewHolder {
            val itemLayoutView = LayoutInflater.from(parent.context).inflate(R.layout.recycler_item, null)
            return ViewHolder(itemLayoutView)
        }

        override fun getItemCount() = shortcodes.size

        override fun onBindViewHolder(holder: CustomAdapter.ViewHolder, position: Int) {
            val link = if (shortcodes[position].contains("http")) shortcodes[position]
            else "https://instagram.com/p/" + shortcodes[position] + "/media/?size=m"
            Picasso.get().load(link).into(holder.imageView)
            holder.imageView.contentDescription = shortcodes[position]
            holder.imageView.setOnClickListener { view ->
                val user = HashMap<String, Any>()
                user["picture_shortcode"] = view.contentDescription
                user["profile_name"] = instagram_name.text.toString()
                FirebaseFirestore.getInstance().collection("users")
                        .document(FirebaseAuth.getInstance().currentUser!!.uid)
                        .set(user)
                        .addOnSuccessListener {
                            finish()
                        }
            }
        }

        private inner class ViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
            val imageView = itemView.findViewById<ImageView>(R.id.imageView)!!
        }
    }

    private class DownloadProfileShortcodes(val shortcodes: ArrayList<String>, val adapter: CustomAdapter): AsyncTask<String, Void, Document>() {
        override fun doInBackground(vararg params: String?): Document? {
            val profileName = params[0]
            var doc: Document? = null
            try {
                doc = Jsoup.connect("https://www.instagram.com/$profileName/").get()
            } catch (e: IOException) {
                e.printStackTrace()
            }

            return doc
        }

        override fun onPostExecute(result: Document?) {
            if (result != null) {
                val shortcodesSize = shortcodes.size
                shortcodes.clear()
                adapter.notifyItemRangeRemoved(0, shortcodesSize)

                val data = result.body().data()
                val firstString = data.split("\n")[0].substring("window._sharedData = ".length)
                val jsonObject = JSONObject(firstString)
                        .getJSONObject("entry_data")
                        .getJSONArray("ProfilePage")
                        .getJSONObject(0)
                        .getJSONObject("graphql")
                        .getJSONObject("user")

                shortcodes.add(jsonObject.getString("profile_pic_url_hd"))
                adapter.notifyItemInserted(0)

                val jsonArray = jsonObject
                        .getJSONObject("edge_owner_to_timeline_media")
                        .getJSONArray("edges")

                for (i in 0 until jsonArray.length()) {
                    shortcodes.add(jsonArray.getJSONObject(i).getJSONObject("node").getString("shortcode"))
                    adapter.notifyItemInserted(shortcodes.size - 1)
                }
            }
        }
    }
}

